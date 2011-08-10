(ns ring.adapter.plumbing
  (:require [clojure.string :as str])
  (:import [java.io InputStream File RandomAccessFile FileInputStream]
           java.net.URLConnection
           [org.jboss.netty.channel Channel ChannelFutureListener
            ChannelHandlerContext]
           org.jboss.netty.handler.codec.http.HttpRequest
           org.jboss.netty.util.CharsetUtil
           org.jboss.netty.channel.ChannelFuture
           java.net.InetSocketAddress
           [org.jboss.netty.buffer ChannelBufferInputStream ChannelBuffers]
           [org.jboss.netty.handler.stream ChunkedStream ChunkedFile]
           [org.jboss.netty.handler.codec.http HttpHeaders HttpVersion
            HttpMethod HttpResponseStatus DefaultHttpResponse]))

(defn- remote-address [^ChannelHandlerContext ctx]
  (let [^InetSocketAddress a (-> ctx .getChannel .getRemoteAddress)]
    (-> a .getAddress .getHostAddress)))

(defn- get-headers
  [^HttpRequest req]
  (reduce (fn [headers ^String name]
            (assoc headers (.toLowerCase name) (.getHeader req name)))
          {}
          (.getHeaderNames req)))

(defn- content-type [headers]
  (if-let [ct ^String (headers "content-type")]
    (-> ct (.split ";") first str/trim str/lower-case)
    "text/html"))

(defn- uri-query [^HttpRequest req]
  (let [uri ^String (.getUri req)
        idx (.indexOf uri "?")]
    (if (= idx -1) [uri nil]
        [(subs uri 0 idx) (subs uri (inc idx))])))

(defn build-request-map
  "Converts a netty request into a ring request map, TODO http/1.0 use
   keep-alive too"
  [^ChannelHandlerContext ctx ^HttpRequest req]
  (let [headers (get-headers req)
        [domain port] (.split ^String (headers "host") ":")
        [uri query-string] (uri-query req)]
    {:server-port        port
     :server-name        domain
     :remote-addr        (remote-address ctx)
     :uri                uri
     :query-string       query-string
     :scheme             (keyword (headers "x-scheme" "http"))
     :request-method     (-> req .getMethod .getName .toLowerCase keyword)
     :headers            headers
     :content-type       (content-type headers)
     :content-length     (headers "content-length")
     :character-encoding (headers "content-encoding")
     :body               (ChannelBufferInputStream. (.getContent req))
     :keep-alive         (= (headers "connection") "keep-alive")}))

(defn- set-headers [^DefaultHttpResponse response headers]
  (doseq [[^String key val-or-vals]  headers]
    (if (string? val-or-vals)
      (.setHeader response key ^String val-or-vals)
      (doseq [val val-or-vals]
        (.addHeader response key val)))))

(defn- write-content
  [^Channel ch ^DefaultHttpResponse response ^CharSequence content keep-alive]
  (.setContent response (ChannelBuffers/copiedBuffer
                         content CharsetUtil/UTF_8))
  (if keep-alive
    (do (HttpHeaders/setContentLength ; only for a keep-alive connection.
         response (-> response .getContent .readableBytes))
        (.write ch response))
    (-> ch (.write response) (.addListener ChannelFutureListener/CLOSE))))

(defn- write-file
  [^Channel ch ^DefaultHttpResponse response ^File file keep-alive]
  (let [raf (RandomAccessFile. file "r")
        len (.length raf)
        region (ChunkedFile. raf 0 len 8192)]
    (.setHeader response "Content-Type"
                (URLConnection/guessContentTypeFromName (.getName file)))
    (HttpHeaders/setContentLength response len)
    (.write ch response)                ;write initial line and header
    (let [write-future (.write ch region)]
      (if-not keep-alive
        (.addListener write-future ChannelFutureListener/CLOSE)))))

(defn write-response
  [^ChannelHandlerContext ctx keep-alive {:keys [status headers body]}]
  (let [ch (.getChannel ctx)
        resp (DefaultHttpResponse. HttpVersion/HTTP_1_1
               (HttpResponseStatus/valueOf status))]
    (set-headers resp headers)
    (cond (string? body) (write-content ch resp body keep-alive)
          (seq? body) (write-content ch resp (apply str body) keep-alive)
          (instance? InputStream body)
          (do
            (.write ch resp)
            (-> (.write ch (ChunkedStream. ^InputStream body))
                (.addListener (reify ChannelFutureListener
                                (operationComplete [this fut]
                                  (-> fut .getChannel .close)
                                  (.close ^InputStream body))))))
          (instance? File body) (write-file ch resp body keep-alive)
          (nil? body)  nil
          :else (throw (Exception. (format "Unrecognized body: %s") body)))))


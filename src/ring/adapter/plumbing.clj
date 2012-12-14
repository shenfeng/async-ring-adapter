(ns ring.adapter.plumbing
  (:import [org.jboss.netty.channel ChannelHandlerContext]
           ring.adapter.netty.Util
           org.jboss.netty.handler.codec.http.HttpRequest
           java.net.InetSocketAddress
           ring.adapter.netty.IListenableFuture
           [org.jboss.netty.buffer ChannelBufferInputStream]
           [org.jboss.netty.handler.codec.http HttpHeaders HttpVersion
            HttpResponseStatus DefaultHttpResponse]))

(defn- remote-address [^ChannelHandlerContext ctx]
  (let [^InetSocketAddress a (-> ctx .getChannel .getRemoteAddress)]
    (-> a .getAddress .getHostAddress)))

(defn- get-headers  [^HttpRequest req]
  (reduce (fn [headers ^String name]
            (assoc headers (.toLowerCase name) (.getHeader req name)))
          {}
          (.getHeaderNames req)))

(defn- uri-query [^HttpRequest req]
  (let [uri ^String (.getUri req)
        idx (.indexOf uri "?")]
    (if (= idx -1) [uri nil]
        [(subs uri 0 idx) (subs uri (inc idx))])))

(defn- domain-port [^HttpRequest req]
  (let [host (HttpHeaders/getHost req)
        idx (.indexOf host ":")]
    (if (= idx -1) (list host 80)
        (list (subs host 0 idx) (Integer/parseInt
                                 (subs host (inc idx)))))))

(defn build-request-map
  "Converts a netty request into a ring request map, TODO http/1.0 use
   keep-alive too"
  [^ChannelHandlerContext ctx ^HttpRequest req]
  (let [headers (get-headers req)
        [domain port] (domain-port req)
        [uri query-string] (uri-query req)]
    {:server-port        port
     :server-name        domain
     :remote-addr        (remote-address ctx)
     :uri                uri
     :query-string       query-string
     :scheme             (keyword (headers "x-scheme" "http"))
     :request-method     (-> req .getMethod .getName .toLowerCase keyword)
     :headers            headers
     :content-type       (headers "content-type")
     :content-length     (HttpHeaders/getContentLength req)
     :character-encoding (headers "content-encoding")
     :body               (ChannelBufferInputStream. (.getContent req))
     :keep-alive         (HttpHeaders/isKeepAlive req)}))

(defn- set-headers [^DefaultHttpResponse resp headers]
  (doseq [[^String key val-or-vals]  headers]
    (if (string? val-or-vals)
      (.setHeader resp key ^String val-or-vals)
      (doseq [val val-or-vals]
        (.addHeader resp key val)))))

(defn write-response
  [^ChannelHandlerContext ctx keep-alive
   {:keys [status body] :as ring-resp}]
  (if (instance? IListenableFuture body)
    (.addListener ^IListenableFuture body
                  (fn [] (write-response ctx keep-alive
                                        (.get ^IListenableFuture body))))
    (let [resp (DefaultHttpResponse. HttpVersion/HTTP_1_1
                 (HttpResponseStatus/valueOf status))]
      (set-headers resp (:headers ring-resp))
      (Util/writeResp ctx resp body keep-alive))))


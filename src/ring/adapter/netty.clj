(ns ring.adapter.netty
  (:use ring.adapter.plumbing)
  (:require [clojure.tools.macro :as macro])
  (:import ring.adapter.netty.IListenableFuture
           java.net.InetSocketAddress
           java.util.concurrent.Executors
           org.jboss.netty.channel.group.DefaultChannelGroup
           ring.adapter.netty.PrefixTF
           org.jboss.netty.bootstrap.ServerBootstrap
           [org.jboss.netty.util ThreadRenamingRunnable ThreadNameDeterminer]
           org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory
           org.jboss.netty.handler.stream.ChunkedWriteHandler
           [org.jboss.netty.channel ChannelPipeline Channels MessageEvent
            ExceptionEvent ChannelEvent
            ChannelPipelineFactory SimpleChannelUpstreamHandler]
           [org.jboss.netty.handler.codec.http HttpRequestDecoder
            HttpResponseEncoder]))

(def default-server-options
  {"child.reuseAddress" true,
   "reuseAddress" true,
   "child.keepAlive" true,
   "child.connectTimeoutMillis" 4000,
   "tcpNoDelay" true,
   "child.tcpNoDelay" true})

(defn- make-handler
  [^DefaultChannelGroup channel-group handler]
  (proxy [SimpleChannelUpstreamHandler] []
    (channelOpen [ctx ^ChannelEvent e]
      (.add channel-group (.getChannel e)))
    (messageReceived [ctx ^MessageEvent e]
      (let [request-map (build-request-map ctx (.getMessage e))
            ring-response (handler request-map)]
        (when ring-response
          (write-response ctx
                          (request-map :keep-alive) ring-response))))
    (exceptionCaught [ctx ^ExceptionEvent e]
      ;; close it
      (-> e .getChannel .close))))

(defn- pipeline-factory
  [^DefaultChannelGroup channel-group handler options]
  (reify ChannelPipelineFactory
    (getPipeline [this]
      (doto (Channels/pipeline)
        (.addLast "decoder" (HttpRequestDecoder.))
        (.addLast "encoder" (HttpResponseEncoder.))
        (.addLast "chunkedWriter" (ChunkedWriteHandler.))
        (.addLast "handler" (make-handler channel-group handler))))))

(defmacro async-response
  "Wraps body so that a standard Ring response will be returned to caller when
  `(callback-name ring-response)` is executed in any thread:

     (defn my-async-handler! [request]
       (async-response respond!
         (future (respond! {:status  200
                            :headers {\"Content-Type\" \"text/html\"}
                            :body    \"This is an async response!\"}))))

  The caller's request will block while waiting for a response (see
  Ajax long polling example as one common use case)."
  [callback-name & body]
  `(let [data# (atom {})
         ~callback-name (fn [response#]
                          (swap! data# assoc :response response#)
                          (when-let [listener# (:listener @data#)]
                            (.run ^Runnable listener#)))]
     (do ~@body)
     {:status  200
      :headers {}
      :body    (reify IListenableFuture
                 (addListener [this# listener#]
                   (if (:response @data#)
                     (.run ^Runnable listener#)
                     (swap! data# assoc :listener listener#)))
                 (get [this#] (:response @data#)))}))

(defmacro defasync
  "(defn name [request] (async-response callback-name body))"
  {:arglists '(name [request] callback-name & body)}
  [name & sigs]
  (let [[name [[request] callback-name & body]]
        (macro/name-with-attributes name sigs)]
    `(defn ~name [~request] (async-response ~callback-name ~@body))))

(defn run-netty [handler options]
  (ThreadRenamingRunnable/setThreadNameDeterminer ThreadNameDeterminer/CURRENT)
  (let [cf (NioServerSocketChannelFactory.
            (Executors/newCachedThreadPool (PrefixTF. "Server Boss"))
            (Executors/newCachedThreadPool (PrefixTF. "Server Worker"))
            (or (:worker options) 1))
        server (ServerBootstrap. cf)
        channel-group (DefaultChannelGroup.)]
    (doseq [[k v] (merge default-server-options (:netty options))]
      (.setOption server k v))
    (.setPipelineFactory server (pipeline-factory channel-group
                                                  handler options))
    (.add channel-group (.bind server (InetSocketAddress. (:port options))))
    (fn stop-server []
      (-> channel-group .close .awaitUninterruptibly)
      (.releaseExternalResources server))))

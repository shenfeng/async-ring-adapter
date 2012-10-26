(ns ring.adapter.netty
  (:use ring.adapter.plumbing)
  (:import ring.adapter.netty.ListenableFuture
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

(defmacro defasync [name params & body]
  (let [req (params 0)]
    `(defn ~name [~req]
       {:status 200
        :headers {}
        :body (let [data# (atom {})
                    ~req (assoc ~req :cb
                                (fn [resp#]
                                  (reset! data# (assoc @data# :r resp#))
                                  (when-let [l# ^Runnable (:l @data#)]
                                    (.run l#))))]
                (do ~@body)
                (reify ListenableFuture
                  (addListener [this# listener#]
                    (if-let [d# (:r @data#)]
                      (.run ^Runnable listener#)
                      (reset! data# (assoc @data# :l listener#))))
                  (get [this#]
                    (:r @data#))))})))

(defn run-netty [handler options]
  (ThreadRenamingRunnable/setThreadNameDeterminer
   ThreadNameDeterminer/CURRENT)
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
    (.add channel-group (.bind server
                               (InetSocketAddress. (:port options))))
    (fn []
      (-> channel-group .close .awaitUninterruptibly)
      (.releaseExternalResources server))))

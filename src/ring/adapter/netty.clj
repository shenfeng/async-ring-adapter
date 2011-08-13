(ns ring.adapter.netty
  (:use ring.adapter.plumbing)
  (:import [java.net InetSocketAddress]
           [java.util.concurrent Executors]
           org.jboss.netty.channel.group.DefaultChannelGroup
           org.jboss.netty.bootstrap.ServerBootstrap
           org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory
           org.jboss.netty.handler.stream.ChunkedWriteHandler
           [org.jboss.netty.channel ChannelPipeline Channels
            ExceptionEvent ChannelEvent MessageEvent
            ChannelPipelineFactory SimpleChannelUpstreamHandler]
           [org.jboss.netty.handler.codec.http HttpContentCompressor
            HttpRequestDecoder HttpResponseEncoder HttpChunkAggregator]))

(def default-server-options
  {"child.reuseAddress" true,
   "reuseAddress" true,
   "child.keepAlive" true,
   "child.connectTimeoutMillis" 100,
   "tcpNoDelay" true,
   "readWriteFair" true,
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
      (-> e .getCause .printStackTrace)
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

(defn run-netty [handler options]
  (let [server (ServerBootstrap. (NioServerSocketChannelFactory.
                                  (Executors/newCachedThreadPool)
                                  (Executors/newCachedThreadPool)))
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

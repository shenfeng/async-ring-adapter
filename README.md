# Ring netty adapter

An netty adapter impl on top of [netty](http://netty.io/)
for used with [Ring](https://github.com/mmcgrana/ring)

I write another one using pure java [http-kit](https://github.com/shenfeng/http-kit)

## Quick Start

  `[me.shenfeng/ring-netty-adapter "1.0.2"]`

```clj
(use 'ring.adapter.netty)

(defn app
 [req]
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    (str "hello word")})

(run-netty app {:port 8080
                :netty {"reuseAddress" true}})
```
### netty options
* connectTimeoutMillis
* keepAlive
* reuseAddress
* tcpNoDelay
* receiveBufferSize
* sendBufferSize
* trafficClass
* writeBufferHighWaterMark
* writeBufferLowWaterMark
* writeSpinCount

see
[netty doc](http://docs.jboss.org/netty/3.2/api/org/jboss/netty/channel/socket/nio/NioSocketChannelConfig.html)

## why

*  Netty is well designed and documented. It's fun reading it's
   code. It's high performance.

*  Netty's HTTP support is very different from the existing HTTP
   libraries. It gives you complete control over how HTTP messages are
   exchanged in a low level. Because it is basically the combination
   of HTTP codec and HTTP message classes, there is no restriction
   such as enforced thread model. That is, you can write your own HTTP
   client or server that works exactly the way you want. You have full
   control over thread model, connection life cycle, chunked encoding,
   and as much as what HTTP specification allows you to do.

## Limitation

* Serving file is not optimized due to it's better be done by Nginx,
  so as compression.

## Async

 By using
 [ListenableFuture](https://github.com/shenfeng/async-ring-adapter/tree/master/src/java/ring/adapter/netty/ListenableFuture.java).

 More info [here](https://github.com/shenfeng/async-ring-adapter/blob/master/src/ring/adapter/plumbing.clj#L66)

 This is used by [rssminer](http://rssminer.net) to do

 1. Favicon service on demand fetch and delivery
 2. Feed's original page on demand fetch and delivery
 3. Proxy, workaround some good blogging site are blocked

```clj
(def asyc-body
  (reify ListenableFuture
    (addListener [this listener]
      (.start (Thread. (fn []
                         (println "sleep 100ms")
                         (Thread/sleep 100)
                         (.run listener)))))
    (get [this]
      {:status 204
       :headers {"Content-type" "application/json"}})))

(deftest test-body-listenablefuture
  (let [server (run-netty (fn [req]
                            {:status  200
                             :body asyc-body})
                          {:port 4347})]
    (try
      (let [resp (http/get "http://localhost:4347")]
        (is (= (:status resp) 204))
        (is (= (get-in resp [:headers "content-type"]) "application/json")))
      (finally (server)))))
```

## Benchmark

There is a script `./scripts/start_server` will start netty at port
3333, jetty at port 4444, here is a result on my machine

#### how to run
```sh
# start jetty, use ab to benchmark it; do it with netty the same
lein deps && lein javac && ./scripts/bench

```

```sh
  ab -n 300000 -c 50 http://localhost:4444/  #11264.90 [#/sec] (mean)
  ab -n 300000 -c 50 http://localhost:3333/  #12638.37 [#/sec] (mean)
```

## Contributors

This repo was fork from [datskos](https://github.com/datskos/ring-netty-adapter)

# Ring netty adapter

An netty adapter impl on top of [netty](http://netty.io/)
for used with [Ring](https://github.com/mmcgrana/ring)

I write another one using pure java [http-kit](https://github.com/shenfeng/http-kit)

## Quick Start

  `me.shenfeng/async-ring-adapter "1.1-SNAPSHOT"`

```clj
(use 'ring.adapter.netty)

(defn app
 [req]
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    "hello word"})

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

### Async extension


You write a ring handler this way

```clj
(defn handler [req]
  {:status 200 :headers {} :body "hello world"})  # accept a Clojure map, return a map

```

defasync just like defn. The difference is: compute the respone map (possiblity asynchronous, in an other thread), give it to `cb`, cb can be passed around.

**interface design suggestion welcome**

Currently, [http-kit](https://github.com/shenfeng/http-kit) use the same defasync mechanism, So you should have No trouble switch between them two.


#### example
```clj
(:use ring.adapter.netty)

(defasync async [req] cb
  (.start (Thread. (fn []
                     (Thread/sleep 1000)
                     ;; return a ring spec response => {:status :headers :body}
                     ;; or just :body
                     ;; call (cb req) when response ready
                     (cb {:status 200 :body "hello async"})))))

;; or this way, just wrap with async-response
(defn async [req]
  (async-response respond!
                  (future (respond! {:status 200 :body "hello async"}))))

(run-netty async {:port 8080})
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

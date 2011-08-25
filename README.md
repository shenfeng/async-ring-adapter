# Ring netty adapter

An netty adapter impl on top of [netty](http://www.jboss.org/netty)
for used with [Ring](https://github.com/mmcgrana/ring)

## Quick Start

  `[me.shenfeng/ring-netty-adapter "0.0.1-SNAPSHOT"]`

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

* Currently, the worker thread is fixed: `cpu` * 2, may not very
  suited for long running handler due to Blocking jdbc call, etc.

* Serving file is not optimized due to it's better be done by Nginx,
  so as compression.

## Benchmark

There is a script `./scripts/start_server` will start netty at port
3333, jetty at port 4444, here is a result on my machine
#### how to run
* lein deps && lein javac && ./scripts/start_server

```sh
  ab -n 300000 -c 50 http://localhost:4444/  #11264.90 [#/sec] (mean)
  ab -n 300000 -c 50 http://localhost:3333/  #12638.37 [#/sec] (mean)
```

## Contributors

This repo was fork from [datskos](https://github.com/datskos/ring-netty-adapter)

## Long poling(alpha):

By using http chunked encoding:
sample code:

```clj
(deftest test-body-chunked
  (let [async (fn [^Runnable f] (.start (Thread. f)))
        server (run-netty (fn [req]
                            (let [chunked (HttpChunked. "Hello ")]
                              (async (fn [] (Thread/sleep 100)
                                       (.send chunked "World")
                                       (async (fn [] (Thread/sleep 100)
                                                (.close chunked)))))
                              {:status  200
                               :headers {"Content-Type" "text/plain"}
                               :body chunked}))
                          {:port 4347})]
    (try
      (let [resp (http/get "http://localhost:4347")]
        (is (= (:status resp) 200))
        (is (= (get-in resp [:headers "content-type"]) "text/plain"))
        (is (= (get-in resp [:headers "transfer-encoding"]) "chunked"))
        (is (= (:body resp) "Hello World")))
      (finally (server)))))
```

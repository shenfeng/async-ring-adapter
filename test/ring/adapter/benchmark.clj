(ns ring.adapter.benchmark
  (:use ring.adapter.jetty
        [clojure.tools.cli :only [cli optional required]]
        ring.adapter.netty))

(def resp {:status  200
           :headers {"Content-Type" "text/plain"}
           :body    "Hello World"})

(defn start-server [{:keys [server]}]
  (case server
    :netty
    (run-netty (fn [req] resp) {:port 9091})
    :jetty
    (run-jetty (fn [req] resp) {:port 9091 :join? false})
    :all
    (do
      (println "netty on port 3333 \njetty on port 4444")
      (run-netty (fn [req] resp) {:port 3333})
      (run-jetty (fn [req] resp) {:port 4444 :join? false}))))

(defn main [& args]
  (start-server
   (cli args
        (optional ["-s" "--server" "jetty or netty or all" :default "all"]
                  keyword))))

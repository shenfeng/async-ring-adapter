(ns ring.adapter.netty-test
  (:use clojure.test
        ring.middleware.file-info
        ring.adapter.netty)
  (:require [clj-http.client :as http])
  (:import [java.io File FileOutputStream FileInputStream]))

(defn gen-tempfile
  "generate a tempfile, the file will be deleted before jvm shutdown"
  ([size extension]
     (let [tmp (doto
                   (File/createTempFile "tmp_" extension)
                 (.deleteOnExit))]
       (with-open [w (FileOutputStream. tmp)]
         (doseq [i (range size)]
           (.write w (rem i 255))))
       tmp)))

(deftest netty-body-string
  (let [server (run-netty (fn [req]
                            {:status  200
                             :headers {"Content-Type" "text/plain"}
                             :body    "Hello World"})
                          {:port 4347})]
    (try
      (Thread/sleep 300)
      (let [resp (http/get "http://localhost:4347")]
        (is (= (:status resp) 200))
        (is (.startsWith (get-in resp [:headers "content-type"])
                         "text/plain"))
        (is (= (:body resp) "Hello World")))
      (server))))


(deftest test-body-file
  (let [server (run-netty
                (wrap-file-info (fn [req]
                                  {:status 200
                                   :body (gen-tempfile 67 ".txt")}))
                {:port 4347})]
    (try
      (Thread/sleep 300)
      (let [resp (http/get "http://localhost:4347")]
        (is (= (:status resp) 200))
        (is (.startsWith (get-in resp [:headers "content-type"])
                         "text/plain"))
        (is (:body resp)))
      (server))))

(deftest test-body-inputstream
  (let [server (run-netty
                (fn [req]
                  {:status 200
                   :body (FileInputStream. (gen-tempfile 67 ".txt"))})
                {:port 4347})]
    (try
      (Thread/sleep 300)
      (let [resp (http/get "http://localhost:4347")]
        (is (= (:status resp) 200))
        (is (:body resp)))
      (server))))

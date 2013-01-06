(ns ring.adapter.netty-test
  (:use clojure.test
        ring.middleware.file-info
        [clojure.java.io :only [input-stream]]
        (compojure [core :only [defroutes GET POST HEAD DELETE ANY context]]
                   [handler :only [site]]
                   [route :only [not-found]])
        ring.middleware.file-info
        ring.adapter.netty)
  (:require [clj-http.client :as http])
  (:import [java.io File FileOutputStream FileInputStream]
           ring.adapter.netty.IListenableFuture))

(defn ^File gen-tempfile
  "generate a tempfile, the file will be deleted before jvm shutdown"
  ([size extension]
     (let [tmp (doto
                   (File/createTempFile "tmp_" extension)
                 (.deleteOnExit))]
       (with-open [w (FileOutputStream. tmp)]
         (.write w ^bytes (byte-array size
                                      (map (fn [i]
                                             (byte (+ (int \a) (rem i 26))))
                                           (range size)))))
       tmp)))

(defn test-get-spec [req]
  ;; (is (= 4347 (:server-port req)))
  ;; (is (= "127.0.0.1" (:remote-addr req)))
  ;; (is (= "localhost" (:server-name req)))
  ;; ;; (is (= "127.0.0.1" (:remote-addr req)))
  ;; (is (= "/spec-get" (:uri req)))
  ;; (is (= "a=b" (:query-string req)))
  ;; (is (= :http (:scheme req)))
  ;; (is (= :get (:request-method  req)))
  ;; (is (= "utf8" (:character-encoding req)))
  ;; (is (= nil (:content-type req)))
  {:status  200
   :headers {"Content-Type" "text/plain"}
   :body    "Hello World"})

(defn test-post-spec [req]
  ;; TODO fix this, http header name are case-insensitive
  (is (= 4347 (:server-port req)))
  (is (= "127.0.0.1" (:remote-addr req)))
  (is (= "localhost" (:server-name req)))
  (is (= "127.0.0.1" (:remote-addr req)))
  (is (= "/spec-post" (:uri req)))
  (is (= "a=b" (:query-string req)))
  ;; ;; (is (= "c" (-> req :params :p)))
  (is (= :http (:scheme req)))
  (is (= :post (:request-method  req)))
  ;; (is (= "application/x-www-form-urlencoded" (:content-type req)))
  ;; (is (= "UTF-8" (:character-encoding req)))
  {:status  200
   :headers {"Content-Type" "text/plain"}
   :body    "Hello World"})

(defasync async-handler [req] cb
  (cb {:status 200 :body "hello async"}))

(defasync async-just-body [req] cb
  (cb "just-body"))

(defn to-int [int-str] (Integer/valueOf int-str))

(defroutes test-routes
  (GET "/" [] "hello world")
  (GET "/spec-get" [] test-get-spec)
  (POST "/spec-post" [] test-post-spec)
  (GET "/string" [] (fn [req] {:status 200
                              :headers {"Content-Type" "text/plain"}
                              :body "Hello World"}))
  (GET "/iseq" [] (fn [req] {:status 200
                            :headers {"Content-Type" "text/plain"}
                            :body (range 1 10)}))
  (GET "/file" [] (wrap-file-info (fn [req]
                                    {:status 200
                                     :body (let [l (to-int (or (-> req :params :l) "5024000"))]
                                             (gen-tempfile l ".txt"))})))
  (GET "/inputstream" [] (fn [req] (let [l (-> req :params :l to-int)
                                        file (gen-tempfile l ".txt")]
                                    {:status 200
                                     :body (FileInputStream. file)})))
  (POST "/multipart" [] (fn [req] (let [{:keys [title file]} (:params req)]
                                   {:status 200
                                    :body (str title ": " (:size file))})))
  (POST "/chunked" [] (fn [req] {:status 200
                                :body (str (:content-length req))}))
  (GET "/null" [] (fn [req] {:status 200 :body nil}))
  (GET "/async" [] async-handler)
  (GET "/async-response" [] (fn [req]
                              (async-response respond!
                                              (future (respond! {:status 200 :body "hello async"})))))
  (GET "/async-just-body" [] async-just-body))

(use-fixtures :once (fn [f]
                      (let [server (run-netty (site test-routes) {:port 4347})]
                        (try (f) (finally (server))))))

(deftest test-netty-ring-spec
  (http/get "http://localhost:4347/spec-get"
            {:query-params {"a" "b"}})
  (http/post "http://localhost:4347/spec-post?a=b"
             {:headers {"Content-Type" "application/x-www-form-urlencoded"}
              :body "p=c&d=e"}))

(deftest test-body-string
  (let [resp (http/get "http://localhost:4347/string")]
    (is (= (:status resp) 200))
    (is (= (get-in resp [:headers "content-type"]) "text/plain"))
    (is (= (:body resp) "Hello World"))))

(deftest test-body-file
  (doseq [length (range 1 (* 1024 1024 5) 739987)]
    (let [resp (http/get "http://localhost:4347/file?l=" length)]
      (is (= (:status resp) 200))
      (is (= (get-in resp [:headers "content-type"]) "text/plain"))
      (is (= length (count (:body resp)))))))

(deftest test-body-file
  (let [resp (http/get "http://localhost:4347/file")]
    (is (= (:status resp) 200))
    (is (= (get-in resp [:headers "content-type"]) "text/plain"))
    (is (:body resp))))

(deftest test-body-inputstream
  (doseq [length (range 1 (* 1024 1024 5) 739987)] ; max 5m, many files
    (let [uri (str "http://localhost:4347/inputstream?l=" length)
          resp (http/get uri)]
      (is (= (:status resp) 200))
      (is (= length (count (:body resp)))))))

(deftest test-body-iseq
  (let [resp (http/get "http://localhost:4347/iseq")]
    (is (= (:status resp) 200))
    (is (= (get-in resp [:headers "content-type"]) "text/plain"))
    (is (= (:body resp) (apply str (range 1 10))))))

(deftest test-body-null
  (let [resp (http/get "http://localhost:4347/null")]
    (is (= (:status resp) 200))
    (is (= "" (:body resp)))))

(deftest test-async
  (let [resp (http/get "http://localhost:4347/async")]
    (is (= (:status resp) 200))
    (is (= (:body resp) "hello async"))))

(deftest test-async
  (let [resp (http/get "http://localhost:4347/async")]
    (is (= (:status resp) 200))
    (is (= (:body resp) "hello async"))))

(deftest test-async-response
  (let [resp (http/get "http://localhost:4347/async-response")]
    (is (= (:status resp) 200))
    (is (= (:body resp) "hello async"))))

(deftest test-async-just-body
  (let [resp (http/get "http://localhost:4347/async-just-body")]
    (is (= (:status resp) 200))
    (is (:headers resp))
    (is (= (:body resp) "just-body"))))

(deftest test-multipart
  (let [title "This is a pic"
        size 102400
        resp (http/post "http://localhost:4347/multipart"
                        {:multipart [{:name "title" :content title}
                                     {:name "file" :content (gen-tempfile size ".jpg")}]})]
    (is (= (:status resp) 200))
    (is (= (str title ": " size) (:body resp)))))

(deftest test-chunked-encoding
  (let [size 4194304
        resp (http/post "http://localhost:4347/chunked"
                        ;; no :length, HttpClient will split body as 2k chunk
                        ;; server need to decode the chunked encoding
                        {:body (input-stream (gen-tempfile size ".jpg"))})]
    (is (= 200 (:status resp)))
    (is (= (str size) (:body resp)))))

;;; java -cp `lein classpath` clojure.main -m me.shenfeng.http.server.server-test
(defonce tmp-server (atom nil))
(defn -main [& args]
  (when-let [server @tmp-server]
    (server))
  (reset! tmp-server (run-netty (site test-routes) {:port 4347}))
  (println "server started at 0.0.0.0:4347"))

;; (deftest test-ws
;;   (let [resp (http/get "http://localhost:4347/ws"
;;                        {:headers {"Sec-WebSocket-Key" "x3JJHMbDL1EzLkh9GBhXDw=="
;;                                   "Upgrade" "websocket"
;;                                   "Connection" "Upgrade"}})]
;;     (is (= (:status resp) 101))
;;     (is (:headers resp))
;;     (is (= (:body resp) nil))))

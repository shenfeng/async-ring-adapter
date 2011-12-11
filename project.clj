(defproject me.shenfeng/async-ring-adapter
  "1.0.0"
  :description "Ring Netty adapter"
  :dependencies [[clojure "1.3.0"]
                 [org.jboss.netty/netty "3.2.7.Final"]]
  :dev-dependencies [[swank-clojure "1.4.0-SNAPSHOT"]
                     [clj-http "0.1.3"]
                     [org.clojure/tools.cli "0.1.0"]
                     [ring/ring-jetty-adapter "0.3.11"]
                     [ring/ring-core "0.3.11"]]
  :warn-on-reflection true
  :javac-options {:debug "true" :fork "true" :source "1.6" :target "1.6"}
  :java-source-path "src/java"
  :repositories {"JBoss" {:url "http://repository.jboss.org/nexus/content/groups/public/"
                          :snapshots false}})

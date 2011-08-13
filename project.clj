(defproject org.clojars.shenfeng/ring-netty-adapter
  "0.0.1-SNAPSHOT"
  :description "Ring Netty adapter"
  :dependencies [[clojure "1.2.1"]
                 [org.jboss.netty/netty "3.2.5.Final"]]
  :dev-dependencies [[swank-clojure "1.4.0-SNAPSHOT"]
                     [clj-http "0.1.3"]
                     [ring/ring-core "0.3.11"]]
  :warn-on-reflection true
  :repositories {"JBoss"
                 "http://repository.jboss.org/nexus/content/groups/public/"})

(defproject fileyard "0.1-SNAPSHOT"
  :description "A simple HTTP file storage"
  :license {:name "MIT License"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha17"]
                 [http-kit "2.2.0"]
                 [com.taoensso/timbre "4.10.0"]]
  :source-paths ["src"]
  :test-paths ["test"]
  :main fileyard.main)

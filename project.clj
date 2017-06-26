(defproject fileyard "0.2"
  :description "A simple HTTP file storage"
  :license {:name "MIT License"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha17"]
                 [http-kit "2.2.0"]]
  :source-paths ["src"]
  :test-paths ["test"]
  :profiles {:uberjar {:aot :all}}
  :main fileyard.main)

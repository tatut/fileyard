(ns fileyard.client
  "Client for fileyard. Provides a function to save and fetch files."
  (:require [org.httpkit.client :as http]
            [clojure.java.io :as io]))


(defprotocol Client
  (save [this input]
    "Save input (anything that io/input-stream can read).
  Returns a future of the SHA-256 hash of the saved content or an error.")

  (fetch [this sha256-hash]
    "Fetch file by SHA-256 hash. Returns an input stream from which the file
  contents can be read or an error."))

(def ok-response #{200 201})

(defrecord FileyardClient [url]
  Client

  (save [_ input]
    (future
      (let [resp @(http/request {:method :post
                                 :body (io/input-stream input)
                                 :url url})]
        (println "RESP: " (pr-str resp))
        (if (ok-response (:status resp))
          (get-in resp [:headers :x-fileyard-hash])
          {::error (str "Error saving file, response: " (:status resp)
                        " " (:body resp))}))))

  (fetch [_ sha256-hash]
    (let [resp @(http/get (str url sha256-hash) {:as :stream})]
      (if (= 200 (:status resp))
        (:body resp)
        {::error (str "Error fetching file, response: " (:status resp)
                      " " (slurp (:body resp)))}))))

(defn new-client [url]
  (->FileyardClient url))

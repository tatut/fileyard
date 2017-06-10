(ns fileyard.client
  "Client for fileyard. Provides a function to save and fetch files."
  (:require [org.httpkit.client :as http]
            [clojure.java.io :as io])
  (:import (java.util UUID)))

(defn- new-uuid []
  (UUID/randomUUID))

(defprotocol Client
  (save [this input]
    "Save input (anything that io/input-stream can read).
  Returns a future of the UUID of the saved file or an error.")

  (fetch [this uuid]
    "Fetch file by UUID. Returns an input stream from which the file
  contents can be read or an error."))

(defrecord FileyardClient [url]
  Client

  (save [_ input]
    (let [uuid (new-uuid)]
      (future
        (let [resp @(http/request {:method :put
                                   :body (io/input-stream input)
                                   :url (str url (str uuid))})]
          (if (= (:status resp) 201)
            uuid
            {::error (str "Error saving file, response: " (:status resp)
                          " " (:body resp))})))))

  (fetch [_ uuid]
    (let [resp @(http/get (str url uuid) {:as :stream})]
      (if (= 200 (:status resp))
        (:body resp)
        {::error (str "Error fetching file, response: " (:status resp)
                      " " (slurp (:body resp)))}))))

(defn new-client [url]
  (->FileyardClient url))

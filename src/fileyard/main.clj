(ns fileyard.main
  (:require [org.httpkit.server :as server]
            [clojure.java.io :as io]
            [taoensso.timbre :as log]
            [clojure.string :as str])
  (:import (java.util.zip GZIPInputStream GZIPOutputStream))
  (:gen-class))

(defn request-file [storage-path {:keys [uri] :as req}]
  (let [file (io/file storage-path (subs uri 1))]
    (if (or
         ;; Check that file does not escape storage path
         (nil? (.getParentFile file))
         (not= (.getCanonicalPath (.getParentFile file))
               (.getCanonicalPath storage-path)))
      (do
        (log/warn "Illegal access attempted: " uri)
        nil)

      ;; File is ok
      file)))

(defn- size [bytes]
  (when bytes
    (format "%.1f kb" (/ bytes 1024.0))))
(defn put-file [storage-path {headers :headers :as req}]
  (let [file (request-file storage-path req)]

    (if-not file
      {:status 400 :body (str "Invalid file")}

      (if (.exists file)
        ;; 409 Conflict
        {:status 409 :body (str "File " (.getName file) " already exists.")}

        (do
          ;; Copy the file
          (with-open [file-out (io/output-stream file)
                      zip-out (GZIPOutputStream. file-out)]
            (io/copy (:body req) zip-out))

          (log/info "Stored file " (.getName file)
                    ". Content length: " (some-> "content-length"
                                                 headers
                                                 Integer/valueOf
                                                 size)
                    ". Compressed size: " (size (.length file)) " bytes.")

          ;; Send 201 created response
          {:status 201
           :body "Created"})))))

(defn accept-gzip? [{headers :headers :as req}]
  (str/includes? (or (headers "accept-encoding") "")
                 "gzip"))

(defn get-file [storage-path req]
  (let [file (request-file storage-path req)]
    (if (or (nil? file) (not (.canRead file)))
      {:status 404 :body "No such file"}

      (if (contains? (:headers req) "if-modified-since")
        ;; Files can't be updated, so they are never modified
        {:status 304}

        ;; Send the file
        (if (accept-gzip? req)
          ;; Send the gzipped file, if accepted
          {:status 200
           :headers {"Content-Type" "application/octet-stream"
                     "Content-Encoding" "gzip"}
           :body file}

          ;; Uncompress file if client does not accept gzip
          {:status 200
           :headers {"Content-Type" "application/octet-stream"}
           :body (GZIPInputStream. (io/input-stream file))})))))


(defn handler [storage-path]
  (fn [{:keys [request-method] :as req}]
    (cond
      (= request-method :put)
      (put-file storage-path req)

      (= request-method :get)
      (get-file storage-path req)

      :default
      {:status 400 :body "Unrecognized request. Either PUT or GET a file."})))


(defn check-path [path]
  (let [f (io/file path)]
    (and (.isDirectory f)
         (.canWrite f))))

(defn check-port [port]
  (try
    (> (Integer/valueOf port) 1024)
    (catch NumberFormatException nfe
      false)))

(defn start [path port]
  (server/run-server (handler path) {:port port})
  (println "Fileyard is up"))

(defn -main [& args]
  (let [[path port] args]
    (cond
      (not (and path port))
      (println "Usage: give storage path and port")

      (not (check-path path))
      (println "Given path is not a writable directory: " path)

      (not (check-port port))
      (println "Given port is not a unprivileged port: " port)

      :default
      (start (io/file path) (Integer/valueOf port)))))

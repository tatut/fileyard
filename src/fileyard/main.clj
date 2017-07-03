(ns fileyard.main
  (:require [org.httpkit.server :as server]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [fileyard.sha256 :as sha256])
  (:import (java.util.zip GZIPInputStream GZIPOutputStream)
           (java.io File))
  (:gen-class))

(defn request-file [storage-path {:keys [uri] :as req}]
  (let [file (io/file storage-path (subs uri 1))]
    (if (or
         ;; Check that file does not escape storage path
         (nil? (.getParentFile file))
         (not= (.getCanonicalPath (.getParentFile file))
               (.getCanonicalPath storage-path)))
      (do
        (println "Illegal access attempted: " uri)
        nil)

      ;; File is ok
      file)))

(defn- size [bytes]
  (when bytes
    (format "%.1f kb" (/ bytes 1024.0))))

(defn post-file [storage-path {headers :headers :as req}]
  (let [file (File/createTempFile "fileyard" "upload" storage-path)
        sha256-hash (sha256/with-digest-input-stream
                      (:body req)
                      ;; Copy the file
                      (fn [body]
                        (with-open [file-out (io/output-stream file)
                                    zip-out (GZIPOutputStream. file-out)]
                          (io/copy body zip-out))))
        headers {"X-Fileyard-Hash" sha256-hash}
        new-file (io/file storage-path sha256-hash)]

    (if (.exists new-file)
      ;; File already exists, delete temp file and return hash
      (do
        (println "Duplicate file uploaded")
        (.delete file)
        {:status 200 :body "OK" :headers headers})

      ;; Rename to file by hash
      (do
        (println "Stored file " sha256-hash ". "
                 (when-let [len (some-> "content-length" headers Integer/valueOf size)]
                   (str "Content length: " len ". "))
                 "Compressed size: " (size (.length file)) " bytes.")
        (.renameTo file new-file)
        ;; Send 201 created response
        {:status 201 :body "Created" :headers headers}))))

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
      (= request-method :post)
      (post-file storage-path req)

      (= request-method :get)
      (get-file storage-path req)

      :default
      {:status 400 :body "Unrecognized request. Either POST or GET a file."})))


(defn check-path [path]
  (let [f (io/file path)]
    (and (.isDirectory f)
         (.canWrite f))))

(defn check-port [port]
  (try
    (> (Integer/valueOf port) 1024)
    (catch NumberFormatException nfe
      false)))

(defn start [path port max-file-size]
  (server/run-server (handler path) {:port port
                                     :max-body (or max-file-size 32000000)})
  (println "Fileyard is up"))

(defn -main [& args]
  (let [[path port max-file-size] args]
    (cond
      (not (and path port))
      (println "Usage: give storage path and port. Optional: max file size (default 32MB).")

      (not (check-path path))
      (println "Given path is not a writable directory: " path)

      (not (check-port port))
      (println "Given port is not a unprivileged port: " port)

      :default
      (start (io/file path) (Integer/valueOf port) (when max-file-size (Integer/valueOf max-file-size))))))

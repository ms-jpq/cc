(ns srv.view
  (:require
   [clojure.string :as str]
   [lib.interop :as ip]
   [srv.fs :as fs])
  (:import [java.io FileInputStream]
           [java.net URLConnection]
           [java.nio ByteBuffer]
           [java.security MessageDigest]
           [java.time Instant]))

(defn- l->b [long]
  {:pre [(int? long)]}
  (-> (ByteBuffer/allocate 8) (.putLong long) .array))

(defn- file-headers [{:keys [path size m-time]}]
  {:pre [(ip/path? path) (int? size) (instance? Instant m-time)]}
  (let [md (MessageDigest/getInstance "MD5")]
    (doto md
      (.update (-> path str .getBytes))
      (.update (l->b size))
      (.update (l->b (.toEpochMilli m-time))))
    {:content-type (-> path
                       .toFile
                       .getName
                       URLConnection/guessContentTypeFromName
                       (or "application/octet-stream"))
     :etag (->> md
                .digest
                (map (partial format "%02x"))
                (str/join ""))
     :accept-ranges "bytes"}))

(defn- stream-file [path pos]
  {:pre [(ip/path? path) (string? pos)]}
  (-> path .toFile FileInputStream.))

(defn handler [root data-dir
               {:keys [method path headers]
                :as _}]
  {:pre [(ip/path? root) (ip/path? data-dir)]}
  (let [{:keys [if-none-match range]} headers
        current (.resolve root path)
        {:keys [link dir?]
         :or {link (ip/path "/")}
         :as attrs} (fs/stat current)
        {:keys [etag]
         :as hdrs} (if attrs (file-headers attrs) nil)]
    (cond (or (nil? attrs) (.startsWith link root)) {:status 404
                                                     :body "404"}
          dir? {:status 307
                :headers {:location (str path "/")}}
          (= (last if-none-match) etag) {:status 304}
          :else (let [st (when (= method :get)
                           (stream-file path range))]
                  {:status 200
                   :headers hdrs
                   :body st}))))

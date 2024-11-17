(ns srv.view
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [lib.interop :as ip]
   [lib.prelude :as lib]
   [srv.fs :as fs])
  (:import [java.io FileInputStream]
           [java.net URLConnection]
           [java.nio ByteBuffer]
           [java.security MessageDigest]
           [java.time Instant]
           [java.util UUID]))

(def ^:private resource-path (str (UUID/randomUUID)))

(defn- l->b [long]
  {:pre [(int? long)]}
  (-> (ByteBuffer/allocate 8) (.putLong long) .array))

(defn- md5 [& bs]
  (let [hasher (MessageDigest/getInstance "MD5")]
    (doseq [b bs]
      (.update hasher b))
    (->> hasher
         .digest
         (map (partial format "%02x"))
         (str/join ""))))

(defn- file-headers [{:keys [path size m-time]}]
  {:pre [(ip/path? path) (int? size) (instance? Instant m-time)]}
  {:content-type (-> path
                     .toFile
                     .getName
                     URLConnection/guessContentTypeFromName
                     (or "application/octet-stream"))
   :etag (md5 (-> path str .getBytes) (l->b size) (l->b (.toEpochMilli m-time)))
   :accept-ranges "bytes"})

(defn- parse-range [header]
  {:pre [((some-fn string? nil?) header)]
   :post [(nil? %) (string? %)]}
  (when header
    (-> header
        (str/replace-first #"^bytes=" ""))))

(defn- stream-file [path range-header]
  {:pre [(ip/path? path) ((some-fn string? nil?) range-header)]}

  (let [st (-> path .toFile FileInputStream.)]
    st))

(defn handler [root data-dir
               {:keys [method path headers]
                :as _}]
  {:pre [(ip/path? root) (ip/path? data-dir)]}
  (let [{:keys [if-none-match range]} headers
        current (if (str/starts-with? path resource-path)
                  (io/resource (lib/remove-prefix path resource-path))
                  (.resolve root path))
        ranges (parse-range range)
        {:keys [link dir?]
         :or {link (ip/path "/")}
         :as attrs} (fs/stat current)
        {:keys [etag]
         :as hdrs} (when attrs (file-headers attrs))]
    (cond (or (nil? attrs) (.startsWith link root)) {:status 404
                                                     :body "404"}
          dir? {:status 307
                :headers {:location (str path "/")}}
          (= (last if-none-match) etag) {:status 304}
          :else (let [st (when (= method :get)
                           (stream-file path ranges))]
                  {:status 200
                   :headers hdrs
                   :body st}))))

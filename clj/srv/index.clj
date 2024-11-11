(ns srv.index
  (:require
   [clojure.string :as str]
   [lib.hiccup :as h]
   [lib.interop :as ip]
   [lib.prelude :as lib]
   [srv.fs :as fs])
  (:import [java.io FileInputStream]
           [java.net URLConnection]
           [java.nio ByteBuffer]
           [java.security MessageDigest]
           [java.time Instant]
           [java.util UUID]))

(def path-glob (str (UUID/randomUUID)))

(def ^:private html-headers {:content-type "text/html; charset=utf-8"})

(defn- rel-path [root {:keys [dir? path]}]
  {:pre [(ip/path? root) (boolean? dir?) (ip/path? path)]}
  (let [rel (-> root (.relativize path) str)]
    (if dir? (str rel "/") rel)))

(defn handler-glob [root data-dir {:keys [path query]}]
  {:pre [(ip/path? root) (ip/path? data-dir)]}
  (let [pattern (-> query :q first)
        dir (.resolve root path)
        st (fs/glob root dir pattern)]
    {:close st
     :body (h/html
            [:ul
             (for [{:keys [size m-time c-time]
                    :as row} (ip/st->seq st)
                   :let [rel (rel-path root row)]]
               [:li
                [:a {:href rel} rel]
                [:span size]
                [:span (str c-time)]
                [:span (str m-time)]])])}))

(defn- l->b [long]
  {:pre [(int? long)]}
  (-> (ByteBuffer/allocate 8) (.putLong long) .array))

(defn- single-file-headers [{:keys [path size m-time]}]
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
  {:pre [(ip/path? path)]}
  (-> path .toFile FileInputStream.))

(defn- index [root current st]
  {:pre [(ip/path? root) (ip/path? current) (ip/stream? st)]}
  (h/html
   [[:head
     [:meta {:name "viewport"
             :content "width=device-width, initial-scale=1.0"}]]
    [:body
     [:nav
      [:ol
       (for [parent (fs/p-parents current)
             :while (and (.startsWith parent root) (not= current root))
             :let [rel (rel-path root {:dir? true
                                       :path parent})]]
         [:li
          [:a {:href rel} rel]])]]
     [:main
      [:ul
       (for [{:keys [size m-time c-time]
              :as row} (ip/st->seq st)
             :let [rel (rel-path root row)]]
         [:li
          [:a {:href rel} rel]
          [:span size]
          [:time {:datetime (str c-time)} (str c-time)]
          [:time {:datetime (str m-time)} (str m-time)]])]]]]))

(defn handler-static [root data-dir {:keys [method path headers]}]
  {:pre [(ip/path? root) (ip/path? data-dir)]}
  (let [current (.resolve root path)
        {:keys [link file? dir?]
         :as attrs} (fs/stat current)]
    (cond
      (or (nil? attrs) (and (lib/not-nil? link) (not (.startsWith link root))))
      {:status 404
       :body "404"}
      file?
      (let [{:keys [if-none-match range]} headers
            {:keys [etag]
             :as hdrs} (single-file-headers attrs)
            st (when (= method :get)
                 (stream-file current range))]
        (if (= (last if-none-match) etag)
          {:status 304}
          {:status 200
           :headers hdrs
           :body st}))
      dir?
      (let [st (fs/walk 1 root current)]
        {:close st
         :headers html-headers
         :body (index root current st)}))))

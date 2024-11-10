(ns srv.index
  (:require
   [clojure.pprint :as pp]
   [lib.prelude :as lib])
  (:import
   [java.nio.file Files Path Paths LinkOption]))

(def ^:private path? (partial instance? Path))

(defn- path [path & paths]
  {:pre [(string? path)]}
  (Path/of path (into-array String paths)))

(defn- attributes [path]
  {:pre [(path? path)]
   :post [(map? %)]}
  (as-> path $
    (Files/readAttributes $ "posix:*" (into-array LinkOption [LinkOption/NOFOLLOW_LINKS]))
    (map (fn [[k v]] [(-> k lib/clj-case keyword) v]) $)
    (into {} $)))

(defn- walk [root dir]
  {:pre [(path? root) (path? dir)]}
  (let [st (Files/newDirectoryStream dir)]
    (for [path st
          :when (Files/isReadable path)
          :let [{:keys [is-symbolic-link is-directory size last-modified-time creation-time]
                 :as attrs} (attributes path)]
          :when (not is-symbolic-link)]
      {:path path
       :size size
       :m-time last-modified-time
       :c-time creation-time})))

(defn os-walk [root dir]
  {:pre [(string? root) (string? dir)]}
  (walk (path root) (path dir)))

(clojure.pprint/pprint (os-walk "." "."))

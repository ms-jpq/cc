(ns srv.index
  (:require
   [clojure.java.io :as io]
   [clojure.pprint :as pp]
   [clojure.walk :as walk]
   [lib.prelude :as lib])
  (:import
   [java.nio.file Files Path Paths LinkOption SecureDirectoryStream]
   [java.nio.file.attribute BasicFileAttributes]))

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

(defn- walk [root current]
  {:pre [(path? root) (path? current)]}
  (let [st (Files/newDirectoryStream current)]
    (for [row st
          :let [{:keys [is-symbolic-link]
                 :as attrs} (attributes row)]
          :when (not is-symbolic-link)]
      (do
        (clojure.pprint/pprint attrs)
        [row]))))

(defn os-walk [root current]
  {:pre [(string? root) (string? current)]}
  (walk (path root) (path current)))

(clojure.pprint/pprint (os-walk "." "."))

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

(defn- os-walk [root dir]
  {:pre [(path? root) (path? dir)]}
  (let [st (Files/list dir)
        que (atom [])]
    (concat
     (filter
      (complement nil?)
      (for [path (-> st .iterator iterator-seq)
            :when (Files/isReadable path)
            :let [{:keys [is-symbolic-link is-directory size last-modified-time creation-time]} (attributes path)]
            :when (or (not is-symbolic-link) 1)]
        (if is-directory
          (do (swap! que conj path)
              nil)
          {:path path
           :size size
           :m-time last-modified-time
           :c-time creation-time})))
     (lazy-seq (do (.close st) []))
     (lazy-seq (mapcat (partial os-walk root) @que)))))

(defn walk [root dir]
  {:pre [(string? root) (string? dir)]}
  (os-walk (path root) (path dir)))

(clojure.pprint/pprint (map identity (walk "." ".")))

(ns srv.index
  (:require
   [clojure.pprint :as pp]
   [lib.interop :as ip]
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

(defn- parse [path]
  {:pre [(path? path)]
   :post [(map? %)]}
  (let [{:keys [is-symbolic-link is-directory size last-modified-time creation-time]} (attributes path)]
    {:path path
     :link (when is-symbolic-link
             (.toRealPath path (into-array LinkOption [])))
     :dir? is-directory
     :size size
     :m-time last-modified-time
     :c-time creation-time}))

(defn- os-walk [root dir]
  {:pre [(path? root) (path? dir)]}
  (let [st (-> dir
               Files/list
               (.filter (ip/->pred #(Files/isReadable %)))
               (.map (ip/->fn parse)))
        que (atom [])]
    (concat
     (filter
      (complement nil?)
      (for [{:keys [path link dir?]
             :as row} (-> st .iterator iterator-seq)]
        (if dir?
          (do (swap! que conj path)
              nil)
          row)))
     (lazy-seq (do (.close st) []))
     (lazy-seq (mapcat (partial os-walk root) @que)))))

(defn walk [root dir]
  {:pre [(string? root) (string? dir)]}
  (os-walk (path root) (path dir)))

(clojure.pprint/pprint (map identity (walk "." ".")))

(ns srv.index
  (:require
   [clojure.pprint :as pp]
   [lib.interop :as ip]
   [lib.prelude :as lib])
  (:import
   [java.nio.file Files Path Paths LinkOption]
   [java.util.stream Stream]))

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

(defn- os-walk [dir]
  {:pre [(path? dir)]}
  (let [que (atom [])
        closed (atom false)
        st (-> dir
               Files/list
               (.filter (ip/->pred #(Files/isReadable %)))
               (.map (ip/->fn #(let [{:keys [dir?]
                                      :as parsed} (parse %)]
                                 (when dir?
                                   (swap! que conj %))
                                 parsed))))
        gen (Stream/generate
             (ip/->supp #(let [{:keys [path]} (peek @que)]
                           (swap! que pop)
                           (when-not @closed
                             (reset! closed true)
                             (.close st))
                           path)))
        st2 (.. gen
                (takeWhile (ip/->pred (complement nil?)))
                (flatMap (ip/->fn os-walk)))]
    (Stream/concat st st2)))

(defn walk [root dir]
  {:pre [(string? root) (string? dir)]}
  (-> dir
      path
      os-walk
      .iterator
      iterator-seq))

(clojure.pprint/pprint (walk "." "."))

(ns srv.fs
  (:require
   [lib.interop :as ip]
   [lib.prelude :as lib])
  (:import
   [java.nio.file FileSystems Files LinkOption Path]
   [java.util.stream Stream]))

(def ^:private path? (partial instance? Path))

(defn- path [path & paths]
  {:pre [(string? path)]}
  (Path/of path (into-array String paths)))

(defn- canonicalize [path]
  {:pre [(path? path)]}
  (.toRealPath path (into-array LinkOption [])))

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
             (canonicalize path))
     :dir? is-directory
     :size size
     :m-time last-modified-time
     :c-time creation-time}))

(defn- stream-dir [dir]
  {:pre [(path? dir)]}
  (let [que (atom [])
        st (-> dir
               Files/list
               (.filter (ip/->pred #(Files/isReadable %)))
               (.map (ip/->fn #(let [{:keys [dir?]
                                      :as parsed} (parse %)]
                                 (when dir?
                                   (swap! que conj %))
                                 parsed)))
               Stream/of
               (.flatMap (ip/->fn identity)))
        gen (Stream/generate
             (ip/->supp #(let [path (peek @que)]
                           (when-not (nil? path)
                             (swap! que pop))
                           path)))
        st2 (.. gen
                (takeWhile (ip/->pred (complement nil?)))
                (flatMap (ip/->fn stream-dir)))]
    (Stream/concat st st2)))

(defn walk [root dir]
  {:pre [(string? root) (string? dir)]}
  (let [real-root (-> root path canonicalize)]
    (-> dir
        path
        stream-dir
        (.filter (ip/->pred (comp (some-fn nil? #(.startsWith % real-root)) :link))))))

(defn glob [root dir pattern]
  {:pre [(string? pattern)]}
  (let [fs (FileSystems/getDefault)
        matcher (->> pattern (str "glob:" dir (.getSeparator fs)) (.getPathMatcher fs))]
    (-> dir
        (walk root)
        (.filter (ip/->pred (comp #(.matches matcher %) :path))))))

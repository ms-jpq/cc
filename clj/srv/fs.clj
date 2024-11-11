(ns srv.fs
  (:require
   [lib.interop :as ip]
   [lib.prelude :as lib])
  (:import
   [java.nio.file Files LinkOption Path NoSuchFileException]
   [java.util.stream Stream]))

(def path? (partial instance? Path))

(defn path [path & paths]
  {:pre [(string? path)]}
  (Path/of path (into-array String paths)))

(defn- canonicalize [path]
  {:pre [(path? path)]}
  (.toRealPath path (into-array LinkOption [])))

(defn stat [path]
  {:pre [(path? path)]
   :post [((some-fn map? nil?) %)]}
  (try
    (as-> path $
      (Files/readAttributes $ "posix:*" (into-array LinkOption [LinkOption/NOFOLLOW_LINKS]))
      (map (fn [[k v]] [(-> k lib/clj-case keyword) v]) $)
      (into {} $))
    (catch NoSuchFileException _ nil)))

(defn- parse [path]
  {:pre [(path? path)]
   :post [(map? %)]}
  (let [{:keys [is-symbolic-link is-directory size last-modified-time creation-time]} (stat path)]
    {:path path
     :link (when is-symbolic-link
             (canonicalize path))
     :dir? is-directory
     :size size
     :m-time last-modified-time
     :c-time creation-time}))

(defn- stream-dir [max-depth depth dir]
  {:pre [(int? max-depth) (int? depth) (path? dir)]}
  (if (>= depth max-depth)
    (Stream/empty)
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
                  (flatMap (ip/->fn (partial stream-dir max-depth (inc depth)))))]
      (Stream/concat st st2))))

(defn walk [max-depth root dir]
  {:pre [(int? max-depth) (path? root) (path? dir)]}
  (let [real-root (canonicalize root)
        pred (ip/->pred (comp (some-fn nil? #(.startsWith % real-root)) :link))]
    (.filter (stream-dir max-depth 0 (canonicalize dir)) pred)))

(defn glob [root dir pattern]
  {:pre [(string? pattern)]}
  (let [fs (.getFileSystem root)
        matcher (->> pattern (str "glob:" dir (.getSeparator fs)) (.getPathMatcher fs))
        pred (ip/->pred (comp #(.matches matcher %) :path))]
    (.filter (walk Long/MAX_VALUE root dir) pred)))

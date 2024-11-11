(ns srv.fs
  (:require
   [lib.interop :as ip]
   [lib.prelude :as lib])
  (:import
   [java.nio.file Files LinkOption NoSuchFileException]
   [java.util.stream Stream]))

(defn canonicalize [path]
  {:pre [(ip/path? path)]}
  (.toRealPath path (into-array LinkOption [])))

(defn p-parents [path]
  {:pre [(ip/path? path)]}
  (let [parent (.getParent path)]
    (if parent
      (cons parent (p-parents parent))
      ())))

(defn- os-stat [path]
  {:pre [(ip/path? path)]
   :post [((some-fn map? nil?) %)]}
  (try
    (as-> path $
      (Files/readAttributes $ "posix:*" (into-array LinkOption [LinkOption/NOFOLLOW_LINKS]))
      (map (fn [[k v]] [(-> k lib/clj-case keyword) v]) $)
      (into {} $))
    (catch NoSuchFileException _ nil)))

(defn stat [path]
  {:pre [(ip/path? path)]
   :post [(map? %)]}
  (let [{:keys [is-symbolic-link is-directory is-regular-file size last-modified-time creation-time]} (os-stat path)]
    {:path path
     :link (when is-symbolic-link
             (canonicalize path))
     :file? is-regular-file
     :dir? is-directory
     :size size
     :m-time (.toInstant last-modified-time)
     :c-time (.toInstant creation-time)}))

(defn- stream-dir [max-depth depth dir]
  {:pre [(int? max-depth) (int? depth) (ip/path? dir)]}
  (if (>= depth max-depth)
    (Stream/empty)
    (let [que (atom [])
          st (-> dir
                 Files/list
                 (.filter (ip/->pred #(Files/isReadable %)))
                 (.map (ip/->fn #(let [{:keys [dir?]
                                        :as parsed} (stat %)]
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
                  (takeWhile (ip/->pred lib/not-nil?))
                  (flatMap (ip/->fn (partial stream-dir max-depth (inc depth)))))]
      (Stream/concat st st2))))

(defn walk [max-depth root dir]
  {:pre [(int? max-depth) (ip/path? root) (ip/path? dir)]}
  (let [pred (ip/->pred (comp (some-fn nil? #(.startsWith % root)) :link))]
    (.filter (stream-dir max-depth 0 (canonicalize dir)) pred)))

(defn glob [root dir pattern]
  {:pre [(string? pattern)]}
  (let [fs (.getFileSystem root)
        matcher (->> pattern (str "glob:" dir (.getSeparator fs)) (.getPathMatcher fs))
        pred (ip/->pred (comp #(.matches matcher %) :path))]
    (.filter (walk Long/MAX_VALUE root dir) pred)))

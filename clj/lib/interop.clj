(ns lib.interop
  (:import
   [java.nio.file Path]
   [java.time Duration]
   [java.util Spliterator]
   [java.util.stream Stream StreamSupport]))

(defmacro ->fn [f]
  `(reify java.util.function.Function
     (apply [_ arg#]
       (~f arg#))))

(defmacro ->bi [f]
  `(reify java.util.function.BinaryOperator
     (apply [_ arg1# arg2#]
       (~f arg1# arg2#))))

(defmacro ->supp [f]
  `(reify java.util.function.Supplier
     (get [_]
       (~f))))

(defmacro ->pred [f]
  `(reify java.util.function.Predicate
     (test [_ arg#]
       (~f arg#))))

(defn ->duration
  [seconds]
  {:pre [(number? seconds)]
   :post [(instance? Duration %)]}
  (-> seconds
      (* 1000000000)
      long
      Duration/ofNanos))

(def stream? (partial instance? Stream))

(defn stream->seq [st]
  {:pre [(stream? st)]
   :post [(seqable? %)]}
  (-> st .iterator iterator-seq))

(defn seq->stream [seq]
  {:pre [seqable? seq]
   :post [(stream? %)]}
  (-> (Spliterator/spliteratorUnknownSize Spliterator/IMMUTABLE)
      (StreamSupport/stream false)))

(def path? (partial instance? Path))

(defn ->path [path & paths]
  {:pre [(string? path)]
   :post [(path? %)]}
  (Path/of path (into-array String paths)))

(ns lib.interop
  (:import
   [java.util.stream Stream]))

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

(defn st->seq [st]
  {:pre [(instance? Stream st)]}
  (-> st .iterator iterator-seq))

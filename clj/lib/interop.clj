(ns lib.interop)

(defmacro ->fn [f]
  `(reify java.util.function.Function
     (apply [_ arg#]
       (~f arg#))))

(defmacro ->supp [f]
  `(reify java.util.function.Supplier
     (get [_]
       (~f))))

(defmacro ->pred [f]
  `(reify java.util.function.Predicate
     (test [_ arg#]
       (~f arg#))))

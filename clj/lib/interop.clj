(ns lib.interop)

(defmacro ->fn [f]
  `(reify java.util.function.Function
     (apply [_ arg#]
       (~f arg#))))

(defmacro ->pred [f]
  `(reify java.util.function.Predicate
     (test [_ arg#]
       (~f arg#))))

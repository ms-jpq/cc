(ns lib.interop
  (:import
   [java.util.function Predicate]))

(defn ->predicate [f]
  {:pre [(fn? f)]}
  (reify Predicate
    (test [_ t]
      (boolean (f t)))))


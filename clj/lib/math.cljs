(ns lib.math
  (:require-macros
   [lib.prelude :refer [math!]]))

(defn pow [x & xs]
  (reduce * x xs))

; ceil exp floor max min

(math! :abs
       :acos :acosh
       :asin :asinh
       :atan :atanh
       :cbrt
       :clz32
       :cos :cosh
       :fround
       :hypot
       :log :log10 :log1p :log2
       :round
       :sin :sinh
       :sqrt
       :tan :tanh
       :trunc)

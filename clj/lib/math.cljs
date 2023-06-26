(ns lib.math
  (:require-macros
   [lib.prelude :refer [math-1!]]))

(defn pow [x y]
  (.pow js/Math x y))

; (math-1! 'acos 'acosh
;          'asin 'asinh
;          'atan 'atanh
;          'cbrt
;          'celi
;          'clz32
;          'cos 'cosh
;          'exp
;          'floor
;          'fround
;          'hypot
;          'log 'log10 'log1p 'log2
;          'round
;          'sin 'sinh
;          'sqrt
;          'tan 'tanh
;          'trunc)

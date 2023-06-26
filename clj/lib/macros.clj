(ns lib.macros)

(defmacro debug! [& xs]
  (when false
    `(do
       ~@xs)))

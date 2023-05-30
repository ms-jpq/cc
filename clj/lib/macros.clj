(ns lib.macros)

(defmacro debug! [& xs]
  (when (System/getenv "DEBUG")
    xs))

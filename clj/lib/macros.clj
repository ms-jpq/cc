(ns lib.macros)

(defmacro debug! [& xs]
  (when (System/getenv "DEBUG")
    `(do
       ~@xs)))

(defmacro suppress [exns & body]
  {:pre [(vector? exns) (not-empty body)]}
  `(try
     ~@body
     ~@(for [exn exns]
         `(catch ~exn e# nil))))

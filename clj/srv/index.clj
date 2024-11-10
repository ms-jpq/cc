(ns srv.index
  (:require
   [clojure.pprint :as pp]
   [lib.interop :as ip]
   [srv.fs :as fs]))

(doseq [x (ip/st->seq (fs/walk "." "."))]
  (pp/pprint (str (:path x)))
  nil)

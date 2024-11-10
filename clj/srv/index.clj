(ns srv.index
  (:require
   [clojure.java.io :as io])
  (:import
   [java.io File]))

(defn- walk [root current]
  {:pre [(instance? File root) (instance? File current)]}
  (for [child (file-seq root)
        :let [ok child]]
    ok))

(defn os-walk [root current]
  {:pre [(string? root) (string? current)]}
  (walk (io/file root) (io/file current)))

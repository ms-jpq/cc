(ns srv.index
  (:require
   [clojure.pprint :as pp]
   [lib.interop :as ip]
   [srv.fs :as fs])
  (:import [java.util UUID]))

(def path-glob (str (UUID/randomUUID)))

(defn handler-glob [])

(defn handler-static [{:keys [path]
                       :as request}]
  nil)

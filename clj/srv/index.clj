(ns srv.index
  (:require
   [clojure.pprint :as pp]
   [lib.interop :as ip]
   [lib.lib :as lib]
   [srv.fs :as fs])
  (:import [java.util UUID]))

(def path-glob (str (UUID/randomUUID)))

(defn handler-glob [{:keys [path]
                     :as request}]
  {
   })

(defn handler-static [{:keys [path]
                       :as request}]
  {})

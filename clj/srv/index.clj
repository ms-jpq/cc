(ns srv.index
  (:require
   [clojure.pprint :as pp]
   [clojure.string :as str]
   [lib.interop :as ip]
   [lib.lib :as lib]
   [srv.fs :as fs])
  (:import [java.util UUID]))

(def path-glob (str (UUID/randomUUID)))

(defn handler-glob [root data {:keys [path]
                               :as request}]
  {:body ""})

(defn handler-static [root data {:keys [path]
                                 :as request}]
  {:body ""})

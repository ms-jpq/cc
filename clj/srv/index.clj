(ns srv.index
  (:require
   [lib.hiccup :as h]
   [lib.interop :as ip]
   [lib.prelude :as lib]
   [srv.fs :as fs])
  (:import [java.util UUID]))

(def path-glob (str (UUID/randomUUID)))

(defn handler-glob [root data-dir {:keys [path query]}]
  {:pre [(fs/path? root) (fs/path? data-dir)]}
  {:body ""})

(defn handler-static [root data-dir {:keys [path]}]
  {:pre [(fs/path? root) (fs/path? data-dir)]}
  (let [dir (.resolve root path)
        st (fs/walk root dir)]
    {:close st
     :body (h/html
            [:main [:ul
                    (for [{:keys [path dir? size m-time c-time]} (ip/st->seq st)
                          :let [rel (-> root (.relativize path) str)]]
                      [:li
                       [:a {:href rel} rel]
                       [:span size]
                       [:span (str c-time)]
                       [:span (str m-time)]])]])}))

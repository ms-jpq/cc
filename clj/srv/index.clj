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
            [:div
             (for [row (ip/st->seq st)]
               [:div
                [:span (str (:path row))]
                [:span (str (:size row))]
                [:span (str (:m-time row))]])])}))

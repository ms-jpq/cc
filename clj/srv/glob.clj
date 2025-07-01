(ns srv.glob
  (:require
   [lib.hiccup :as h]
   [lib.interop :as ip]
   [srv.fs :as fs]
   [srv.index :as idx])
  (:import
   [java.util UUID]))

(def path (str (UUID/randomUUID)))

(defn handler [root data-dir {:keys [path query]}]
  {:pre [(ip/path? root) (ip/path? data-dir)]}
  (let [pattern (-> query :q first)
        dir (.resolve root path)
        st (fs/glob root dir pattern)]
    {:close st
     :headers idx/html-headers
     :body (h/html
            [:ul
             (for [{:keys [size m-time c-time]
                    :as row} (ip/stream->seq st)
                   :let [rel (idx/rel-path root row)]]
               [:li
                [:a {:href rel} rel]
                [:span size]
                [:span (str c-time)]
                [:span (str m-time)]])])}))

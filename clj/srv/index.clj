(ns srv.index
  (:require
   [lib.hiccup :as h]
   [lib.interop :as ip]
   [lib.prelude :as lib]
   [srv.fs :as fs])
  (:import [java.util UUID]))

(def path-glob (str (UUID/randomUUID)))

(def ^:private content-headers {"content-type" "text/html; charset=utf-8"})

(defn- rel-path [root {:keys [dir? path]}]
  {:pre [(fs/path? root) (boolean? dir?) (fs/path? path)]}
  (let [rel (-> root (.relativize path) str)]
    (if dir? (str rel "/") rel)))

(defn handler-glob [root data-dir {:keys [path query]}]
  {:pre [(fs/path? root) (fs/path? data-dir)]}
  (let [pattern (-> query :q first)
        dir (.resolve root path)
        st (fs/glob root dir pattern)]
    {:close st
     :body (h/html
            [:main [:ul
                    (for [{:keys [size m-time c-time]
                           :as row} (ip/st->seq st)
                          :let [rel (rel-path root row)]]
                      [:li
                       [:a {:href rel} rel]
                       [:span size]
                       [:span (str c-time)]
                       [:span (str m-time)]])]])}))

(defn handler-static [root data-dir {:keys [path query]}]
  {:pre [(fs/path? root) (fs/path? data-dir)]}
  (let [current (.resolve root path)
        {:keys [link]
         :as attrs} (fs/stat current)]
    (cond
      (or (nil? attrs) (and (lib/not-nil? link) (not (.startsWith link root))))
      {:status 404
       :body "404"}
      :else
      (let [st (fs/walk 1 root current)]
        {:close st
         :headers content-headers
         :body (h/html
                [[:head
                  [:meta {:name "viewport"
                          :content "width=device-width, initial-scale=1.0"}]]
                 [:body [:main [:ul
                                (for [{:keys [size m-time c-time]
                                       :as row} (ip/st->seq st)
                                      :let [rel (rel-path root row)]]
                                  [:li
                                   [:a {:href rel} rel]
                                   [:span size]
                                   [:time {:datetime (str c-time)} (str c-time)]
                                   [:time {:datetime (str m-time)} (str m-time)]])]]]])}))))

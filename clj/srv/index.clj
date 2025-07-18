(ns srv.index
  (:require
   [lib.hiccup :as h]
   [lib.interop :as ip]
   [srv.fs :as fs]))

(def html-headers {:content-type "text/html; charset=utf-8"})

(defn rel-path [root {:keys [dir? path]}]
  {:pre [(ip/path? root) (boolean? dir?) (ip/path? path)]}
  (let [rel (-> root (.relativize path) str)]
    (if dir? (str rel "/") rel)))

(defn- index [prefix root current st]
  {:pre [(string? prefix) (ip/path? root) (ip/path? current) (ip/stream? st)]}
  (h/html
   [[:head
     [:meta {:name "viewport"
             :content "width=device-width, initial-scale=1.0"}]]
    [:body
     [:nav
      [:ol
       (reverse (for [parent (fs/p-parents current)
                      :while (and (.startsWith parent root) (not= current root))
                      :let [rel (rel-path root {:dir? true
                                                :path parent})]]
                  [:li
                   [:a {:href (str prefix (if (= rel "/") "" rel))} rel]]))]]
     [:main
      [:ul
       (for [{:keys [size m-time c-time]
              :as row} (ip/->seq st)
             :let [rel (rel-path current row)]]
         [:li
          [:a {:href rel} rel]
          [:span size]
          [:time {:datetime (str c-time)} (str c-time)]
          [:time {:datetime (str m-time)} (str m-time)]])]]]]))

(defn handler [prefix root data-dir
               {:keys [path]
                :as _}]
  {:pre [(ip/path? root) (ip/path? data-dir)]}
  (let [current (.resolve root path)
        st (fs/walk 1 root current)]
    {:close st
     :headers html-headers
     :body (index prefix root current st)}))

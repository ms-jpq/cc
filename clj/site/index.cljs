(ns site.index
  (:require
   [lib.react :refer [rend]]))

(enable-console-print!)
(.clear js/console)

(defonce state (atom nil))

(def render!
  (when-let [main (-> js/document
                      .-body
                      (.querySelector "main"))]
    (rend main)))

(remove-watch state nil)
(add-watch state nil #(render! %4))

(defn- excel [rows cols]
  {:pre [(int? rows) (int? cols)]}
  [:div.excel
   (for [_ (range cols)]
     [:div.col (for [_ (range rows)]
                 [:div "cell"])])])

(reset! state (excel 10 10))

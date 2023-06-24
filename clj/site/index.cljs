(ns site.index
  (:require
   [clojure.string :as s]
   [lib.react :refer [rend]]))

; (enable-console-print!)
(.clear js/console)

(defonce state (atom nil))

(def render!
  (when-let [main (-> js/document
                      .-body
                      (.querySelector "main"))]
    (rend main)))

(remove-watch state nil)
(add-watch state nil #(render! %4))

(def a2z (s/split "ABCDEFGHIJKLMNOPQRSTUVWXYZ" ""))

(def col-name
  (memoize
   (fn [col]
     {:pre [(int? col)]}
     (let [n (count a2z)]
       (if (< col n)
         (get a2z col)
         (str (->> n
                   (quot col)
                   (dec)
                   (col-name))
              (->> n
                   (rem col)
                   col-name)))))))

(defn- excel [rows cols]
  {:pre [(int? rows) (int? cols)]}
  [:div.sheet
   {:class-name (str "columns-" (inc cols))}
   [:div "."]
   (for [row (range rows)]
     [:div (str row)])
   (for [c (range cols)]
     [:div.col.flex.flex-nowrap.flex-col.justify-between
      [:div (col-name c)]
      (for [_ (range rows)]
        [:div.row
         (str "_")])])])

(reset! state (excel 60 40))

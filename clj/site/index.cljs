(ns site.index
  (:require
   [clojure.string :as s]
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

(def a2z (s/split "abcdefghijklmnopqrstuvwxyz" ""))

(def col-name
  (memoize
   (fn [col]
     {:pre [(int? col)]
      :post [(string? %)]}
     (let [n (count a2z)]
       (if (< col n)
         (get a2z col)
         (str (->> n (quot col) dec col-name)
              (->> n (rem col) col-name)))))))

(defn- excel [{:keys [rows cols]}]
  {:pre [(int? rows) (int? cols)]}
  (let [col-range (range cols)]
    [:div
     [:label {:for "edit"} [:span [:i "f"] "(x)"]]
     [:input#edit]
     [:table.table-auto.divide-y.border.border-gray-200
      [:tr.divide-x [:th]
       (for [col (map col-name col-range)]
         [:th.col.text-start.uppercase col])]
      (for [row (->> rows range (map inc))]
        [:tr.divide-x.even:bg-gray-100
         [:td {:class "row text-end after:whitespace-pre after:content-['_']"}
          row]
         (for [_ col-range]
           [:td
            [:input.resize {:default-value _}]])])]]))

(reset! state (excel {:rows 9
                      :cols 9}))

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
  (let [col-names (->> cols range (map col-name))]
    [:div
     [:label {:for "edit"} [:span [:i "f"] "(x)"]]
     [:input#edit]
     [:table.table-auto.divide-y.border.border-gray-200
      [:tr.divide-x [:th]
       (for [col col-names]
         [:th.col.text-start.uppercase col])]
      (for [row (->> rows range (map inc))]
        [:tr.divide-x.even:bg-gray-100
         [:td {:class "row text-end after:whitespace-pre after:content-['_'] min-w-[3rem]"}
          row]
         (for [col col-names]
           [:td {:class (str "row-" row " " "col-" col)}
            [:div.flex.flex-col
             [:div.flex.flex-row
              [:input.grow.overflow-auto.text-ellipsis {:default-value "box"
                                                        :disabled true}]
              [:span {:class "cursor-ew-resize w-0.5"}]]
             [:span {:class "cursor-ns-resize h-0.5"}]]])])]]))

(reset! state (excel {:rows 20
                      :cols 20}))

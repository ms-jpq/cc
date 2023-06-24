(ns site.index
  (:require
   [clojure.string :as s]
   [lib.react :refer [rend]]))

(enable-console-print!)
(.clear js/console)

(defonce state (atom nil))

(defonce render!
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
           [:td {:data-row row
                 :data-col col
                 :data-cell (str col row)}
            [:label {:class "inline-flex after:whitespace-pre after:content-['_'] after:w-0.5 after:cursor-ew-resize"}
             [:label {:class "inline-flex flex-col after:whitespace-pre after:content-['_'] after:h-0.5 after:cursor-ns-resize"}
              [:input {:value nil}]]]])])]]))

(reset! state (excel {:rows 5
                      :cols 5}))

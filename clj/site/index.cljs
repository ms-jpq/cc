(ns site.index
  (:require
   [clojure.string :as s]
   [lib.react :refer [rend]]))

(enable-console-print!)
(.clear js/console)

(defonce state (atom nil))
(set-validator! state (constantly true))
(remove-watch state nil)

(defonce render!
  (when-let [main (-> js/document
                      .-body
                      (.querySelector "main"))]
    (rend main)))

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
    [:div.flex.flex-col.gap-y-4
     [:header.select-none.text-center.text-4xl "Excel"]
     [:div.flex.flex-col
      [:hr]
      [:label.inline-flex.select-none
       [:span [:i "f"] "(x)"
        {:class "whitespace-nowrap before:content-['_'] after:content-['_'] before:inline-block after:inline-block before:w-2 after:w-2"}]
       [:input.grow.w-full]]
      [:table.table-auto.divide-y.border.border-gray-200
       [:tr.divide-x {:data-tr 0} [:th]
        (for [col col-names]
          [:th.select-none.text-start.uppercase {:data-th col} col])]
       (for [row (->> rows range (map inc))]
         [:tr.group.divide-x {:data-tr row}
          [:td {:data-row-label row}
           {:class "select-none group-even:bg-gray-100 text-end whitespace-nowrap after:content-['_'] after:inline-block after:w-4"}
           row]
          (for [col col-names]
            [:td {:data-row row
                  :data-col col
                  :data-cell (str col row)}
             [:label {:class "inline-flex w-full after:whitespace-pre after:content-['_'] after:w-0.5 after:cursor-ew-resize"}
              [:label {:class "inline-flex flex-col w-full after:whitespace-pre after:content-['_'] after:h-0.5 after:cursor-ns-resize"}
               [:input {:value nil}]]]])])]]]))

(reset! state (excel {:rows 5
                      :cols 5}))

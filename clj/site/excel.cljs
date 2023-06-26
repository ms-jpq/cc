(ns site.excel
  (:require
   [clojure.string :as s]))

(def ^:private a2z (s/split "abcdefghijklmnopqrstuvwxyz" ""))

(def ^:private col-name
  (memoize
   (fn [col]
     {:pre [(int? col)]
      :post [(string? %)]}
     (let [n (count a2z)]
       (if (< col n)
         (get a2z col)
         (str (->> n (quot col) dec col-name)
              (->> n (rem col) col-name)))))))

(defn- parse [expr]
  {:pre [(string? expr)]}
  {:refs #{}
   :expr expr
   :val "22"})

(defn excel [atom {:keys [selected cells]
                   {:keys [rows cols]} :dimension}]
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
          (for [col col-names
                :let [cell {:row row
                            :col col}]]
            [:td {:data-row row
                  :data-col col
                  :data-cell (str col row)}
             [:label {:class "inline-flex w-full after:whitespace-pre after:content-['_'] after:w-0.5 after:cursor-ew-resize"}

              [:label {:class "inline-flex flex-col w-full after:whitespace-pre after:content-['_'] after:h-0.5 after:cursor-ns-resize"}
               [:input
                (when-not (= selected cell)
                  {:value (->> cell (get cells) :val)})
                {:onfocus #(swap! atom assoc :selected cell)}
                {:oninput #(swap! atom update-in [:cells] assoc cell (-> % (.. -currentTarget -value) parse))}]]]])])]]]))

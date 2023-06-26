(ns site.excel
  (:require
   [clojure.string :as s]
   [lib.js :refer [js-delay]]
   [lib.math]))

(def ^:private a2z (s/split "abcdefghijklmnopqrstuvwxyz" ""))

(def ^:private op-prefix #{'-})

(def ^:private infix-alias {'% 'mod
                            '** 'lib.math/pow})

(def ^:private op-infix {'+ 0
                         '- 0
                         '* 1
                         '/ 1
                         '% 1
                         '** 2})

(cljs.pprint/pprint (ns-publics 'lib.math))
(def ^:private op-fn {'abs 1
                      'min 2
                      'max 2
                      'lib.math/cos 1})

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
   :ref-by #{}
   :err false
   :expr expr
   :val (str "->" expr)})

(def ^:private j-delay (js-delay 64))

(defn excel [atom {:keys [selected cells in-cell]
                   {:keys [rows cols]} :dimension}]
  {:pre [(int? rows) (int? cols)]}
  (let [->val (fnil identity "")
        col-names (->> cols range (map col-name))]
    [:div.flex.flex-col.items-center.gap-y-4
     [:header.select-none.text-4xl "Excel"]
     [:div.flex.flex-col
      {:class "w-[60rem]"}
      [:hr]
      [:label.inline-flex.select-none
       [:span.whitespace-nowrap.before:inline-block.after:inline-block.before:w-2.after:w-2
        {:class "before:content-['_'] after:content-['_']"}
        [:i "f"] [:span.uppercase (str "(" (:col selected) (:row selected) ")")]]
       [:input.grow.w-full
        {:disabled (nil? selected)
         :value (let [key (if in-cell :val :expr)]
                  (->> selected (get cells) key ->val))
         :onfocus #(swap! atom assoc :in-cell false)
         :onblur #(swap! atom assoc :in-cell true)
         :oninput #(when selected
                     (swap! atom update-in [:cells] assoc selected (-> % (.. -currentTarget -value) parse)))}]]
      [:div.overflow-scroll
       [:table.table-fixed.divide-y.border.border-gray-200
        [:thead
         [:tr.divide-x {:data-tr 0}
          [:th]
          (for [col col-names]
            [:th.select-none.text-start.uppercase {:data-th col} col])]]
        [:tbody
         (for [row (->> rows range (map inc))]
           [:tr.group.divide-x {:data-tr row}
            [:td.select-none.text-end.whitespace-nowrap.after:inline-block.after:w-4.group-even:bg-gray-100
             {:class "after:content-['_']"}
             {:data-td row} row]
            (for [col col-names
                  :let [cell {:row row
                              :col col}]]
              [:td {:data-row row
                    :data-col col
                    :data-cell (str col row)
                    :onclick nil}
               [:label.inline-flex.w-full.after:whitespace-pre.after:cursor-ew-resize
                {:class "after:w-0.5 after:content-['_']"}
                [:label.inline-flex.flex-col.w-full.after:whitespace-pre.after:cursor-ns-resize
                 {:class "after:h-0.5 after:content-['_']"}
                 [:input
                  {:value (->val (if (and in-cell (= selected cell))
                                   (->> cell (get cells) :expr)
                                   (->> cell (get cells) :val)))
                   :onfocus #(swap! atom assoc :selected cell :in-cell true)
                   :oninput (fn [event]
                              (let [value (.. event -currentTarget -value)]
                                (j-delay #(swap! atom update-in [:cells] assoc cell (parse value)))))}]]]])])]

        [:tfoot]]]]
     [:footer 22]]))

(ns site.index
  (:require
   [lib.react :refer [rend]]
   [site.excel :refer [excel]]))

(enable-console-print!)
(.clear js/console)

(defonce state (atom {:dimension {:rows 15
                                  :cols 15}
                      :selected nil
                      :cells {}
                      :in-cell false}))

(set-validator! state (fn [{{:keys [rows cols]} :dimension
                            :keys [selected cells in-cell]
                            :as s}]
                        (letfn [(v-pos? [{:keys [row col]
                                          :as cell}]
                                  (and (map? cell) (int? row) (string? col)))
                                (v-cell? [[self {:keys [refs ref-by expr err val]
                                                 :as c}]]
                                  (and
                                   (map? c)
                                   (set? refs)
                                   (set? ref-by)
                                   (boolean? err)
                                   (every? (every-pred (partial contains? cells) (partial not= self)) refs)
                                   (string? expr)
                                   (string? val)))]

                          (and
                           (map? s)
                           (and (int? rows) (int? cols) ((some-fn nil? v-pos?) selected))
                           (and (map? cells)
                                (every? (every-pred (comp v-pos? first) v-cell?) cells))
                           (boolean? in-cell)))))

(remove-watch state nil)

(defonce render!
  (when-let [main (-> js/document
                      .-body
                      (.querySelector "main"))]
    (rend main)))

(add-watch state nil #(render! (excel %2 %4)))
(add-watch state :debug #(cljs.pprint/pprint %4))
(remove-watch state :debug)
(swap! state identity)

(ns site.index
  (:require
   [lib.react :refer [rend]]
   [site.excel :refer [excel]]))

(enable-console-print!)
(.clear js/console)

(defonce state (atom {:dimension {:rows 5
                                  :cols 5}
                      :selected nil
                      :cells {}}))

(set-validator! state (fn [{{:keys [rows cols]} :dimension
                            :keys [selected cells]
                            :as s}]
                        (letfn [(v-pos? [{:keys [row col]
                                          :as cell}]
                                  (and (map? cell) (int? row) (string? col)))
                                (v-cell? [[self {:keys [refs expr val]
                                                 :as c}]]
                                  (and
                                   (map? c)
                                   (set? refs)
                                   (every? #(and (contains? cells %) (not= % self)) refs)
                                   (string? expr)
                                   (string? val)))]

                          (and
                           (map? s)
                           (and (int? rows) (int? cols) ((some-fn nil? v-pos?) selected))
                           (and (map? cells)
                                (every? #(and (->> % first v-pos?) (v-cell? %)) cells))))))

(remove-watch state nil)

(defonce render!
  (when-let [main (-> js/document
                      .-body
                      (.querySelector "main"))]
    (rend main)))

(add-watch state nil #(render! (excel %2 %4)))
(add-watch state :debug #(cljs.pprint/pprint %4))
(swap! state identity)

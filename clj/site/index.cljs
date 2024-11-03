(ns site.index
  (:require
   [lib.react :refer [rend]]
   [site.excel :refer [excel]]))

(enable-console-print!)
(.clear js/console)

(defonce state (atom {}))

(set-validator! state (fn [{}]))

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

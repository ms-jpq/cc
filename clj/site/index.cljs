(ns site.index
  (:require
   [lib.react :as react]))

(enable-console-print!)
(.clear js/console)

(defonce state (atom {}))

(set-validator! state (fn [{}]))

(remove-watch state nil)

(defonce render!
  (when-let [main (-> js/document
                      .-body
                      (.querySelector "main"))]
    (react/rend main)))

(add-watch state nil #(render! []))
(add-watch state :debug #(cljs.pprint/pprint %4))
(remove-watch state :debug)
(swap! state identity)

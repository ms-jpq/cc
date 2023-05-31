(ns site.index
  (:require
   [lib.react :refer [rend]]))

(enable-console-print!)
(.clear js/console)

(defonce state (atom 1))

(def render!
  (when-let [main (-> js/document
                      .-body
                      (.querySelector "main"))]
    (rend main)))

(render! [:div.bg-stone-900
          [:h1 "Hello World!"]
          [:p "This is a paragraph."]
          [:p "This is another paragraph."]])

(render! [:div.bg-stone-900
          [:h1 (apply str (reverse "Hello World!"))]
          [:b "BEGIN"]
          [:p {:key 1} "1"]
          [:p {:key 2} "2"]
          [:p {:key 3} "3"]
          [:p "END"]])

(render! [:div.bg-stone-950
          [:h1 (apply str (reverse "Hello World!"))]
          [:p {:key 2} "2"]
          [:p {:key 1} "1"]
          [:p {:key 4} "4"]
          [:p {:key 3} "3"]
          [:s "Stroke"]])

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
          [:b "This is a paragraph."]
          [:p {:key 1} "b1"]
          [:p {:key 2} "b2"]
          [:p {:key 3} "b3"]
          [:p "This is another paragraph."]])

(render! [:div.bg-stone-950
          [:h1 (apply str (reverse "Hello World!"))]
          [:p {:key 2} "bn"]
          [:p {:key 1} "b1"]
          [:p {:key 5} "b1"]
          [:p {:key 3} "b3"]
          [:p "This is another paragraph."]])

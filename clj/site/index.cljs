(ns site.index
  (:require
   [lib.react :refer [rend]]))

(enable-console-print!)
(.clear js/console)

(defonce state (atom nil))

(def render!
  (when-let [main (-> js/document
                      .-body
                      (.querySelector "main"))]
    (rend main)))

(remove-watch state nil)
(add-watch state nil #(render! %4))

(reset! state [:div.bg-stone-900
               [:h1 "Hello World!"]
               [:p "This is a paragraph."]
               [:p "This is another paragraph."]])

(reset! state [:div.bg-stone-900
               [:h1 (apply str (reverse "Hello World!"))]
               [:b "BEGIN"]
               [:p {:key 1} "1"]
               [:p {:key 2} "2"]
               [:p {:key 3} "3"]
               [:p "END"]])

(reset! state [:div.bg-stone-100
               [:h1 (apply str (reverse "Hello World!"))]
               [:p.text-amber-600 {:key 2} "2"]
               [:p {:key 1} "1"]
               [:p {:key 4} "4"]
               [:p {:key 3} "3"]
               [:s "Stroke"]])

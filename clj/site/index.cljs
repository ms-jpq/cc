(ns site.index
  (:require [lib.react :refer [rend]]))

(enable-console-print!)
(.clear js/console)

(defonce state (atom 1))
(def main (-> js/document
              .-body
              (.querySelector "main")))

(aset main "innerHTML" "")

(def render (rend main [:div]))

(render [:div
         [:h1 "Hello World!"]
         [:p "This is a paragraph."]
         [:p "This is another paragraph."]])

(render [:div
         [:h1 (apply str (reverse "Hello World!"))]
         [:p "This is a paragraph."]
         [:p "This is another paragraph."]])

(render [:div
         [:h1 (apply str (reverse "Hello World!"))]
         [:s "This is a paragraph."]
         [:p "This is another paragraph."]])

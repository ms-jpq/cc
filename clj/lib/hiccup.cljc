(ns lib.hiccup
  (:require
   [clojure.string :as str])
  (:import
   [java.net URLEncoder]
   [java.nio.charset StandardCharsets]))

(defn- escape [html]
  {:pre [(string? html)]}
  (-> html
      (str/replace #"&" "&amp;")
      (str/replace #"<" "&lt;")
      (str/replace #">" "&gt;")
      (str/replace #"\"" "&quot;")
      (str/replace #"'" "&#39;")))

(defn- encode [s]
  {:pre [((some-fn string? int?) s)]}
  (-> s (str) (URLEncoder/encode StandardCharsets/UTF_8)))

(defn- indent [n]
  {:pre [(int? n)]}
  (cons "\n" (repeat (* 2 n) " ")))

(defmulti ^:private walk-impl #(cond (nil? %2) :nil
                                     (string? %2) :str
                                     (number? %2) :num
                                     (map? %2) :map
                                     (seqable? %2) :seq))

(defmethod walk-impl :nil [_ _] nil)
(defmethod walk-impl :str [depth s] (concat (indent depth) [(escape s)]))
(defmethod walk-impl :num [depth n] (concat (indent depth) [(str n)]))

(defmethod walk-impl :map [_ attrs]
  (for [[k v] attrs] (str " " (name k) "=\"" (encode v) "\"")))

(defmethod walk-impl :seq [depth [x & xs :as xss]]
  (flatten
   (if-not (keyword? x)
     (for [v xss] (walk-impl (inc depth) v))
     (let [tag (name x)
           indented (indent depth)
           closed (atom false)]
       (concat indented
               ["<" tag]
               (for [v (concat xs [[]])
                     :let [st (walk-impl (inc depth) v)]
                     :when (not (and @closed (map? v)))]
                 (if (or @closed (map? v))
                   st
                   (do (reset! closed true)
                       (cons ">" st))))
               indented
               ["</" tag ">"])))))

(defn walk [hiccup]
  {:pre [(seqable? hiccup)]}
  (walk-impl 0 hiccup))

(def hiccup [:p [:div {:class "hi"} [:span 2]] [:table]])

(print (str/join "" (walk hiccup)))

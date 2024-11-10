(ns lib.hiccup
  (:require
   [clojure.string :as str])
  (:import
   [java.lang System]
   [java.net URLEncoder]
   [java.nio.charset StandardCharsets]))

(def ^:private html-esc
  {\& "&amp;"
   \< "&lt;"
   \> "&gt;"
   \" "&quot;"
   \' "&#x27;"})

(defn escape [s]
  {:pre [(string? s)]}
  (str/escape s html-esc))

(defn- encode [s]
  {:pre [((some-fn string? number?) s)]}
  (-> s (str) (URLEncoder/encode StandardCharsets/UTF_8)))

(def ^:private line-sep (System/getProperty "line.separator"))

(defn- indent [n]
  {:pre [(int? n)]}
  (cons line-sep (repeat (* 2 n) " ")))

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
                     :let [map (map? v)
                           st (walk-impl (inc depth) v)]
                     :when (not (and @closed map))]
                 (if (or @closed map)
                   st
                   (do (reset! closed true)
                       (cons ">" st))))
               indented
               ["</" tag ">"])))))

(defn walk [hiccup]
  {:pre [(seqable? hiccup)]}
  (drop 1 (walk-impl 0 hiccup)))

(def hiccup [:p [:div {:class "hi"} [:span {:class "hi"} 2 "adsf"]] [:table]])

(print (str/join "" (walk hiccup)))

(ns lib.hiccup
  (:require
   [clojure.string :as str]))

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
  (-> s (str)
      #?(:clj (java.net.URLEncoder/encode java.nio.charset.StandardCharsets/UTF_8)
         :cljs (js/encodeURIComponent))))

(defn- indent [n]
  {:pre [(int? n)]}
  (cons "\n" (repeat (* 2 n) " ")))

(defmulti ^:private walk
  #(cond (nil? %2) :nil
         (string? %2) :str
         (number? %2) :num
         (map? %2) :map
         (seqable? %2) :seq))

(defmethod walk :nil [_ _] nil)
(defmethod walk :str [depth s] (concat (indent depth) [(escape s)]))
(defmethod walk :num [depth n] (concat (indent depth) [(str n)]))

(defmethod walk :map [_ attrs]
  (for [[k v] attrs] (str " " (name k) "=\"" (encode v) "\"")))

(defmethod walk :seq [depth [x & xs :as xss]]
  (flatten
   (if-not (keyword? x)
     (for [v xss] (walk (inc depth) v))
     (let [tag (name x)
           indented (indent depth)
           closed (atom false)]
       (concat indented
               ["<" tag]
               (for [v xs
                     :let [map (map? v)]
                     :when (not (and @closed map))
                     :let [st (walk (inc depth) v)]]
                 (if (or @closed map)
                   st
                   (do (reset! closed true)
                       (cons ">" st))))
               (lazy-seq
                (if @closed
                  (concat indented ["</" tag ">"])
                  ["/>"])))))))

(defn html [hiccup]
  {:pre [(seqable? hiccup)]}
  (cons
   "<!doctype html>"
   (walk 0 [:html hiccup])))

(ns lib.react
  (:require [clojure.string :as s]))

(def kw-re #"(?:(?<=^:)|(#|\.))(\w+)")

(def v-map {"#" [:id "-"] "." [:class-name " "]})

(defn- parse-kw [kw]
  (let [key (str kw)
        [[_ _ tag] & groups] (re-seq kw-re key)
        parts (->> groups
                   (group-by second)
                   (map (fn [[k v]]
                          (let [[id sep] (get v-map k)]
                            [id (s/join sep (map last v))])))
                   (into {}))]
    {:tag tag
     :id (:id parts)
     :class-name (:class-name parts)}))

(defn parse [[x & xs]]
  {:pre [(keyword? x)]}
  (let [kw-props (parse-kw x)]
    kw-props))

(println (parse [:a#asd.c#zxc.d {:state "fuck"} {:style "style"} {:data "data"}]))

(println "ok!")

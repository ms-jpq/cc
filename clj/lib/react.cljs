(ns lib.react
  (:require [cljs.pprint :as p]
            [clojure.string :as s]))

(def kw-re #"(?:(?<=^:)|(#|\.))(\w+)")

(def v-map {"#" [:id "-"] "." [:class-name " "]})

(defn- parse-kw [kw]
  {:pre (keyword? kw)}
  (let [key (str kw)
        [[_ _ tag-name] & groups] (re-seq kw-re key)
        grouped (for [[k v] (group-by second groups)
                      :let [[id sep] (get v-map k)]]
                  [id (s/join sep (map last v))])
        {:keys [id class-name]} (into {} grouped)]
    {:tag-name tag-name
     :id id
     :class-name class-name}))

(defn parse
  "
  el is one of {nil | string | [:tag-name {:prop-key prop-val}...  seq[el]...]}
  "
  [el]
  (cond
    (nil? el) nil
    (string? el) el
    :else (let [[x & xs] el]
            (if (not (keyword? x))
              (->> el (map parse) (remove empty?))
              (let [kw-props (parse-kw x)
                    {:keys [r-props r-children]} (group-by
                                                  #(if (map? %) :r-props :r-children)
                                                  xs)
                    {:keys [tag-name key style state] :as props} (->> r-props (apply merge-with into) (merge kw-props))
                    children  (->> r-children (map parse) (remove empty?))]
                {:tag-name tag-name
                 :key key
                 :props (dissoc props :tag-name :key :state :style)
                 :state state
                 :style style
                 :children children})))))

(p/pprint (parse [:a#asd.c#zxc.d {:key 2} "abc" [[] []] [nil] nil nil "def" {:state {:a "a" :b #{:c}}} {:style {:background-color "blue"}} {:state {:d "d" :b #{:zz}} :style {:color "red"}} {:data-stdin "somthing funny"}]))

(p/pprint (parse [[:a] [:b]]))

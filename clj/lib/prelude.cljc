(ns lib.prelude
  (:require
   [clojure.string :as str]))

(def path-sep "/")

(defn long-zip [sentenial & seqs]
  (let [rep (repeat sentenial)
        not-eof? (->> sentenial (partial identical?) (partial not-every?))]
    (->> seqs
         (map #(lazy-cat % rep))
         (apply map vector)
         (take-while not-eof?))))

(defn remove-prefix [s p]
  {:pre [(string? s) (string? p)]}
  (if (str/starts-with? s p)
    (subs s (count p))
    s))

(def ^:private re-camel-case #"-(\w)")
(def ^:private re-kebab-case #"([A-Z]|\d)")

(defn js-case [kw]
  {:pre [((some-fn keyword? string?) kw)]}
  (-> kw
      name
      (str/replace re-camel-case (comp str/upper-case second))))

(defn clj-case [kw]
  {:pre [((some-fn keyword? string?) kw)]}
  (-> kw
      name
      (str/replace re-kebab-case (comp (partial str "-") str/lower-case second))))

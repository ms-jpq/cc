(ns lib.prelude
  (:require
   [clojure.string :as str]))

(defn long-zip [sentenial & seqs]
  (let [rep (repeat sentenial)
        not-eof? (->> sentenial (partial identical?) (partial not-every?))]
    (->> seqs
         (map #(lazy-cat % rep))
         (apply map vector)
         (take-while not-eof?))))

(defn update! [m k f & args]
  {:pre [(fn? f)]}
  (assoc! m k (apply f (get m k) args)))

(def ^:private re-case #"-(\w)")
(defn js-case [kw]
  {:pre [((some-fn keyword? string?) kw)]}
  (-> kw
      name
      (str/replace re-case (comp str/upper-case second))))

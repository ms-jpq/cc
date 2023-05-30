(ns lib.prelude
  (:require
   [clojure.string :as s]))

(defn long-zip [sentenial & seqs]
  (let [rep (repeat sentenial)
        eof? (partial identical? sentenial)]
    (->> seqs
         (map #(lazy-cat % rep))
         (apply map vector)
         (take-while #(not (every? eof? %))))))

(def ^:private re-case #"-(\w)")
(defn js-case [kw]
  (-> kw
      (name)
      (s/replace re-case #(let [[_ m] %] (-> m str s/upper-case)))))

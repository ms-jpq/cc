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
  {:pre [((some-fn keyword? string?) kw)]}
  (-> kw
      name
      (s/replace re-case #(->> % second s/upper-case))))

(defmacro math! [& ops]
  {:pre [(every? keyword? ops)]}
  (doseq [op ops
          :let [op-name (symbol op)
                x (gensym)]]
    `(defn ~op-name [~x]
       (. js/Math ~op-name ~x))))

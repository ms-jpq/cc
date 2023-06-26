(ns lib.prelude
  (:require
   [clojure.string :as s]))

(defn long-zip [sentenial & seqs]
  (let [rep (repeat sentenial)
        not-eof? (->> sentenial (partial identical?) (partial not-every?))]
    (->> seqs
         (map #(lazy-cat % rep))
         (apply map vector)
         (take-while not-eof?))))

(def ^:private re-case #"-(\w)")
(defn js-case [kw]
  {:pre [((some-fn keyword? string?) kw)]}
  (-> kw
      name
      (s/replace re-case (comp s/upper-case second))))

(defmacro math-1! [& ops]
  {:pre [(every? symbol? ops)]}
  (for [op ops
        :let [op-name (symbol op)
              x (gensym)]]
    `(defn ^:math-1 ~op-name [~x]
       (. js/Math ~op-name ~x))))

(def arity (memoize
            (fn [f]
              {:pre [(fn? f)]}
              (->> f meta :arglists first count))))

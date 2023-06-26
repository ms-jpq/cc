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

(defmacro math-1! [& ops]
  {:pre [(every? keyword? ops)]}
  (for [op ops
        :let [op-name (symbol op)
              x (gensym)]]
    `(defn ^:math-1 ~op-name [~x]
       (. js/Math ~op-name ~x))))

(def arity (memoize
            (fn [f]
              {:pre [(fn? f)]}
              (->> f meta :arglists first count))))

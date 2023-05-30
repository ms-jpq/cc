(ns lib.prelude)

(defn long-zip [sentenial & seqs]
  (let [rep (repeat sentenial)
        eof? (partial identical? sentenial)]
    (->> seqs
         (map #(lazy-cat % rep))
         (apply map vector)
         (take-while #(not (every? eof? %))))))

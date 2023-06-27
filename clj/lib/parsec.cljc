(ns lib.parsec
  (:require
   [clojure.string :as s]
   [lib.prelude :refer [xor]]))

(defn >result-valid? [step]
  (let [has? (partial contains? step)]
    (and (map? step)
         (xor (and (has? :ok) (has? :xs)) (has? :err)))))

(defn >ok? [step]
  {:pre [(>result-valid? step)]}
  (contains? step :ok))

(def digits (->> 10 range (map str)))
(def spaces [\newline \tab \space])

(def >err? (complement >ok?))

(defn >map [parser & fs]
  {:pre [(fn? parser) (every? fn? fs)]}
  (let [f (->> fs reverse comp)]
    (fn [stream]
      {:pre [(seqable? stream)]
       :post [(>result-valid? %)]}
      (let [{:keys [ok]
             :as step} (parser stream)]
        (if (>ok? step)
          (f ok)
          step)))))

(defn >bind [parser & parsers]
  {:pre [(fn? parser) (every? fn? parsers)]}
  (fn [stream]
    {:pre [(seqable? stream)]
     :post [(>result-valid? %)]}
    (loop [pss parsers
           acc (parser stream)]
      (let [[p & ps] pss
            {:keys [ok xs]} acc]
        (if (or (>err? acc) (empty? pss))
          acc
          (recur ps (p ok xs)))))))

(defn >and [& parsers]
  {:pre [(every? fn? parsers)]}
  (fn [stream]
    {:pre [(seqable? stream)]
     :post [(>result-valid? %)]}
    (loop [pss parsers
           acc {:ok []
                :xs stream}]
      (let [[p & ps] pss]
        (cond
          (contains? acc :err) (dissoc acc :ok)
          (empty? pss) acc
          :else (let [{:keys [ok xs err]
                       :as step} (->> acc :xs p)
                      next (if (>ok? step)
                             (-> acc
                                 (update :ok conj ok)
                                 (assoc :xs xs))
                             (assoc acc :err err))]
                  (recur ps next)))))))

(defn >or [& parsers]
  {:pre [(every? fn? parsers)]}
  (fn [stream]
    {:pre [(seqable? stream)]
     :post [(>result-valid? %)]}
    (loop [pss parsers
           acc {:err []}]
      (let [[p & ps] pss]
        (if (empty? pss) acc
            (let [{:keys [err]
                   :as step} (p stream)]
              (if (>ok? step)
                step
                (recur ps (update acc :err conj err)))))))))

(defn >many
  ([parser n]
   {:pre [(fn? parser) (int? n)]}
   (>and (repeat n parser)))
  ([parser]
   {:pre [(fn? parser)]}
   (fn [stream]
     {:pre [(seqable? stream)]
      :post [(>result-valid? %)]}
     (loop [acc {:ok []
                 :xs stream}]
       (let [{:keys [ok xs]
              :as step} (->> acc :xs parser)]
         (if (>ok? step)
           (recur {:ok (conj (:ok acc) ok)
                   :xs xs})
           acc))))))

(defn >lit [& strings]
  {:pre [(every? string? strings)]}
  (let [parse #(if (s/starts-with? %2 %1)
                 {:ok %1
                  :xs (-> %1 count (.substring %2))}
                 {:err "TODO"})]
    (apply >or (map (partial parse) strings))))

(def >eof (>lit ""))

(defn >many-1 [parser]
  {:pre [(fn? parser)]}
  (>and parser (>many parser)))

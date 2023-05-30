(ns lib.react
  (:require [clojure.pprint :as p]
            [clojure.string :as s]))

(def re-kw #"(^|#|\.)(\w+)")
(def re-case #"-(\w)")
(def v-map {"#" [:id "-"] "." [:class-name " "]})

(defn- discrim [x]
  (cond (nil? x) :nil
        (string? x) :str
        (map? x) :map
        ((some-fn vector? seq?) x) :seq))

(defn- js-case [kw]
  {:pre (keyword? kw)}
  (-> kw
      (name)
      (s/replace re-case #(let [[_ m] %] (-> m str s/capitalize)))))

(defn- long-zip [bottom & seqs]
  (let [len (apply max (map count seqs))
        longer (map #(take len (concat % (repeat bottom))) seqs)]
    (apply map vector longer)))

(defn- parse-kw [kw]
  {:pre (keyword? kw)}
  (let [key (name kw)
        [[_ _ tag] & groups] (re-seq re-kw key)
        grouped (for [[k v] (group-by second groups)
                      :let [[id sep] (get v-map k)]]
                  [id (s/join sep (map last v))])
        {:keys [id class-name]} (into {} grouped)]
    {:tag tag
     :id id
     :class-name class-name}))

(defn- do-draw [target props]
  (doseq [[key val] props
          :let [js-key (js-case key)]]
    (if (nil? val)
      (js-delete target js-key)
      (aset target js-key val))))

(defmulti parse discrim)
(defmethod parse :nil [_] nil)
(defmethod parse :str [s]
  {:txt s
   :el (delay (. js/document createTextNode s))})
(defmethod parse :seq [[x & xs :as xss]]
  (if (not (keyword? x))
    (->> xss (map parse) (remove empty?))
    (let [kw-props (parse-kw x)
          {:keys [r-props r-children]} (group-by
                                        #(if (map? %) :r-props :r-children)
                                        xs)
          {:keys [tag key style] :as props} (->> r-props (apply merge-with into) (merge kw-props))
          children (->> r-children (map parse) (remove empty?))]
      {:tag tag
       :key key
       :props (dissoc props :tag :key :style)
       :style style
       :children children
       :el (delay (let [el (. js/document createElement tag)
                        el-style (.-style el)]
                    (do-draw el props)
                    (do-draw el-style style)
                    (doseq [child children
                            :let [c @(:el child)]]
                      (.appendChild el c))
                    el))})))

(defn- do-recon [target old-props new-props]
  (doseq [[new-key new-val] new-props
          :let [old-val (new-key old-props)]
          :when (not (= old-val new-val))
          :let [js-key (js-case new-key)]]
    (if (nil? new-val)
      (js-delete target js-key)
      (aset target js-key new-val)))
  (doseq [[old-key] old-props
          :when (not (contains? old-key new-props))
          :let [js-key (js-case old-key)]]
    (js-delete target js-key)))

(declare recon)

(defn- do-re [el {old-el :el :as old-child} {new-el :el :as new-child}]
  (cond
    (nil? old-child) (do (.appendChild el @new-el) new-child)
    (nil? new-child) (do (.remove @old-el) nil)
    :else (recon old-child new-child)))

(defn- do-rec [el old-children new-children]
  (let [zipped (long-zip nil old-children new-children)
        old-index (if (contains? (first new-children) :key)
                    (->> old-children
                         (map (juxt :key identity))
                         (remove (comp nil? first))
                         (into {}))
                    {})]
    (for [[old-child new-child] zipped
          :let [{old-key :key} old-child
                {new-key :key} new-child]]
      (cond
        (= old-key new-key) (do-re el old-child new-child)
        :else (if-let [match (get old-index new-key)]
                (recon match new-child)
                (do-re el old-child new-child))))))

(defn- recon [{old-txt :txt old-tag :tag old-el :el :as old}
              {new-txt :txt new-tag :tag new-el :el :as new}]
  {:pre [(not (nil? old)) (not (nil? new))]}
  (cond
    (or old-txt new-txt) (if (= old-txt new-txt)
                           old
                           (do (.replaceWith @old-el @new-el)
                               new))

    (not (= old-tag new-tag)) (do (.replaceWith @old-el @new-el)
                                  new)
    :else (let [{old-el :el old-props :props old-style :style old-children :children} old
                {new-props :props new-style :style new-children :children} new
                el @old-el
                el-style (.-style el)]
            (do-recon el old-props new-props)
            (do-recon el-style old-style new-style)
            (let [children (->>
                            (do-rec el old-children new-children)
                            (remove nil?)
                            (doall))]
              (assoc new :children children :el old-el)))))

(defn rend [root v-init]
  (let [v-i (parse v-init)
        v-dom (atom v-i)]
    (.appendChild root @(:el @v-dom))
    (fn [v-next]
      (->> v-next parse (swap! v-dom recon)))))

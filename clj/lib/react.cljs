(ns lib.react
  (:require-macros
   [lib.macros :refer [debug!]])
  (:require
   [clojure.set :as set]
   [clojure.string :as s]
   [lib.js :refer [js-delay]]
   [lib.prelude :refer [js-case update!]]))

(def ^:private attr-subst {:class :class-name
                           :for :html-for})

(def ^:private data-prefix "data-")
(def ^:private data-prefix-len (count data-prefix))

(defn- parse-attrs [attrs props]
  {:pre [(map? props)]}
  (loop [stream props
         acc attrs]
    (let [[[key val] & ps] stream]
      (cond (empty? stream) acc
            (= key :style)
            (recur ps
                   (update! acc :style assoc! key val))
            (-> key name (s/starts-with? data-prefix))
            (recur ps
                   (update! acc :dataset
                            assoc! (-> key name (.substring data-prefix-len))
                            val))
            :else
            (recur ps
                   (update! acc :attrs assoc! key val))))))

(defn- parse-props [props]
  {:pre [(seqable? props)]}
  (let [attr? map?
        child? (some-fn seqable? int?)]
    (loop [stream props
           acc (transient {:attrs (transient {})
                           :dataset (transient {})
                           :style (transient {})
                           :children (transient [])})]
      (let [[p & ps] stream]
        (cond (empty? stream) acc
              (nil? p) (recur ps acc)
              (attr? p) (recur ps
                               (parse-attrs acc p))
              (child? p) (recur ps
                                (update! acc :children conj! p))
              :else (assert false))))))

(declare parse-impl)

(defn- parse-children [children]
  {:pre [(seqable? children)]
   :post [(seqable? %)]}
  (let [atomic? (some-fn (complement seqable?) string? (comp keyword? first))
        css (loop [stream children
                   acc []]
              (let [[c & cs] stream]
                (cond
                  (empty? stream) acc
                  (nil? c) (recur cs acc)
                  (atomic? c) (recur cs (conj acc c))
                  :else (recur cs (apply conj acc c)))))]
    (map-indexed parse-impl css)))

(defmulti ^:private parse-impl #(cond (nil? %2) :nil
                                      (string? %2) :str
                                      (int? %2) :int
                                      (seqable? %2) :seq))
(defmethod parse-impl :nil [_ _] nil)
(defmethod parse-impl :str [key s] {:key key
                                    :txt s})
(defmethod parse-impl :int [key i] {:key key
                                    :txt (str i)})
(defmethod parse-impl :seq [key-idx [x & xs :as xss]]
  (if-not (keyword? x)
    (parse-children xss)
    (let [tag-name (name x)
          {:keys [dataset style]
           {:keys [key]
            :or {key key-idx}
            :as raw-attrs} :attrs
           raw-children :children} (parse-props xs)
          attrs (-> raw-attrs (dissoc! :tag-name :key) persistent! (set/rename-keys attr-subst))
          children (->> raw-children persistent! parse-children)
          keys (->> children (map :key) (into #{}))]
      (assert (= (count keys) (count children)))
      {:key key
       :tag-name tag-name
       :attrs attrs
       :dataset (persistent! dataset)
       :style (persistent! style)
       :children children
       :keys keys})))

(defn parse [tree]
  (parse-impl 0 tree))

(defn- set-prop! [target key val]
  (let [js-key (js-case key)
        js-val (clj->js val)]
    (if (nil? val)
      (js-delete target key)
      (aset target js-key js-val))))

(defn- set-props! [target props]
  {:pre [(seqable? props)]}
  (doseq [[key val] props]
    (set-prop! target key val)))

(defn- assoc-dom [depth {:keys [txt tag-name attrs dataset style children]
                         :as tree}]
  {:pre [(int? depth) (map? tree)]}
  (if (nil? tag-name)
    (do
      (debug! (.log js/console (apply str (concat (repeat depth "..") [txt]))))
      (->> txt (. js/document createTextNode)
           (assoc tree :el)))
    (let [el (. js/document createElement tag-name)
          el-data (.-dataset el)
          el-style (.-style el)
          new-children (doall (for [child children
                                    :let [{child-el :el
                                           :as new-child} (assoc-dom (inc depth) child)]]
                                (do (.appendChild el child-el)
                                    new-child)))]
      (set-props! el attrs)
      (set-props! el-data dataset)
      (set-props! el-style style)
      (debug! (.log js/console (apply str (concat (repeat depth "->") [tag-name]))))
      (assoc tree :el el :children new-children))))

(defn- replace-child! [depth old-el new]
  {:pre [(int? depth) (map? new)]}
  (let [{new-el :el
         :as drawn} (assoc-dom depth new)]
    (.replaceWith old-el new-el)
    drawn))

(defn- recon-props! [target old-props new-props]
  {:pre [(map? old-props) (map? new-props)]}
  (doseq [[new-key new-val] new-props
          :let [old-val (get old-props new-key)]
          :when (not= old-val new-val)]
    (set-prop! target new-key new-val))
  (doseq [[old-key] old-props
          :when (not (contains? new-props old-key))]
    (set-prop! target old-key nil)))

(declare reconcile!)

(defn- rec! [depth parent-el old-keys new-keys old-children new-children]
  {:pre [(int? depth) (set? old-keys) (set? new-keys) (seqable? old-children) (seqable? new-children)]
   :post [(vector? %)]}
  (loop [old-c old-children
         new-c new-children
         acc []]
    (let [[{old-el :el
            old-key :key
            :as old-child} & old-rest] old-c
          [{new-key :key
            :as new-child} & new-rest] new-c]
      (cond
        (= nil old-child new-child) acc

        (nil? old-child) (let [{new-el :el
                                :as drawn}
                               (assoc-dom depth new-child)]
                           (.appendChild parent-el new-el)
                           (debug! (println "1<"))
                           (recur old-c new-rest (conj acc drawn)))

        (or (nil? new-child) (not (contains? new-keys old-key))) (do
                                                                   (.remove old-el)
                                                                   (debug! (println "2<"))
                                                                   (recur old-rest new-c acc))

        (not (contains? old-keys new-key)) (let [{new-el :el
                                                  :as drawn}
                                                 (assoc-dom depth new-child)]
                                             (.before old-el new-el)
                                             (debug! (println "3<"))
                                             (recur old-c new-rest (conj acc drawn)))

        :else (do
                (debug! (println "4<"))
                (recur old-rest new-rest (->> new-child (reconcile! depth old-child) (conj acc))))))))

(defn- reconcile! [depth
                   {old-txt :txt
                    old-tag :tag-name
                    old-el :el
                    :as old}
                   {new-txt :txt
                    new-tag :tag-name
                    :as new}]
  {:pre [(map? old) (map? new)]}
  (cond
    (or (nil? old-tag) (nil? new-tag)) (do
                                         (debug! (println "|1|"))
                                         (cond (= old-txt new-txt) old
                                               (some? old-txt) (do (set! old-el -data new-txt)
                                                                   (assoc new :el old-el))
                                               :else (replace-child! depth old-el new)))

    (not= old-tag new-tag) (do
                             (debug! (println "|2|"))
                             (replace-child! depth old-el new))

    :else (let [{old-attrs :attrs
                 old-dataset :dataset
                 old-style :style
                 old-children :children
                 old-keys :keys} old
                {new-attrs :attrs
                 new-dataset :dataset
                 new-style :style
                 new-children :children
                 new-keys :keys} new
                el-data (.-dataset old-el)
                el-style (.-style old-el)]
            (recon-props! old-el old-attrs new-attrs)
            (recon-props! el-data old-dataset new-dataset)
            (recon-props! el-style old-style new-style)
            (let [children (rec! (inc depth) old-el old-keys new-keys old-children new-children)]
              (debug! "|3|")
              (assoc new :children children :el old-el)))))

(def ^:private jdelay (js-delay 64))

(defn rend [root]
  {:pre [(instance? js/HTMLElement root)]}
  (let [v-dom (atom nil)
        recon! (partial reconcile! 0)]
    (fn [v-next]
      {:pre [(seqable? v-next)]}
      (debug! (.group js/console))
      (swap! v-dom (fn [old]
                     (if (nil? old)
                       (let [new-tree (->> v-next parse (assoc-dom 0))]
                         (doseq [child (.-children root)]
                           (.remove child))
                         (.appendChild root (:el new-tree))
                         new-tree)
                       (do
                         (debug! (.time js/console))
                         (jdelay #(do
                                    (let [parsed (parse v-next)]
                                      (swap! v-dom recon! parsed))
                                    (debug! (.timeEnd js/console))))
                         old))))

      (debug! (.groupEnd js/console))
      nil)))

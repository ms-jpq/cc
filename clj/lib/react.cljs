(ns lib.react
  (:require-macros
   [lib.macros :refer [debug!]])
  (:require
   [clojure.set :as set]
   [clojure.string :as s]
   [lib.js :refer [js-delay]]
   [lib.prelude :refer [js-case update-in!]]))

(def ^:private attr-subst {:class :class-name
                           :for :html-for})

(defn- parse-attrs [attrs props]
  {:pre [(map? props)]}
  (loop [stream props
         acc attrs]
    (let [[[key val] & ps] stream]
      (cond (empty? stream) acc
            (= key :style)
            (recur ps
                   (update-in! acc [:style] assoc! key val))
            (-> key name (s/starts-with? "data-"))
            (recur ps
                   (update-in! acc [:dataset]
                               assoc! (-> key name (s/replace-first "data-" ""))
                               val))
            :else
            (recur ps
                   (update-in! acc [:attrs] assoc! key val))))))

(defn- parse-props [props]
  {:pre [(seqable? props)]}
  (let [attr? map?
        child? (some-fn nil? seqable? int?)]
    (loop [stream props
           acc (transient {:attrs (transient {})
                           :dataset (transient {})
                           :style (transient {})
                           :children (transient [])})]
      (let [[p & ps] stream]
        (cond (empty? stream) acc
              (attr? p) (recur ps
                               (parse-attrs acc p))
              (child? p) (recur ps
                                (update-in! acc [:children] conj! p))
              :else (assert false))))))

(defn- parse-children [children]
  {:pre [(seqable? children)]
   :post [(instance? PersistentTreeMap %)]}
  (loop [stream children
         acc (sorted-map)]
    (let [[c & cs] stream]
      (cond
        (empty? stream) acc
        (nil? c) (recur cs acc)
        (instance? PersistentTreeMap c) (recur cs (conj acc c))
        (map? c) (recur cs (assoc acc (:key c) c))
        :else (assert false)))))

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
    (->> xss (map-indexed parse-impl) parse-children)
    (let [tag-name (name x)
          {:keys [dataset style]
           raw-attrs :attrs
           raw-children :children} (parse-props xs)
          key (or (:key raw-attrs) key-idx)
          attrs (-> raw-attrs (dissoc! :tag-name :key) persistent! (set/rename-keys attr-subst))
          children (->> raw-children persistent! (map-indexed parse-impl) parse-children)]
      {:key key
       :tag-name tag-name
       :attrs attrs
       :dataset (persistent! dataset)
       :style (persistent! style)
       :children children})))

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
          new-children (into (sorted-map) (for [[key child] children
                                                :let [{e :el
                                                       :as c} (assoc-dom (inc depth) child)]]
                                            (do (.appendChild el e)
                                                [key c])))]
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

(defn- rec! [depth parent-el old-children new-children]
  {:pre [(int? depth) (instance? PersistentTreeMap old-children) (instance? PersistentTreeMap new-children)]
   :post [(instance? PersistentTreeMap %)]}
  (loop [old-c (->> old-children reverse (into []))
         new-c (->> new-children reverse (into []))
         acc (sorted-map)]
    (let [[[old-key {old-el :el
                     :as old-child}] & old-rest] old-c
          [[new-key new-child] & new-rest] new-c
          assoc-child (comp (partial apply assoc acc) (juxt :key identity))]
      (cond
        (= nil old-child new-child) acc

        (nil? old-child) (let [{new-el :el
                                :as drawn}
                               (assoc-dom depth new-child)]
                           (.appendChild parent-el new-el)
                           (recur old-c new-rest (assoc-child drawn)))

        (or (nil? new-child) (not (contains? new-children old-key))) (do
                                                                       (.remove old-el)
                                                                       (recur old-rest new-c acc))

        (not (contains? old-children new-key)) (let [{new-el :el
                                                      :as drawn}
                                                     (assoc-dom depth new-child)]
                                                 (.before old-el new-el)
                                                 (recur old-c new-rest (assoc-child drawn)))

        :else (recur old-rest new-rest (->> new-child (reconcile! depth old-child) assoc-child))))))

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
    (or (nil? old-tag) (nil? new-tag)) (cond (= old-txt new-txt) old
                                             (some? old-txt) (do (set! old-el -data new-txt)
                                                                 (assoc new :el old-el))
                                             :else (replace-child! depth old-el new))

    (not= old-tag new-tag) (replace-child! depth old-el new)

    :else (let [{old-attrs :attrs
                 old-dataset :dataset
                 old-style :style
                 old-children :children} old
                {new-attrs :attrs
                 new-dataset :dataset
                 new-style :style
                 new-children :children} new
                el-data (.-dataset old-el)
                el-style (.-style old-el)]
            (recon-props! old-el old-attrs new-attrs)
            (recon-props! el-data old-dataset new-dataset)
            (recon-props! el-style old-style new-style)
            (let [children (rec! (inc depth) old-el old-children new-children)]
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

(ns lib.react
  (:require-macros
   [lib.macros :refer [debug!]])
  (:require
   [clojure.set :as set]
   [clojure.string :as s]
   [clojure.walk :as w]
   [lib.prelude :refer [js-case]]))

(def ^:private re-kw #"(^|#|\.)([^#.]+)")
(def ^:private v-map {"#" [:id "-"]
                      "." [:class-name " "]})

(def ^:private attr-subst {:class :class-name
                           :for :html-for})

(defn parse-kw [kw]
  {:pre [(keyword? kw)]
   :post [(->> % :tag-name string?)]}
  (let [key (name kw)
        [[_ _ tag] & groups] (re-seq re-kw key)
        grouped (for [[k v] (group-by second groups)
                      :let [[id sep] (get v-map k)]]
                  [id (s/join sep (map last v))])
        {:keys [id class-name]} (into {} grouped)]
    {:tag-name (js-case tag)
     :id id
     :class-name class-name}))

(defn- parse-children [children]
  {:pre [(seqable? children)]
   :post [(seqable? %)]}
  (loop [acc []
         [c & cs] children]
    (let [nul? (nil? c)]
      (cond
        (and nul? (empty? cs)) acc
        nul? (recur acc cs)
        (map? c) (recur (conj acc c) cs)
        :else (recur acc (concat c cs))))))

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
    (let [{:keys [tag-name id class-name]} (parse-kw x)
          {:keys [raw-props raw-children]
           :or {raw-children []}} (group-by
                                   #(if (map? %) :raw-props :raw-children)
                                   xs)
          props (as-> raw-props $
                  (apply merge-with into $)
                  (set/rename-keys $ attr-subst))
          {:keys [key style]
           :or {key key-idx
                style {}}
           :as raw-attrs} (cond-> props
                            id (update-in
                                [:id]
                                #(if % (str id "-" %) id))
                            class-name (update-in
                                        [:class-name]
                                        #(if % (str class-name " " %) class-name))
                            true (dissoc :tag-name :key :style))
          {:keys [attrs data]
           :or {attrs []
                data []}} (group-by
                           #(if (-> % first name (s/starts-with? "data-")) :data :attrs)
                           raw-attrs)
          dataset (w/walk
                   #(vector (-> % first name (s/replace-first "data-" "")) (second %))
                   #(into {} %)
                   data)
          children (->> raw-children (map-indexed parse-impl) parse-children)]
      {:key key
       :tag-name tag-name
       :attrs (into {} attrs)
       :dataset dataset
       :style style
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
      (debug! .log js/console (apply str (concat (repeat depth "..") [txt])))
      (->> txt (. js/document createTextNode)
           (assoc tree :el)))
    (let [el (. js/document createElement tag-name)
          el-data (.-dataset el)
          el-style (.-style el)
          new-children (doall (for [child children
                                    :let [{e :el
                                           :as c} (assoc-dom (+ depth 1) child)]]
                                (do (.appendChild el e) c)))]
      (set-props! el attrs)
      (set-props! el-data dataset)
      (set-props! el-style style)
      (debug! .log js/console (apply str (concat (repeat depth "->") [tag-name])))
      (assoc tree :el el :children new-children))))

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
  {:pre [(int? depth) (seqable? old-children) (seqable? new-children)]}
  (let [old-index (->> old-children
                       (map :key)
                       (into #{}))
        new-index (->> new-children
                       (map :key)
                       (into #{}))]
    (loop [old-c (->> old-children (reverse) (into []))
           new-c (->> new-children (reverse) (into []))
           acc []]
      (let [{old-el :el
             old-key :key
             :as old-child} (peek old-c)
            {new-key :key
             :as new-child} (peek new-c)]
        (cond
          (= nil old-child new-child) acc

          (nil? old-child) (let [{new-el :el
                                  :as drawn}
                                 (assoc-dom depth new-child)]
                             (.appendChild parent-el new-el)
                             (recur old-c (pop new-c) (conj acc drawn)))

          (or (nil? new-child) (not (contains? new-index old-key))) (do
                                                                      (.remove old-el)
                                                                      (recur (pop old-c) new-c acc))

          (not (contains? old-index new-key)) (let [{new-el :el
                                                     :as drawn}
                                                    (assoc-dom depth new-child)]
                                                (.before old-el new-el)
                                                (recur old-c (pop new-c) (conj acc drawn)))

          :else (recur (pop old-c) (pop new-c) (->> new-child (reconcile! depth old-child) (conj acc))))))))

(defn- replace-child! [depth old-el new]
  {:pre [(int? depth) (map? new)]}
  (let [{new-el :el
         :as drawn} (assoc-dom depth new)]
    (.replaceWith old-el new-el)
    drawn))

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
            (let [children (->>
                            (rec! (+ depth 1) old-el old-children new-children)
                            (remove nil?)
                            doall)]
              (assoc new :children children :el old-el)))))

(defn rend [root]
  {:pre [(instance? js/HTMLElement root)]}
  (let [v-dom (atom nil)]
    (fn [v-next]
      {:pre [(seqable? v-next)]}
      (debug! .group js/console "rend")
      (->> v-next
           parse
           (swap! v-dom #(if (nil? %1)
                           (let [tree (assoc-dom 0 %2)]
                             (doseq [child (.-children root)]
                               (.remove child))
                             (.appendChild root (:el tree))
                             tree)
                           (reconcile! 0 %1 %2))))
      (debug! .groupEnd js/console))))

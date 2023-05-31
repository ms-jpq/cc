(ns lib.react
  (:require-macros
   [lib.macros :refer [debug!]])
  (:require
   [clojure.string :as s]
   [lib.prelude :refer [js-case]]))

(def ^:private re-kw #"(^|#|\.)(\w+)")
(def ^:private v-map {"#" [:id "-"]
                      "." [:class-name " "]})

(defn- parse-kw [kw]
  {:pre [(keyword? kw)]}
  (let [key (name kw)
        [[_ _ tag] & groups] (re-seq re-kw key)
        grouped (for [[k v] (group-by second groups)
                      :let [[id sep] (get v-map k)]]
                  [id (s/join sep (map last v))])
        {:keys [id class-name]} (into {} grouped)]
    {:tag tag
     :id id
     :class-name class-name}))

(defmulti ^:private parse #(cond (nil? %) :nil
                                 (string? %) :str
                                 ((some-fn vector? seq?) %) :seq))
(defmethod parse :nil [_] nil)
(defmethod parse :str [s] {:txt s})
(defmethod parse :seq [[x & xs :as xss]]
  (if-not (keyword? x)
    (->> xss (map parse) (remove empty?))
    (let [kw-props (parse-kw x)
          {:keys [r-props r-children]} (group-by
                                        #(if (map? %) :r-props :r-children)
                                        xs)
          {:keys [tag key style]
           :as props} (->> r-props (apply merge-with into) (merge kw-props))
          children (->> r-children (map parse) (remove empty?))]
      {:tag tag
       :key key
       :props (dissoc props :tag :key :style)
       :style style
       :children children})))

(defn- set-props! [target props]
  (doseq [[key val] props
          :let [js-key (js-case key)]]
    (if (nil? val)
      (js-delete target js-key)
      (aset target js-key val))))

(defn- assoc-dom [depth {:keys [txt tag props style children]
                         :as tree}]
  {:pre [(int? depth) (not (nil? tree))]}
  (if-not (nil? txt)
    (do
      (debug! .trace js/console (apply str (concat (repeat depth "..") [txt])))
      (->> txt (. js/document createTextNode)
           (assoc tree :el)))
    (let [el (. js/document createElement tag)
          el-style (.-style el)
          c-iter (for [child children
                       :let [{e :el
                              :as c} (assoc-dom (+ depth 1) child)]]
                   (do (.appendChild el e) c))
          new-children (doall c-iter)]
      (set-props! el props)
      (set-props! el-style style)
      (debug! .trace js/console (apply str (concat (repeat depth "->") [tag])))
      (assoc tree :el el :children new-children))))

(defn- recon-props! [target old-props new-props]
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

(declare reconcile!)

(defn- rec! [depth parent-el old-children new-children]
  (loop [pos 0
         old-c (->> old-children (reverse) (into []))
         new-c (->> new-children (reverse) (into []))
         old-index (->> old-children
                        (map-indexed #(vector (:key %2) %1))
                        (remove (comp nil? first))
                        (into {}))
         acc []]
    (let [{old-el :el
           old-key :key
           :as old-child} (peek old-c)
          {new-key :key
           :as new-child} (peek new-c)
          match (old-index new-key)]
      (if (and old-child new-child)
        (cond
          (nil? old-child) (let [{new-el :el
                                  :as drawn}
                                 (assoc-dom depth new-child)]
                             (.appendChild parent-el new-el)
                             (recur nil old-c (pop new-c)
                                    old-index (conj acc drawn)))

          (nil? new-child) (do (.remove old-el)
                               (recur nil (pop old-c) new-c
                                      old-index acc))

          (nil? new-key) (recur (+ 1 pos) (pop old-c) (pop new-c)
                                (dissoc old-index old-key) (->> new-child (reconcile! depth old-child) (conj acc)))

          (nil? match) (let [{new-el :el
                              :as drawn}
                             (assoc-dom depth new-child)]
                         (.before old-el new-el)
                         (recur pos old-c (pop new-c)
                                old-index (conj acc drawn)))

          (= match pos) (recur (+ 1 pos) (pop old-c) (pop new-c)
                               (dissoc old-index new-key) (->> new-child (reconcile! depth old-child) (conj acc)))

          (> match pos) (do (.remove old-el)
                            (recur pos (pop old-c) new-c
                                   (dissoc old-index new-key) acc))

          :else (assert false))

        acc))))

(defn- replace-child! [depth old-el new]
  (let [{new-el :el
         :as drawn} (assoc-dom depth new)]
    (.replaceWith old-el new-el)
    drawn))

(defn- reconcile! [depth
                   {old-txt :txt
                    old-tag :tag
                    old-el :el
                    :as old}
                   {new-txt :txt
                    new-tag :tag
                    :as new}]
  {:pre [(not (nil? old)) (not (nil? new))]}
  (cond
    (or old-txt new-txt) (if (= old-txt new-txt)
                           old
                           (replace-child! depth old-el new))

    (not (= old-tag new-tag)) (replace-child! depth old-el new)

    :else (let [{old-props :props
                 old-style :style
                 old-children :children} old
                {new-props :props
                 new-style :style
                 new-children :children} new
                el-style (.-style old-el)]
            (recon-props! old-el old-props new-props)
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

(ns lib.react
  (:require [lib.prelude :refer [long-zip]]
            [clojure.pprint :as p]
            [clojure.string :as s]))

(def ^:private re-kw #"(^|#|\.)(\w+)")
(def ^:private re-case #"-(\w)")
(def ^:private v-map {"#" [:id "-"] "." [:class-name " "]})

(defn- discrim [x]
  (cond (nil? x) :nil
        (string? x) :str
        (map? x) :map
        ((some-fn vector? seq?) x) :seq))

(defn- js-case [kw]
  {:pre [(keyword? kw)]}
  (-> kw
      (name)
      (s/replace re-case #(let [[_ m] %] (-> m str s/capitalize)))))

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

(defmulti ^:private parse discrim)
(defmethod parse :nil [_] nil)
(defmethod parse :str [s] {:txt s})
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
       :children children})))

(defn- do-props [target props]
  (doseq [[key val] props
          :let [js-key (js-case key)]]
    (if (nil? val)
      (js-delete target js-key)
      (aset target js-key val))))

(defn- do-dom [{:keys [txt tag props style children] :as tree}]
  {:pre [(not (nil? tree))]}
  (if (not (nil? txt))
    (->> txt (. js/document createTextNode)
         (assoc tree :el))
    (let [el (. js/document createElement tag)
          el-style (.-style el)
          c-iter (for [child children
                       :let [{e :el :as c} (do-dom child)]]
                   (do (.appendChild el e) c))
          new-children (doall c-iter)]
      (do-props el props)
      (do-props el-style style)
      (assoc tree :el el :children new-children))))

(defn- do-recon-props [target old-props new-props]
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

(declare do-reconcile)

(defn- do-recon-children [parent-el {old-el :el :as old-child}  new-child]
  (cond
    (nil? old-child) (let [{new-el :el :as drawn}
                           (do-dom new-child)]
                       (.appendChild parent-el new-el)
                       drawn)
    (nil? new-child) (do (.remove old-el) nil)
    :else (do-reconcile old-child new-child)))

(defn- do-rec [el old-children new-children]
  (let [zipped (long-zip nil old-children new-children)
        old-index (delay (->> old-children
                              (map (juxt :key identity))
                              (remove (comp nil? first))
                              (into {})))]
    (for [[{old-key :key :as old-child} {new-key :key :as new-child}] zipped]
      (cond
        (= old-key new-key) (do-recon-children el old-child new-child)
        :else (if-let [match (get @old-index new-key)]
                (do-reconcile match new-child)
                (do-recon-children el old-child new-child))))))

(defn- do-reconcile [{old-txt :txt old-tag :tag old-el :el :as old}
                     {new-txt :txt new-tag :tag :as new}]
  {:pre [(not (nil? old)) (not (nil? new))]}
  (cond
    (or old-txt new-txt) (if (= old-txt new-txt)
                           old
                           (let [{new-el :el :as drawn} (do-dom new)]
                             (.replaceWith old-el new-el)
                             drawn))

    (not (= old-tag new-tag)) (let [{new-el :el :as drawn} (do-dom new)]
                                (.replaceWith old-el new-el)
                                drawn)

    :else (let [{old-props :props old-style :style old-children :children} old
                {new-props :props new-style :style new-children :children} new
                el-style (.-style old-el)]
            (do-recon-props old-el old-props new-props)
            (do-recon-props el-style old-style new-style)
            (let [children (->>
                            (do-rec old-el old-children new-children)
                            (remove nil?)
                            doall)]
              (assoc new :children children :el old-el)))))

(defn rend [root v-init]
  {:pre [(-> root type (isa? js/HTMLElement))]}
  (let [v-i (->> v-init parse do-dom)
        v-dom (atom v-i)]
    (.appendChild root (:el @v-dom))
    (fn [v-next]
      (->> v-next parse (swap! v-dom do-reconcile)))))

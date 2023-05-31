(ns lib.react
  (:require-macros
   [lib.macros :refer [debug!]])
  (:require
   [clojure.string :as s]
   [lib.prelude :refer [js-case]]))

(def ^:private re-kw #"(^|#|\.)((?:\w|-)+)")
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

(defmulti ^:private parse #(cond (nil? %2) :nil
                                 (string? %2) :str
                                 (seqable? %2) :seq))
(defmethod parse :nil [_ _] nil)
(defmethod parse :str [key s] {:key key
                               :txt s})
(defmethod parse :seq [idx [x & xs :as xss]]
  (if-not (keyword? x)
    (->> xss (map parse) (remove empty?))
    (let [kw-props (parse-kw x)
          {:keys [r-props r-children]} (group-by
                                        #(if (map? %) :r-props :r-children)
                                        xs)
          {:keys [tag key style]
           :or {key idx}
           :as props} (->> r-props (apply merge-with into) (merge kw-props))
          children (->> r-children (map-indexed parse) (remove empty?))]
      {:key key
       :tag tag
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
      (debug! .log js/console (apply str (concat (repeat depth "..") [txt])))
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
      (debug! .log js/console (apply str (concat (repeat depth "->") [tag])))
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
          (and (nil? old-child) (nil? new-child)) acc

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
    (or (not (nil? old-txt)) (not (nil? new-txt))) (cond (= old-txt new-txt) old
                                                         (not (nil? old-txt)) (do (set! old-el -data new-txt)
                                                                                  (assoc new :el old-el))
                                                         :else (replace-child! depth old-el new))

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
      {:pre [(seqable? v-next)]}
      (debug! .group js/console "rend")
      (->> v-next
           (parse 0)
           (swap! v-dom #(if (nil? %1)
                           (let [tree (assoc-dom 0 %2)]
                             (doseq [child (.-children root)]
                               (.remove child))
                             (.appendChild root (:el tree))
                             tree)
                           (reconcile! 0 %1 %2))))
      (debug! .groupEnd js/console))))

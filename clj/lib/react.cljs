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
        (or (seq? x) (vector? x)) :seq))

(defn- js-case [kw]
  {:pre [(keyword? kw)]}
  (-> kw
      (name)
      (s/replace re-case #(let [[_ m] %] (-> m str s/capitalize)))))

(defn- long-zip [bottom & seqs]
  (let [len (apply max (map count seqs))
        longer (map #(take len (concat % (repeat bottom))) seqs)]
    (apply map vector (range) longer)))

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

(defmulti parse discrim)
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
          children (->> r-children (map parse) (remove empty?) (into []))]
      {:tag tag
       :key key
       :props (dissoc props :tag :key :style)
       :style style
       :children children})))

(defn- do-draw [target props]
  (doseq [[key val] props
          :let [js-key (js-case key)]]
    (if (nil? val)
      (js-delete target js-key)
      (aset target js-key val))))

(defmulti draw discrim)
(defmethod draw :seq [s] (map draw s))
(defmethod draw :map [{:keys [txt tag props style children]}]
  (if txt
    (. js/document createTextNode txt)
    (let [el (. js/document createElement tag)
          el-style (.-style el)]
      (do-draw el props)
      (do-draw el-style style)
      (doseq [child children
              :let [c (draw child)]]
        (. el appendChild c))
      el)))

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

(defn- recon [{old-txt :txt old-tag :tag :as old}
              {new-txt :txt new-tag :tag :as new}]
  {:pre [(not (nil? old)) (not (nil? new))]}
  (cond
    (or old-txt new-txt) (if (= old-txt new-txt) old (assoc new :el (draw new)))

    (not (= old-tag new-tag)) (assoc new :el (draw new))

    :else (let [{:keys [el] old-props :props old-style :style old-children :children} old
                {new-props :props new-style :style new-children :children} new
                el-style (.-style el)]
            (do-recon el old-props new-props)
            (do-recon el-style old-style new-style)
            (let [zipped (long-zip nil old-children new-children)
                  keyed (if (contains? (first new-children) :key)
                          (->> old-children
                               (map-indexed #(vector (:key %2) [%1 %2]))
                               (remove (comp nil? first))
                               (into {}))
                          {})
                  c-iter (for [[idx old-child new-child] zipped
                               :let [{old-key :key} old-child
                                     {new-key :key} new-child]]
                           (cond
                             (= old-key new-key) (cond
                                                   (nil? old-child) (let [new-el (draw new-child)]
                                                                      (assoc new-child :el new-el)
                                                                      (.appendChild el new-el))
                                                   (nil? new-child) (do
                                                                      (.remove (:el old-child))
                                                                      nil)
                                                   :else (recon old-child new-child))
                             :else (assert false)))
                  children (doall (remove nil? c-iter))]
              (assoc new :el el :children children)))))

(defn rend [v-init]
  {:pre [(map? v-init)]}
  (let [v-dom (atom (assoc v-init :el (draw v-init)))]
    (fn [v-next]
      (->> v-next
           (swap! v-dom recon)
           (:el)))))

(def spec [:a#asd.c#zxc.d
           {:key 2}
           {:onclick #(. js/console log %)}
           "abc"
           [[] []]
           [nil]
           nil nil
           "wwwwwwwwww"
           {:style {:background-color "green"}} {:style {:color "pink"}}
           {:style {:color nil}}
           [:h1 "big-txt" {:key 9}]
           [:h3 "somt-txt"]
           {:data-stdin "somthing funny"}])

(. js/console clear)
(def p (parse spec))
(def r (rend p))
(def it (r p))

(. js/console log "---------------------")
(p/pprint p)
(. js/console log it)

(p/pprint (if "" 1 2))

; (-> js/document (.  -body) (. appendChild it))

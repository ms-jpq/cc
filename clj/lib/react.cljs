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
(defmethod parse :str [s] s)
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

(defn- do-draw [target props]
  (doseq [[key val] props]
    (let [js-key (js-case key)]
      (if (nil? val)
        (js-delete target js-key)
        (aset target js-key val)))))

(defmulti draw discrim)
(defmethod draw :seq [s] (map draw s))
(defmethod draw :str [s] (. js/document createTextNode s))
(defmethod draw :map [{:keys [tag props style children]}]
  (let [el (. js/document createElement tag)
        el-style (.-style el)]
    (do-draw el props)
    (do-draw el-style style)
    (doseq [child children]
      (let [c (draw child)]
        (. el appendChild c)))
    el))

(defn- do-recon [target old-props new-props]
  (doseq [[new-key new-val] new-props]
    (let [old-val (new-key old-props)]
      (when (not (= old-val new-val))
        (let [js-key (js-case new-key)]
          (if (nil? new-val)
            (js-delete target js-key)
            (aset target js-key new-val))))))
  (doseq [[old-key] old-props]
    (when (not (contains? old-key new-props))
      (let [js-key (js-case old-key)]
        (js-delete target js-key)))))

(defn- do-rec [old-children new-children]

  new-children)

(defn- recon [{old-tag :tag :as old} {new-tag :tag :as new}]
  (if (not (= old-tag new-tag))
    (let [el (draw new)]
      (assoc new :el el))
    (let [{:keys [el] old-props :props old-style :style old-children :children} old
          {new-props :props new-style :style new-children :children} new
          el-style (.-style el)]
      (do-recon el old-props new-props)
      (do-recon el-style old-style new-style)
      (assoc new :el el))))

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
           [:h1 "h1"]
           [:h3 "H3"]
           {:data-stdin "somthing funny"}])

(. js/console clear)
(def p (parse spec))
(def r (rend p))
(def it (r p))

(p/pprint p)
(. js/console log it)

; (-> js/document (.  -body) (. appendChild it))

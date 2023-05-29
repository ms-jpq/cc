(ns lib.react
  (:require [clojure.pprint :as p]
            [clojure.string :as s]))

(def re-kw #"(?:(?<=^:)|(#|\.))(\w+)")

(def v-map {"#" [:id "-"] "." [:class-name " "]})

(defn- parse-kw [kw]
  {:pre (keyword? kw)}
  (let [key (str kw)
        [[_ _ tag-name] & groups] (re-seq re-kw key)
        grouped (for [[k v] (group-by second groups)
                      :let [[id sep] (get v-map k)]]
                  [id (s/join sep (map last v))])
        {:keys [id class-name]} (into {} grouped)]
    {:tag-name tag-name
     :id id
     :class-name class-name}))

(defn- discrim [x]
  (cond (nil? x) :nil
        (string? x) :str
        (map? x) :map
        (or (seq? x) (vector? x)) :seq))

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
          {:keys [tag-name key style] :as props} (->> r-props (apply merge-with into) (merge kw-props))
          children  (->> r-children (map parse) (remove empty?))]
      {:tag-name tag-name
       :key key
       :props (dissoc props :tag-name :key :style)
       :style style
       :children children})))

(def re-case #"-(\w)")

(defn js-case [key]
  (-> key
      (str)
      (subs 1)
      (s/replace re-case #(let [[_ m] %] (-> m str s/capitalize)))))

(defmulti render discrim)
(defmethod render :nil [_] nil)
(defmethod render :seq [s] (->> s (map render) (remove empty?)))
(defmethod render :str [s] (. js/document createTextNode s))
(defmethod render :map [{:keys [tag-name key props style children]}]
  (let [el (. js/document createElement tag-name)
        el-style (.-style el)]
    (doseq [[kk v] props]
      (let [k (js-case kk)]
        (if (nil? v)
          (js-delete el k)
          (aset el k v))))
    (doseq [[kk v] style]
      (let [k (js-case kk)]
        (if (nil? v)
          (js-delete el-style k)
          (aset el-style k v))))
    (doseq [child children]
      (. el appendChild (render child)))
    el))

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

; (. js/console clear)
(def it (render (parse spec)))
(. js/console log it)

(-> js/document (.  -body) (. appendChild it))

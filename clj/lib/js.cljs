(ns lib.js)

(defn js-delay [timeout]
  {:pre [(int? timeout)]}
  (let [atom (atom nil)
        ttl (clj->js {:timeout timeout})]
    (fn [f]
      {:pre [(fn? f)]}
      (swap! atom (fn [handle]
                    (.cancelIdleCallback js/globalThis handle)
                    (.requestIdleCallback js/globalThis
                                          f
                                          ttl))))))

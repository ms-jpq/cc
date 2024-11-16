(ns build
  (:require
   [clojure.tools.build.api :as b]))

(def ^:private class-dir "out/classes")
(def ^:private uber-file "out/compiled.jar")
(def ^:private basis (delay (b/create-basis {:project "deps.edn"})))

(defn uber [_]
  (doseq [p [uber-file class-dir]]
    (b/delete {:path p}))
  (b/copy-dir {:src-dirs ["clj", "out/web"]
               :target-dir class-dir})
  (b/compile-clj {:basis @basis
                  :ns-compile '[srv.main]
                  :class-dir class-dir})
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis @basis
           :main 'srv.main}))

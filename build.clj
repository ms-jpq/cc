(ns build
  (:require [clojure.tools.build.api :as b]))

(def ^:private class-dir "out/classes")
(def ^:private lib 'cc/srv)
(def ^:private version (b/git-count-revs nil))
(def ^:private jar-file (->> lib name (format "out/%s.jar")))

(def ^:private basis (delay (b/create-basis {:project "deps.edn"})))

(defn jar [_]
  (b/write-pom {:class-dir class-dir
                :lib lib
                :version version
                :basis @basis
                :src-dirs ["clj"]})
  (b/copy-dir {:src-dirs ["clj"]
               :target-dir class-dir})
  (b/jar {:class-dir class-dir
          :jar-file jar-file}))

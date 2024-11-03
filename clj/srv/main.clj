(ns srv.main
  (:require
   [clojure.data.json :refer [read-str]]
   [clojure.tools.cli :refer [parse-opts]]
   [clojure.tools.logging :as log]
   [srv.server :refer [run]]))

(defn- handler [request]
  {:pre [(map? request)]}
  (println request)
  {:status 200})

(defn -main [& args]
  {:pre [(seq? args)]}
  (let [argp [["-h" "--help"]
              ["-r" "--root ROOT"
               :default (System/getProperty "user.dir")]
              ["-p" "--port PORT"
               :default 8080
               :parse-fn #(Integer/parseInt %)
               :validate [#(< 0 % 0x10000)]]]
        {{:keys [help port root]} :options
         summary :summary
         errors :errors} (parse-opts args argp)]
    (cond help (println summary)
          errors ((doseq [e errors] (log/error e))
                  (System/exit 2))
          :else (run port handler))))

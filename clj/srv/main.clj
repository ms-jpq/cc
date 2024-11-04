(ns srv.main
  (:require
   [clojure.data.json :as json]
   [clojure.pprint :as pp]
   [clojure.string :as str]
   [clojure.tools.cli :refer [parse-opts]]
   [clojure.tools.logging :as log]
   [srv.server :refer [run]]))

(defn- make-handler [prefix root data]
  {:pre [(string? prefix) (string? root) (string? data)]}
  (fn [{:keys [path]
        :as request}]
    (let [sections (str/split path #"/+")]
      (pp/pprint request)
      {})))

(defn -main [& args]
  {:pre [(seq? args)]}
  (let [argp [["-h" "--help"]
              ["-r" "--root ROOT"
               :default (System/getProperty "user.dir")]
              ["-d" "--data DATA"]
              ["-p" "--port PORT"
               :default 8080
               :parse-fn #(Integer/parseInt %)
               :validate [#(< 0 % 0x10000)]]
              ["--prefix PREFIX"
               :default "/"]]
        {{:keys [help port prefix root data]} :options
         summary :summary
         errors :errors} (parse-opts args argp)
        handler (make-handler prefix root data)]
    (cond help (println summary)
          errors ((doseq [e errors] (log/error e))
                  (System/exit 2))
          :else (run port handler))))

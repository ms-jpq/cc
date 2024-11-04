(ns srv.main
  (:require
   [clojure.data.json :as json]
   [clojure.pprint :as pp]
   [clojure.string :as str]
   [clojure.tools.cli :refer [parse-opts]]
   [clojure.tools.logging :as log]
   [srv.server :refer [run]]))

(def ^:private prefix "/")

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
              ["-d" "--data DATA"
               :default-fn :root]
              ["-p" "--port PORT"
               :default 8080
               :parse-fn #(Integer/parseInt %)
               :validate [#(< 0 % 0x10000)]]
              [nil "--prefix PREFIX"
               :default prefix
               :validate [#(str/starts-with? % prefix)]]]

        {{:keys [help port prefix root data]} :options
         summary :summary
         errors :errors} (parse-opts args argp)
        handler (make-handler prefix root data)]
    (cond help (log/info summary)
          errors ((doseq [e errors] (log/error e))
                  (System/exit 2))
          :else (run port handler))))

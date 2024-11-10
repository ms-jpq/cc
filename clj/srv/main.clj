(ns srv.main
  (:require
   [clojure.string :as str]
   [clojure.tools.cli :as cli]
   [clojure.tools.logging :as log]
   [lib.prelude :as lib]
   [lib.server :as srv]
   [srv.index :as idx]))

(defn- make-handler [prefix root data]
  {:pre [(string? prefix) (string? root) (string? data)]}
  (fn [{:keys [path]
        :as request}]
    (if-not (str/starts-with? path prefix)
      {:status 404
       :body "Prefix Mismatch"}
      (let [path (lib/remove-prefix path prefix)
            request (assoc request :path path)]
        (cond
          (= path idx/path-glob) (idx/handler-glob root data request)
          :else (idx/handler-static root data request))))))

(defn -main [& args]
  {:pre [(seqable? args)]}
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
               :default lib/path-sep
               :validate [#(str/starts-with? % lib/path-sep)
                          #(str/ends-with? % lib/path-sep)]]]

        {{:keys [help port prefix root data]} :options
         summary :summary
         errors :errors} (cli/parse-opts args argp)
        handler (make-handler prefix root data)]
    (cond help (log/info summary)
          errors ((doseq [e errors] (log/error e))
                  (System/exit 2))
          :else (srv/run port handler))))

(ns srv.main
  (:require
   [clojure.string :as str]
   [clojure.tools.cli :as cli]
   [clojure.tools.logging :as log]
   [lib.interop :as ip]
   [lib.prelude :as lib]
   [lib.server :as srv]
   [srv.fs :as fs]
   [srv.glob :as glob]
   [srv.index :as idx]
   [srv.view :as view])
  (:gen-class))

(def ^:private path-sep "/")

(defn- make-handler [prefix root data-dir]
  {:pre [(string? prefix) (ip/path? root) (ip/path? data-dir)]}
  (fn [{:keys [path]
        :as request}]
    (if-not (str/starts-with? path prefix)
      {:status 404
       :body "Prefix Mismatch"}
      (let [path (lib/remove-prefix path prefix)
            request (assoc request :path path)]
        (cond
          (= path glob/path) (glob/handler root data-dir request)
          (or (str/ends-with? path path-sep) (= path "")) (idx/handler prefix root data-dir request)
          :else (view/handler root data-dir request))))))

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
               :default path-sep
               :validate [#(str/starts-with? % path-sep)
                          #(str/ends-with? % path-sep)]]]

        {{:keys [help port prefix root data]} :options
         summary :summary
         errors :errors} (cli/parse-opts args argp)
        handler (make-handler prefix (-> root ip/->path fs/canonicalize) (ip/->path data))]
    (cond help (log/info summary)
          errors ((doseq [e errors] (log/error e))
                  (System/exit 2))
          :else (srv/run port handler))))

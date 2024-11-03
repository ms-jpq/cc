(ns srv.main
  (:require
   [clojure.data.json :refer [read-str]]
   [clojure.tools.cli :refer [parse-opts]]
   [clojure.tools.logging :as log])
  (:import
   [com.sun.net.httpserver HttpExchange HttpHandler HttpServer]
   [java.net InetSocketAddress]
   [java.nio.file Files]))

(defn make-handler [handle-f]
  {:pre [(fn? handle-f)]}
  (reify HttpHandler
    (handle [_ exchange]
      {:pre [(instance? HttpExchange exchange)]}
      (try
        (let [response (handle-f exchange)
              response-bytes (.getBytes response)
              response-length (alength response-bytes)]
          (.sendResponseHeaders exchange 200 response-length)
          (with-open [os (.getResponseBody exchange)]
            (.write os response-bytes)
            (.flush os)))
        (catch Exception e
          (println "Error handling request:" (.getMessage e)))
        (finally
          (.close exchange))))))

(defn router [exchange]
  {:pre [(instance? HttpExchange exchange)]}
  (let [uri (str (.getRequestURI exchange))
        method (.. exchange getRequestMethod toUpperCase)]
    (case [method uri]
      ["GET" "/"] "Welcome to the homepage!"
      ["GET" "/about"] "This is the about page"
      ["POST" "/api"] "API endpoint"
      "404 Not Found")))

(defn run-server [port]
  (let [addr (InetSocketAddress. port)
        server (HttpServer/create addr 0)]
    (-> server
        (.createContext "/" (make-handler router))
        (.setExecutor nil)
        (.start))
    server))

(defn -main [& args]
  {:pre [(seq? args)]}
  (let [argp [["-h" "--help"]
              ["-r" "--root ROOT"
               :default (System/getProperty "user.dir")]
              ["-p" "--port PORT"
               :default 80
               :parse-fn #(Integer/parseInt %)
               :validate [#(< 0 % 0x10000)]]]
        {{:keys [help port root]} :options
         summary :summary
         errors :errors} (parse-opts args argp)]
    (cond help (println summary)
          errors ((doseq [e errors] (log/error e))
                  (System/exit 2))
          :else (println {:port port
                          :root root}))))

(ns srv.server
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log])
  (:import
   [com.sun.net.httpserver HttpExchange HttpHandler HttpServer]
   [java.net InetSocketAddress URLDecoder]
   [java.nio.charset StandardCharsets]
   [java.util.concurrent Executors]))

(def ^:private utf-8 StandardCharsets/UTF_8)

(defn- parse-addr [addr]
  {:pre [(instance? InetSocketAddress addr)]}
  {:ip (.getHostString addr)
   :port (.getPort addr)})

(defn- uri-decode [txt]
  {:pre [(string? txt)]}
  (try
    (URLDecoder/decode txt utf-8)
    (catch Exception _ txt)))

(defn- parse-query [query-string]
  {:pre [((some-fn string? nil?) query-string)]}
  (->> (str/split query-string #"&")
       (remove str/blank?)
       (map #(let [[k v] (map uri-decode (str/split % #"=" 2))]
               [(keyword k) v]))
       (into {})))

(defn- parse-exchange [exchange]
  {:pre [(instance? HttpExchange exchange)]}
  (let [uri (.getRequestURI exchange)]
    {:local-addr (-> exchange .getLocalAddress parse-addr)
     :remote-addr (-> exchange .getRemoteAddress parse-addr)
     :http-version (.getProtocol exchange)
     :method (.. exchange getRequestMethod toUpperCase)
     :path (.getPath uri)
     :query (-> uri .getQuery parse-query)
     :headers (->> exchange
                   .getRequestHeaders
                   (map (fn [[k v]] [(-> k .toLowerCase keyword) (into [] v)]))
                   (into {}))
     :body (.getRequestBody exchange)}))

(defn- make-handler [process]
  {:pre [(fn? process)]}
  (reify HttpHandler
    (handle [_ exchange]
      {:pre [(instance? HttpExchange exchange)]}
      (try
        (let [request (parse-exchange exchange)
              rsp-headers (.getResponseHeaders exchange)
              {:keys [status headers body]
               :or {status 200}} (process request)]
          (doseq [[k v] headers] (.add rsp-headers k v))
          (.sendResponseHeaders exchange (int status) 0)
          (when body
            (.. exchange getResponseBody (write body))))
        (catch Exception e
          (log/error e)
          (let [msg (-> e .getMessage .getBytes)]
            (doto exchange
              (.sendResponseHeaders 500 0)
              (.. getResponseBody (write msg)))))
        (finally
          (.close exchange))))))

(defn run [port process]
  {:pre [(int? port) (fn? process)]}
  (let [addr (InetSocketAddress. port)
        server (HttpServer/create addr 0)]
    (doto server
      (.createContext "/" (make-handler process))
      (.setExecutor (Executors/newCachedThreadPool))
      (.start))
    server))

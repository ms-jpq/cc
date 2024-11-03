(ns srv.server
  (:require [clojure.string :as str])
  (:import
   [com.sun.net.httpserver HttpExchange HttpHandler HttpServer]
   [java.net InetSocketAddress URLDecoder]
   [java.nio.charset StandardCharsets]
   [java.util.concurrent Executors]))

(def ^:private utf-8 StandardCharsets/UTF_8)

(defn- uri-decode [txt]
  {:pre [(string? txt)]}
  (try
    (URLDecoder/decode txt utf-8)
    (catch Exception _ txt)))

(defn- parse-query [query-string]
  {:pre [(string? query-string)]}
  (->> (str/split query-string #"&")
       (remove str/blank?)
       (map #(let [[k v] (map uri-decode (str/split % #"=" 2))]
               [[(keyword k) v]]))
       (into {})))

(defn- parse-exchange [exchange]
  {:pre [(instance? HttpExchange exchange)]}
  (let [uri (.getRequestURI exchange)]
    {:local-addr (.getLocalAddress exchange)
     :remote-addr (.getRemoteAddress exchange)
     :http-version (.getProtocol exchange)
     :method (.. exchange getRequestMethod toUpperCase)
     :path (.getPath uri)
     :query (-> uri .getQuery parse-query)
     :headers (-> exchange .getRequestHeaders (into {}))
     :body (.getRequestBody exchange)}))

(defn- make-handler [process]
  {:pre [(fn? process)]}
  (reify HttpHandler
    (handle [_ exchange]
      {:pre [(instance? HttpExchange exchange)]}
      (try
        (let [request (parse-exchange exchange)
              {:keys [status headers body]} (process request)]
          (doto exchange
            (.getResponseHeaders (doseq [[k v] headers] (.add k v)))
            (.sendResponseHeaders (or status 200) 0)
            (.. getResponseBody (write body))))
        (catch Exception e
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

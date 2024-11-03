(ns srv.server
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log])
  (:import
   [com.sun.net.httpserver HttpExchange HttpHandler HttpServer Headers]
   [java.io BufferedReader InputStreamReader]
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
  (->> (str/split (or query-string "") #"&")
       (remove str/blank?)
       (map #(let [[k v] (map uri-decode (str/split % #"=" 2))]
               [(keyword k) v]))
       (into {})))

(defn- parse-headers [headers]
  {:pre [(instance? Headers headers)]}
  (->> headers
       (map (fn [[k v]] [(-> k .toLowerCase keyword) (into [] v)]))
       (into {})))

(defn parse-header-params [headers]
  {:pre [(map? headers)]}
  (let [header (-> headers :content-type last)
        [value raw-params] (-> header
                               (or "")
                               (str/split #";" 2))
        params (as-> raw-params $
                 (or $ "")
                 (str/split $ #"\s+")
                 (remove str/blank? $)
                 (map #(str/split % #"=" 2) $)
                 (map (fn [[k v]] [(-> k .toLowerCase keyword) v]) $)
                 (into {} $))]
    [(str/trim value) params]))

(defn- parse-request [exchange]
  {:pre [(instance? HttpExchange exchange)]}
  (let [uri (.getRequestURI exchange)
        headers (->> exchange .getRequestHeaders parse-headers)
        [content-type {:keys [charset boundary]
                       :or {charset (str utf-8)}}] (parse-header-params headers)]
    {:local-addr (-> exchange .getLocalAddress parse-addr)
     :remote-addr (-> exchange .getRemoteAddress parse-addr)
     :http-version (.getProtocol exchange)
     :method (.. exchange getRequestMethod toUpperCase)
     :path (.getPath uri)
     :query (-> uri .getQuery parse-query)
     :headers headers
     :content-type content-type
     :charset charset
     :boundary boundary
     :body (-> exchange
               .getRequestBody
               (InputStreamReader. charset)
               (BufferedReader.))}))

(defn- make-handler [process]
  {:pre [(fn? process)]}
  (reify HttpHandler
    (handle [_ exchange]
      {:pre [(instance? HttpExchange exchange)]}
      (try
        (let [request (parse-request exchange)
              rsp-headers (.getResponseHeaders exchange)
              rsp-body (.getResponseBody exchange)
              {:keys [status headers body]
               :or {status 200}} (process request)]
          (doseq [[k v] headers] (.add rsp-headers k v))
          (.sendResponseHeaders exchange (int status) 0)
          (cond body
                (nil? nil)
                bytes? (.write rsp-body body)
                (string? body) (->> body .getBytes (.write rsp-body))
                :else (assert false body)))
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

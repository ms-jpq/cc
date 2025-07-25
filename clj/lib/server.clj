(ns lib.server
  (:require
   [clojure.datafy :refer [datafy]]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [lib.macros :as m])
  (:import
   [com.sun.net.httpserver HttpExchange HttpHandler HttpServer Headers]
   [java.io InputStream]
   [java.net InetSocketAddress URLDecoder]
   [java.nio.charset StandardCharsets]
   [java.util.concurrent Executors]))

(def utf-8 (str StandardCharsets/UTF_8))

(defn- parse-addr [addr]
  {:pre [(instance? InetSocketAddress addr)]
   :post [(map? %)]}
  {:ip (.getHostString addr)
   :port (.getPort addr)})

(defn- uri-decode [txt]
  {:pre [(string? txt)]
   :post [(string? %)]}
  (try
    (URLDecoder/decode txt utf-8)
    (catch Exception _ txt)))

(defn- parse-query [query-string]
  {:pre [((some-fn string? nil?) query-string)]
   :post [(map? %)]}
  (->> (str/split (or query-string "") #"&")
       (remove str/blank?)
       (map #(let [[k v] (map uri-decode (str/split % #"=" 2))]
               [(keyword k) v]))
       (into {})))

(defn- parse-headers [headers]
  {:pre [(instance? Headers headers)]
   :post [(map? %)]}
  (->> headers
       (map (fn [[k v]] [(-> k str/lower-case keyword) (datafy v)]))
       (into {})))

(defn parse-header-params [name headers]
  {:pre [(keyword? name) (map? headers)]
   :post [(-> % first (some-fn keyword? nil?)) (-> % last map?)]}
  (let [header (-> headers name last)
        [value raw-params] (-> header
                               (or "")
                               (str/split #";" 2))
        params (as-> raw-params $
                 (or $ "")
                 (str/split $ #"\s+")
                 (remove str/blank? $)
                 (map #(str/split % #"=" 2) $)
                 (map (fn [[k v]] [(-> k str/lower-case keyword) v]) $)
                 (into {} $))]
    [(str/trim value) params]))

(defn- parse-request [exchange]
  {:pre [(instance? HttpExchange exchange)]
   :post [(map? %)]}
  (let [uri (.getRequestURI exchange)
        headers (->> exchange .getRequestHeaders parse-headers)
        [content-type {:keys [charset boundary]
                       :or {charset utf-8}}] (parse-header-params :content-type headers)]
    {:local-addr (-> exchange .getLocalAddress parse-addr)
     :remote-addr (-> exchange .getRemoteAddress parse-addr)
     :http-version (.getProtocol exchange)
     :method (-> exchange .getRequestMethod str/lower-case keyword)
     :path (.getPath uri)
     :query (-> uri .getQuery parse-query)
     :headers headers
     :content-type content-type
     :boundary boundary
     :body (-> exchange
               .getRequestBody
               (io/reader :encoding charset))}))

(defmulti ^:private blit
  #(cond (nil? %2) :nil
         (bytes? %2) :bytes
         (string? %2) :str
         (seqable? %2) :seq
         (instance? InputStream %2) :stream))

(defmethod blit :nil [_ _] nil)
(defmethod blit :bytes [st b] (.write st b))
(defmethod blit :str [st s] (.write st (.getBytes s utf-8)))
(defmethod blit :seq [st seq] (doseq [v seq] (blit st v)))
(defmethod blit :stream [st in] (with-open [fd in] (.transferTo fd st)))

(defn- blit-stream [exchange body]
  {:pre [(instance? HttpExchange exchange)]}
  (-> exchange .getResponseBody (blit body)))

(defn- make-handler [process]
  {:pre [(fn? process)]}
  (reify HttpHandler
    (handle [_ exchange]
      {:pre [(instance? HttpExchange exchange)]}
      (try
        (let [request (parse-request exchange)
              rsp-headers (.getResponseHeaders exchange)
              {:keys [close status headers body]
               :or {status 200}} (process request)]
          (try
            (doseq [[k v] headers] (.add rsp-headers (name k) v))
            (.sendResponseHeaders exchange (int status) 0)
            (m/suppress [java.io.IOException] (blit-stream exchange body))
            (finally (when close (.close close)))))
        (catch Exception e
          (log/error e)
          (.. exchange getResponseHeaders (add "content-type" (str "text/plain; charset=" utf-8)))
          (m/suppress
           [java.io.IOException]
           (.sendResponseHeaders exchange 500 0)
           (blit-stream exchange (datafy e))))
        (finally
          (.close exchange))))))

(defn run [exec port handler]
  {:pre [(instance? Executors) (int? port) (fn? handler)]
   :post [(instance? HttpServer %)]}
  (let [addr (InetSocketAddress. port)
        server (HttpServer/create addr 0)]
    (doto server
      (.createContext "/" (make-handler handler))
      (.setExecutor exec)
      (.start))
    server))

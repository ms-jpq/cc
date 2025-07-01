(ns lib.client
  (:require [clojure.datafy :refer [datafy]]
            [lib.interop :as ip])
  (:import
   [java.net URI]
   [java.net.http HttpClient Redirect HttpRequest HttpRequest$BodyPublishers HttpResponse HttpResponse$BodyHandler HttpResponse$ResponseInfo HttpResponse$BodySubscriber HttpResponse$BodySubscribers]
   [java.util.concurrent Executors]))

(defn- new-request
  [{:keys [timeout url method headers body]}]
  {:pre [((some-fn number? nil?) timeout)
         (string? url)
         (keyword? method)
         (map? headers)
         ((some-fn nil? bytes? string? seqable? ip/stream?) body)]
   :post [(instance? HttpRequest %)]}
  (let [builder (HttpRequest/newBuilder (URI. url))
        publisher (cond
                    (nil? body) (.nobody HttpRequest$BodyPublishers)
                    (bytes? body) (->> [body] byte-array (.ofByteArray HttpRequest$BodyPublishers))
                    (string? body) (->> body (.ofString HttpRequest$BodyPublishers))
                    (seqable? body) (->> body ip/->stream (.ofInputStream HttpRequest$BodyPublishers))
                    (ip/stream? body) (->> body (.ofInputStream HttpRequest$BodyPublishers)))]
    (doseq [[k v] headers]
      (.header builder (name k) v))
    (.method builder (name method) publisher)
    (when timeout
      (->> timeout ip/->duration (.timeout builder)))
    (.build builder)))

(defn- make-handler []
  (reify HttpResponse$BodyHandler
    (apply [info]
      {:pre [(instance? HttpResponse$ResponseInfo info)]
       :post [(instance? HttpResponse$BodySubscriber %)]}
      HttpResponse$BodySubscribers/ofInputStream)))

(defn- parse-response
  [response]
  {:pre [(instance? HttpResponse response)]
   :post [(map? %)]}
  {:status (-> response .statusCode)
   :headers (->> response .headers .map (map (fn [[k v]] [(keyword k) (datafy v)])) (into {}))
   :body (-> response .body ip/->seq)})

(defn new-client
  [exec]
  {:pre [(instance? Executors exec)]
   :post [(instance? HttpClient %)]}
  (doto HttpClient/newBuilder
    (.followRedirects Redirect/NORMAL)
    (.executor exec)
    (.build)))

(defn send-request
  [client req]
  {:pre [(instance? HttpClient client) (map? req)]
   :post [(map? %)]}
  (let [request (new-request req)
        response (.send client request (make-handler))]
    (parse-response response)))

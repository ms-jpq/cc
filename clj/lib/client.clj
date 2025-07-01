(ns lib.client
  (:require [lib.interop :as ip])
  (:import
   [java.io InputStream]
   [java.net URI]
   [java.net.http HttpClient Redirect HttpRequest HttpRequest$BodyPublishers]
   [java.util.concurrent Executors]))

(defn new-request
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
                    (bytes? body) (.ofByteArray HttpRequest$BodyPublishers (byte-array [body]))
                    (string? body) (.ofString HttpRequest$BodyPublishers body)
                    (seqable? body) (->> ip/seq->stream (.ofInputStream HttpRequest$BodyPublishers))
                    (ip/stream? body) (.ofInputStream HttpRequest$BodyPublishers body))]
    (doseq [[k v] headers]
      (.header builder (name k) v))
    (.method builder (name method) publisher)
    (.build builder)))

(defn new-client
  [exec]
  {:pre [(instance? Executors exec)]
   :post [(instance? HttpClient %)]}
  (doto HttpClient/newBuilder
    (.followRedirects Redirect/NORMAL)
    (.executor exec)
    (.build)))

(ns cloudship.client.impl.mem.meta-describe
  (:require [cloudship.client.meta.protocol :as mp :refer [MetadataDescribeClient]]
            [cloudship.client.data.protocol :as p :refer [BaseClient]]
            [taoensso.nippy :as nippy]
            [clojure.set :as set]))

(defrecord InMemMetaDescribeClient [global types]
  BaseClient
  (info [client] {:type :in-mem-metadata-describe})
  MetadataDescribeClient
  (describe [client] (:global client))
  (describe-type [client type] (get (:types client) type)))

(defn from-client [other-describe-client]
  (let [global (mp/describe other-describe-client)
        all-types (concat (map :xmlName (:metadataObjects global)) (mapcat :childXmlNames (:metadataObjects global)))
        all-data (zipmap all-types (map #(mp/describe-type other-describe-client %) all-types))]
    (->InMemMetaDescribeClient global all-data)))

(defn to-nippy [client file]
  (nippy/freeze-to-file file client))

(defn from-nippy [file]
  (nippy/thaw-from-file file))


; Memoize describe client
(defn- memoize-global-fn [underlying-describe-client atom]
  (fn []
    (let [{:keys [global]} @atom]
      (or global
          (:global (swap! atom assoc :global (mp/describe underlying-describe-client)))))))


(defn- memoize-type-fn [underlying-describe-client atom]
  (fn [type]
    (if-let [type-info (get (:types @atom) type)]
      type-info
      (get-in
        (swap! atom #(assoc-in % [:types type] (mp/describe-type underlying-describe-client type)))
        [:types type]))))

(defn memoize-describe-client
  "Takes a DataDescribeClient and returns a new one that uses an atom to cache every "
  [underlying-describe-client]
  (let [a (atom {})
        global-fn (memoize-global-fn underlying-describe-client a)
        type-fn (memoize-type-fn underlying-describe-client a)]
    (reify
      BaseClient
      (info [_] {:type :memoized-metadata-describe
                 :atom a
                 :base (p/info underlying-describe-client)})
      MetadataDescribeClient
      (describe [_] (global-fn))
      (describe-type [_ type] (type-fn type)))))

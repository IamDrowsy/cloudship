(ns cloudship.client.impl.generic-soap.data
  (:require [cloudship.client.impl.generic-soap.core :as impl]
            [cloudship.client.data.conversion :as c]))

;For now we produce "bad" nil values, before we fix this, we check for them
(defn empty-tag? [content]
  (or (not content) (empty? content) (= [{}] content)))

(defn string->cloudship [describe-client object-name field value]
  (cond (= :Id field) (first value)
        :else (c/string->cloudship describe-client object-name (name field) value)))

(defn string-map->cloudship-map [describe-client m]
  {:pre [(:type m)]}
  (let [object-name (:type m)]
    (reduce-kv (fn [m k v] (assoc m k (string->cloudship describe-client object-name k v)))
               {}
               m)))

(defn query-more* [client records-of-querycall queryLocator tooling]
  (loop [ql queryLocator
         result records-of-querycall]
    (let [{:keys [queryLocator records]} (first (impl/send-soap client :queryMore {:queryLocator ql} (if tooling :tooling :data)))]
      (if (empty-tag? queryLocator)
        (into result records)
        (recur queryLocator (into result records))))))

(defn query* [client query {:keys [all tooling]}]
    (let [{:keys [queryLocator records]} (first (impl/send-soap client (if all :queryAll :query) {:queryString query} (if tooling :tooling :data)))]
      (if (empty-tag? queryLocator)
        records
        (query-more* client records queryLocator tooling))))

(defn query [client describe-client query options]
  (mapv (partial string-map->cloudship-map describe-client) (query* client query options)))

(defn delete [client describe-client options])
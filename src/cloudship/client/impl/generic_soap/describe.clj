(ns cloudship.client.impl.generic-soap.describe
  (:require [cloudship.client.impl.generic-soap.core :as impl]))

(defn describe-global [client]
  (:sobjects (first (impl/send-soap client :describeGlobal))))

(defn describe-sobjects* [client sobject-names]
  (impl/send-soap client :describeSObjects (mapv (fn [name]
                                                   {:tag (impl/in-api-ns :sObjectType :data)
                                                    :content [name]})
                                                 sobject-names)))

(defn describe-sobjects [client sobject-names]
  (mapcat (partial describe-sobjects* client) (partition-all 100 sobject-names)))

(defn describe-global-tooling [client]
  (:sobjects (first (impl/send-soap client :describeGlobal [] :tooling))))

(ns cloudship.client.impl.generic-soap.describe
  (:require [cloudship.client.impl.generic-soap.core :as impl]
            [com.rpl.specter :as s]))

(defn describe-global [client]
  (:sobjects (first (impl/send-soap client :describeGlobal))))

(defn- fix-reference-to [describe-object-data]
  (s/transform [s/ALL :fields s/ALL :referenceTo string?] vector describe-object-data))

(defn- fix-empty-valid-for
  "picklistvalue/validFor returns an empty content if the value is not valid. We fix this to nil, for better handling"
  [describe-object-data]
  (s/setval [s/ALL :fields s/ALL :picklistValues s/ALL :validFor (s/pred= [{}])] nil describe-object-data))


(defn describe-sobjects* [client sobject-names]
  (->> (impl/send-soap client :describeSObjects {:sObjectType sobject-names})
       (fix-reference-to)
       (fix-empty-valid-for)))

(defn describe-sobjects [client sobject-names]
  (mapcat (partial describe-sobjects* client) (partition-all 100 sobject-names)))

(defn describe-global-tooling [client]
  (:sobjects (first (impl/send-soap client :describeGlobal [] :tooling))))

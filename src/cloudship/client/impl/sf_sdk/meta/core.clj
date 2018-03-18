(ns cloudship.client.impl.sf-sdk.meta.core
  (:require [clojure.string :as str]
            [cloudship.client.meta.protocol :refer [MetadataClient MetadataDescribeClient]]
            [cloudship.util.java-data-extension]
            [clojure.java.data :as jd])
  (:import (com.sforce.soap.metadata MetadataConnection Metadata)
           (com.sforce.soap.partner PartnerConnection)
           (com.sforce.ws ConnectorConfig)))

(def default-namespace "{http://soap.sforce.com/2006/04/metadata}")

(defn- ->meta-url [soap-url]
  (str/replace soap-url "/u/" "/m/"))

(defn- ^MetadataConnection ->metadata-connection [^PartnerConnection pc]
  (let [pc-config (.getConfig pc)
        session (.getSessionId pc-config)
        endpoint (.getServiceEndpoint pc-config)
        meta-config (ConnectorConfig.)
        proxy (.getProxy pc-config)]
    (doto meta-config
      (.setSessionId session)
      (.setServiceEndpoint (->meta-url endpoint))
      (.setProxy proxy))
    (MetadataConnection. meta-config)))

(defn- ->api-version [^MetadataConnection meta-con]
  (Double/parseDouble (last (butlast (str/split (.getServiceEndpoint (.getConfig meta-con)) #"/")))))

(defn- add-default-namespace-if-missing [type]
  (if (str/starts-with? type "{")
    type
    (str default-namespace type)))


(defn- describe [metadata-con]
  (jd/from-java (.describeMetadata metadata-con (->api-version metadata-con))))

(defn- describe-type [metadata-con type]
  (jd/from-java (.describeValueType metadata-con (add-default-namespace-if-missing type))))

(extend-protocol MetadataDescribeClient
  PartnerConnection
  (describe [this]
    (describe (->metadata-connection this)))
  (describe-type [this type]
    (describe-type (->metadata-connection this) type)))

(defn- read-metadata-parted [meta-con meta-describe-client meta-type names]
   (jd/from-java (.getRecords (.readMetadata meta-con meta-type (into-array String names)))))

(defn- read-metadata [meta-con meta-describe-client meta-type names]
  (doall (mapcat #(read-metadata-parted meta-con meta-describe-client meta-type %1) (partition-all 10 names))))

(defn- update-metadata-parted [^MetadataConnection meta-con metadata-type metadata]
  (.updateMetadata meta-con (into-array Metadata (map #(jd/to-java (Class/forName (str "com.sforce.soap.metadata." metadata-type)) %) metadata))))

(defn- update-metadata [meta-con meta-describe-client metadata]
  (doall (mapcat #(update-metadata-parted meta-con "CustomObject" %) (partition-all 10 metadata))))

(extend-protocol MetadataClient
  PartnerConnection
  (read [this meta-describe-client meta-type names]
    (read-metadata (->metadata-connection this) meta-describe-client meta-type names))
  (update [this meta-describe-client metadata]
    (update-metadata (->metadata-connection this) meta-describe-client metadata)))
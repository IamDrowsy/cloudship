(ns ^:no-doc cloudship.client.impl.sf-sdk.meta.core
  (:require [clojure.string :as str]
            [cloudship.client.data.protocol :as base :refer [BaseClient]]
            [cloudship.client.meta.protocol :as p :refer [MetadataClient MetadataDescribeClient]]
            [cloudship.client.impl.sf-sdk.meta.convert :as convert]
            [cloudship.util.java-data-extension]
            [clojure.java.data :as jd]
            [taoensso.timbre :as t]
            [clojure.set :as set])
  (:import (com.sforce.soap.metadata MetadataConnection Metadata ListMetadataQuery)
           (com.sforce.soap.partner PartnerConnection)
           (com.sforce.ws ConnectorConfig)))

(def default-namespace "{http://soap.sforce.com/2006/04/metadata}")

(defn- result-to-map
  "Takes a SaveResult and turns it into a map"
  [item]
  (jd/from-java item))

(defn- ->meta-url [soap-url]
  (str/replace soap-url "/u/" "/m/"))

(defn ^MetadataConnection ->metadata-connection [^PartnerConnection pc]
  (let [pc-config (.getConfig pc)
        session (.getSessionId pc-config)
        endpoint (.getServiceEndpoint pc-config)
        meta-config (ConnectorConfig.)
        proxy (.getProxy pc-config)]
    (doto meta-config
      (.setSessionId session)
      (.setServiceEndpoint (->meta-url endpoint))
      (.setUsername (.getUsername pc-config))
      (.setProxy proxy))
    (MetadataConnection. meta-config)))

(defn- ->api-version [url]
  (Double/parseDouble (last (str/split url #"/"))))

(defn- add-default-namespace-if-missing [type]
  (if (str/starts-with? type "{")
    type
    (str default-namespace type)))


(defn- describe [metadata-con]
  (jd/from-java (.describeMetadata metadata-con (->api-version (.getServiceEndpoint (.getConfig metadata-con))))))

(defn- describe-type [metadata-con type]
  (jd/from-java (.describeValueType metadata-con (add-default-namespace-if-missing type))))

(extend-protocol BaseClient
  MetadataConnection
  (info [this]
    (let [config (.getConfig this)]
      {:type    :sf-meta-sdk
       :connection this
       :session (.getSessionId config)
       :endpoint (->meta-url (.getServiceEndpoint config))
       :api-version (->api-version (.getServiceEndpoint config))})))

(extend-protocol MetadataDescribeClient
  MetadataConnection
  (describe [this]
    (describe this))
  (describe-type [this type]
    (describe-type this type)))

(defn- list-meta-query [type folder]
  (let [q (ListMetadataQuery.)]
    (.setType q type)
    (if folder
      (.setFolder q folder))
    q))

(defn- api-version-from-con [^MetadataConnection meta-con]
  (Double/valueOf (:api-version (base/info meta-con))))

(defn- list-metas*
  "Internal List all metadata of a given types. Does not work with foldered types. Use list-meta(s)-in for this"
  [meta-con types]
  (.listMetadata meta-con
                 (into-array ListMetadataQuery
                             (map list-meta-query types (repeat nil)))
                 (api-version-from-con meta-con)))

(defn- flatten-list-results [list-results]
  (map convert/obj->map list-results))

(defn- list-metas
  [con types]
  (flatten-list-results (doall (mapcat (partial list-metas* con) (partition-all 3 types)))))

(defn- list-metadata
  "Lists all metadata of a given type"
  [con-or-kw meta-type]
  (map :FullName (list-metas con-or-kw [meta-type])))

;some metadata comes back as null (Social Post Layout), we need to make sure to warn and remove them
(defn- warn-and-remove-nils [recived wanted]
  (if (some nil? recived)
    (let [known (into #{} (map #(.getFullName %) (remove nil? recived)))]
      (do (t/warn "Components "
                  (clojure.set/difference (into #{} wanted)
                                          known)
                  " were recieved as null")
          (remove nil? recived)))
    recived))

(defn- read-metadata-parted [meta-con meta-describe-client meta-type names]
   (mapv convert/obj->map
         (warn-and-remove-nils
           (.getRecords (.readMetadata meta-con meta-type (into-array String names)))
           names)))

(defn- read-metadata [meta-con meta-describe-client meta-type names]
  (doall (mapcat #(read-metadata-parted meta-con meta-describe-client meta-type %1) (partition-all 10 names))))

(defn- update-metadata-parted [^MetadataConnection meta-con metadata]
  (.updateMetadata meta-con (into-array Metadata (map #(convert/map->obj %) metadata))))

(defn- update-metadata [meta-con meta-describe-client metadata]
  (doall (mapcat #(update-metadata-parted meta-con %) (partition-all 10 metadata))))

(defn- rename-metadata [meta-con meta-describe-client meta-type old-name new-name]
  (try (-> (.renameMetadata meta-con meta-type old-name new-name)
           (result-to-map)
           (set/rename-keys {:fullName :old})
           (assoc :new new-name))

       (catch Throwable t {:success false
                           :errors [(:cause  (Throwable->map t))]
                           :old old-name
                           :new new-name})))

(extend-protocol MetadataClient
  MetadataConnection
  (list [this meta-describe-client meta-type]
    (list-metadata this meta-type))
  (read [this meta-describe-client meta-type names]
    (read-metadata this meta-describe-client meta-type names))
  (update [this meta-describe-client metadata]
    (update-metadata this meta-describe-client metadata))
  (rename [this meta-describe-client meta-type old-name new-name]
    (rename-metadata this meta-describe-client meta-type old-name new-name)))
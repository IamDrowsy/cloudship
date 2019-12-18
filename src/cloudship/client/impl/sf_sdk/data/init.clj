(ns ^:no-doc cloudship.client.impl.sf-sdk.data.init
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [cloudship.client.data.protocol :refer [DataDescribeClient DataClient BaseClient]]
            [cloudship.client.impl.sf-sdk.data.describe :as describe]
            [cloudship.client.impl.sf-sdk.data.core :as impl])
  (:import (com.sforce.ws ConnectorConfig)
           (com.sforce.soap.partner PartnerConnection)))

(s/def ::valid-props map?)

(defn- set-proxy [^ConnectorConfig config proxy]
  (if (and proxy (:host proxy) (:port proxy)
           (not (clojure.string/blank? (:host proxy))))
    (.setProxy config (:host proxy) (Integer/valueOf (:port proxy)))))

(defn- set-userinfo-or-session [^ConnectorConfig config username password session]
  (if session
    (.setSessionId config session)
    (doto config
      (.setUsername username)
      (.setPassword password))))

(defn- add-service-part [api-version base-url]
  (str base-url "/services/Soap/u/" api-version "/"))

(defn- has-no-service-part? [url]
  (not (str/includes? url "services/Soap/u")))

(defn- add-soap-service-part-if-missing [api-version url]
  (if (has-no-service-part? url)
    (add-service-part api-version url)
    url))

(defn- ->soap-url [api-version base-url]
  (add-soap-service-part-if-missing api-version base-url))

(defn- ^ConnectorConfig prepare-config [{:keys [username security-token password url api-version proxy session]}]
  (doto (ConnectorConfig.)
    (set-userinfo-or-session (str username security-token) password session)
    (.setServiceEndpoint (->soap-url api-version url))
    (.setAuthEndpoint (->soap-url api-version url))
    (set-proxy proxy)))

(defn ->partner-connection [props]
  (PartnerConnection. (prepare-config props)))

(defn- ->base-url [service-endpoint]
  (str/join "/" (take 3 (str/split service-endpoint #"/"))))

(defn- ->api-version [service-endpoint]
  (nth (str/split service-endpoint #"/") 6))

(extend-protocol BaseClient
  PartnerConnection
  (info [this]
    (let [config (.getConfig this)]
      {:type    :sf-data-sdk
       :connection this
       :session (.getSessionId config)
       :endpoint (->base-url (.getServiceEndpoint config))
       :api-version (->api-version (.getServiceEndpoint config))
       :user {:username (.getUserName (.getUserInfo this))
              :id (.getUserId (.getUserInfo this))
              :email (.getUserEmail (.getUserInfo this))}})))

(extend-protocol DataDescribeClient
  PartnerConnection
  (describe-global [this] (describe/describe-global this))
  (describe-objects [this object-names] (describe/describe-objects this object-names)))

(extend-protocol DataClient
  PartnerConnection
  (query [this describe-client query-string options]
    (impl/query this describe-client query-string options))
  (insert [this describe-client records options]
    (impl/insert this describe-client records options))
  (update [this describe-client records options]
    (impl/update this describe-client records options))
  (upsert [this describe-client records options]
    (impl/upsert this describe-client records options))
  (delete [this describe-client records options]
    (impl/delete this describe-client records options))
  (undelete [this describe-client ids options]
    (impl/undelete this ids options))
  (remove-from-bin [this describe-client ids options]
    (impl/remove-from-bin this ids options))
  (process [this describe-client records]
    (impl/process this records)))


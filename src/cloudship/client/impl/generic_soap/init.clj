(ns cloudship.client.impl.generic-soap.init
  (:require [clojure.string :as str]
            [cloudship.client.data.protocol :refer [DataDescribeClient DataClient BaseClient]]
            [cloudship.client.impl.generic-soap.describe :as describe]
            [cloudship.client.impl.generic-soap.data :as data]
            [cloudship.client.impl.generic-soap.core :as core]))

(defrecord GenericSoapClient [base-url api-version session])

(defn parse-url [server-url]
  (let [[p _ url services soap u api-version _] (str/split server-url #"/")]
    [(str "https://" url "/") api-version]))

(defn ->generic-client [{:keys [url api-version session]}]
  (->GenericSoapClient url api-version session))

(extend-protocol BaseClient
  GenericSoapClient
  (info [this] {:type :generic-soap
                :endpoint (core/->soap-url (:api-version this) (:base-url this) :data)
                :client this
                :session (:session this)
                :api-version (:api-version this)}))

(extend-protocol DataDescribeClient
  GenericSoapClient
  (describe-global [this] (describe/describe-global this))
  (describe-objects [this object-names] (describe/describe-sobjects this object-names)))

(extend-protocol DataClient
  GenericSoapClient
  (query [this describe-client query options] (data/query this describe-client query options)))


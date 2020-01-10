(ns cloudship.client.impl.generic-xml.init
  (:require [clojure.string :as str]
            [cloudship.client.impl.generic-xml.core :as impl]
            [cloudship.client.data.protocol :refer [DataDescribeClient]]))

(defrecord SoapClient [base-url api-version session])

(defn parse-url [server-url]
  (let [[p _ url services soap u api-version _] (str/split server-url #"/")]
    [(str "https://" url "/") api-version]))

(defn ->generic-client [{:keys [url api-version session]}]
  (->SoapClient url api-version session))

(extend-protocol DataDescribeClient
  SoapClient
  (describe-global [this] (impl/describe-global this))
  (describe-objects [this object-names] (impl/describe-sobjects this object-names)))
(ns cloudship.client.sf-sdk.data.describe
  (:require [cloudship.client.protocols :refer [DataDescribeClient]]
            [clojure.java.data :as jd])
  (:import (com.sforce.soap.partner PartnerConnection)))

(defn- describe-objects* [client object-names]
  (jd/from-java (.describeSObjects client (into-array String object-names))))

(defn- describe-objects [client object-names]
  (mapcat #(describe-objects* client %) (partition-all 100 object-names)))

(defn- describe-global [client]
  (:sobjects (jd/from-java (.describeGlobal client))))

(extend-protocol DataDescribeClient
  PartnerConnection
  (describe-global [this] (describe-global this))
  (describe-sobjects [this object-names] (describe-objects this object-names)))
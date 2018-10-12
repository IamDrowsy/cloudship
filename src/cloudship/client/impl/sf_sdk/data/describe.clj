(ns ^:no-doc cloudship.client.impl.sf-sdk.data.describe
  (:require [cloudship.client.data.protocol :refer [DataDescribeClient]]
            [clojure.java.data :as jd])
  (:import (com.sforce.soap.partner PartnerConnection)))

(defn- describe-objects* [client object-names]
  (jd/from-java (.describeSObjects client (into-array String object-names))))

(defn describe-objects [client object-names]
  (mapcat #(describe-objects* client %) (partition-all 100 object-names)))

(defn describe-global [client]
  (:sobjects (jd/from-java (.describeGlobal client))))
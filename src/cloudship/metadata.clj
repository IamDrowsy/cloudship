(ns cloudship.metadata
  (:refer-clojure :exclude [read update list])
  (:require [cloudship.client.meta.protocol :as p]
            [cloudship.client.data.protocol :as dp]
            [cloudship.client.core :as c]
            [cloudship.util.misc :as misc]))

(defn describe [cloudship]
  (p/describe cloudship))

(defn describe-type [cloudship type]
  (p/describe-type cloudship type))

(defn list [cloudship metadata-type]
  (p/list cloudship cloudship metadata-type))

(defn read [cloudship metadata-type & metadata-names]
  (p/read cloudship cloudship metadata-type (misc/normalize-simple-var-args metadata-names)))

(defn update [cloudship metadata]
  (p/update cloudship cloudship metadata))

(defn evict
  "Removes the connection for this keyword/prop-map from the cache"
  [client-description]
  (c/evict-cloudship-client client-description))

(defn info
  [client-description]
  (dp/info client-description))
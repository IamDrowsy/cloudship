(ns cloudship.metadata
  (:refer-clojure :exclude [read update list])
  (:require [cloudship.client.meta.protocol :as p]
            [cloudship.client.data.protocol :as dp]
            [cloudship.client.core :as c]
            [cloudship.util.misc :as misc]))

(defn describe
  "Returns the global meta describe data for the given cloudship."
  [cloudship]
  (p/describe cloudship))

(defn describe-type
  "Returns the describe data for the given cloudship and metadata type"
  [cloudship type]
  (p/describe-type cloudship type))

(defn list
  "Returns a list of all existing entries of the given cloudship and metadata type"
  [cloudship metadata-type]
  (p/list cloudship cloudship metadata-type))

(defn read
  "Returns the metadata for the given metadata-names of the metadata type."
  [cloudship metadata-type & metadata-names]
  (p/read cloudship cloudship metadata-type (misc/normalize-simple-var-args metadata-names)))

(defn read-all
  "Returns the metadat for all metadata entries of the given metadata type."
  [cloudship metadata-type]
  (p/read cloudship cloudship metadata-type (list cloudship metadata-type)))

(defn update [cloudship metadata]
  (p/update cloudship cloudship metadata))

(defn evict
  "Removes the connection for this keyword/prop-map from the cache"
  [client-description]
  (c/evict-cloudship-client client-description))

(defn info
  [client-description]
  (dp/info client-description))
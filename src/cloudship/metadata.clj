(ns ^{:doc "Namespace for the metadata api. This is experimental the returned/taken data might change."}
  cloudship.metadata
  (:refer-clojure :exclude [read update list])
  (:require [cloudship.client.meta.protocol :as p]
            [cloudship.client.data.protocol :as dp]
            [cloudship.client.core :as c]
            [cloudship.util.misc :as misc]
            [cloudship.util.result :as result]
            [cloudship.client.impl.sf-sdk.meta.core :as meta]
            [ebenbild.core :as e]))

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
  "Returns the metadata for all metadata entries of the given metadata type."
  [cloudship metadata-type]
  (p/read cloudship cloudship metadata-type (list cloudship metadata-type)))

(defn update
  "Updates the given metadata"
  [cloudship metadata]
  (p/update cloudship cloudship metadata))

(defn create
  "Creates the given metadata"
  [cloudship metadata]
  (p/create cloudship cloudship metadata))

(defn delete
  "Deletes the given metadata"
  ([cloudship metadata]
   (p/delete cloudship cloudship metadata))
  ([cloudship metadata-type metadata-fullnames]
   (delete cloudship (map (fn [name]
                            {:metadata-type metadata-type
                             :FullName name})
                          metadata-fullnames))))

(defn rename
  "Renames a given metadata.
   you can provide:
   * [cloudship type old-name new-name]
   * [cloudship type name-mapping]"
  ([cloudship metadata-type rename-map]
   (result/report-results! (doall (map #(rename cloudship metadata-type (key %) (val %))
                                       rename-map))))
  ([cloudship metadata-type old-name new-name]
   (p/rename cloudship cloudship metadata-type old-name new-name)))

(defn evict
  "Removes the connection for this keyword/prop-map from the cache"
  [client-description]
  (c/evict-cloudship-client client-description))

(defn info
  [client-description]
  (dp/info client-description))

(defn search-by-name
  "Searches all metadata types that match the ebenbild example data"
  [cloudship search-data]
  (->> (describe cloudship)
       (:metadataObjects)
       (map :xmlName)
       (filter (e/like search-data))))
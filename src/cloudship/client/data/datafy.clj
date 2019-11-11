(ns cloudship.client.data.datafy
  (:require [cloudship.client.data.conversion :as conv]
            [cloudship.client.data.describe :as describe]
            [com.rpl.specter :refer :all]
            [clojure.core.protocols :as cp]
            [clojure.string :as str])
  (:import (java.net URL)))

(declare datafy-row)
(declare datafy-object-description)
(declare datafy-object-descriptions)
(declare datafy-child-relations)
(declare datafy-child-relation)

(defn- pull-all-data [cloudship q-fn row]
  (datafy-row cloudship q-fn (setval [MAP-VALS nil?] NONE (first (q-fn cloudship (:type row) "*" {:in [:Id [(:Id row)]]})))))

;; datafy
(defn- navize-row [cloudship q-fn row]
  (let [object-type (:type row)]
    (with-meta row
               {`cp/nav (fn [coll k v]
                          (let [describe-data (transform [MAP-VALS seq?] vec (describe/describe-object cloudship (:type coll)))]
                            (case k
                              :type (datafy-object-description cloudship q-fn {:context-id (:Id row)} describe-data)
                              :cloudship/describe-data (datafy-object-description cloudship q-fn {:context-id (:Id row)} describe-data)
                              :cloudship/all-data (pull-all-data cloudship q-fn row)
                              :cloudship/link (URL. (str/replace (:urlDetail describe-data) #"\{ID\}" (:Id coll)))
                              :cloudship/children (datafy-child-relations cloudship q-fn {:context-id (:Id row)}
                                                                          (mapv #(select-keys % [:field :relationshipName :childSObject]) (:childRelationships describe-data)))
                              (let [field-type (conv/field-type cloudship object-type (name k))]
                                (if (and (= field-type "reference")
                                         (not (nil? v)))
                                  (let [target-object (first (describe/describe-id cloudship v))]
                                    (pull-all-data cloudship q-fn {:type target-object :Id v}))
                                  v)))))})))

(defn- navigation-map [row]
  #:cloudship{:link :nav-to-pull
              :children :nav-to-pull
              :describe-data (:type row)
              :all-data :nav-to-pull})

(defn datafy-row [cloudship q-fn row]
  (with-meta (merge row (navigation-map row))
             {`cp/datafy (partial navize-row cloudship q-fn)}))

(defn- navize-object-description [cloudship q-fn options object-description]
  (with-meta object-description
             {`cp/nav (fn [coll k v]
                        (case k
                          :childRelationships
                          (datafy-child-relations cloudship q-fn options v)
                          v))}))

(defn datafy-object-description [cloudship q-fn options object-description]
  (with-meta object-description
             {`cp/datafy (partial navize-object-description cloudship q-fn options)}))

(defn datafy-object-descriptions [cloudship q-fn object-descriptions]
  (mapv (partial datafy-object-description cloudship q-fn {}) object-descriptions))

(defn- navize-child-relations [cloudship q-fn {:keys [context-id]} child-relations]
  (with-meta child-relations
             {`cp/nav (fn [coll k v]
                        (if context-id
                          (let [object-type (:childSObject v)
                                parent-field (:field v)]
                            (into [] (q-fn cloudship object-type [:Id]
                                        {:where (str parent-field "='" context-id "'")
                                         :datafy true})))
                          v))}))

(defn datafy-child-relations [cloudship q-fn options child-relations]
  (with-meta child-relations
             {`cp/datafy (partial navize-child-relations cloudship q-fn options)}))

(ns cloudship.client.query
  (:require [clojure.java.data :as jd]
            [cloudship.client.describe :as describe]
            [ebenbild.core :refer [like]])
  (:import (org.mule.tools.soql SOQLParserHelper)))

(defn parse-soql-query [query-string]
  (jd/from-java (SOQLParserHelper/createSOQLData query-string)))

(defn- field-desc->field-list [fields]
  (map (comp keyword :name) fields))

(defn- add-id [field-list]
  (let [field-set (into #{} field-list)]
    (if (or (field-set :Id)
            (field-set "Id"))
      field-list
      (cons :Id field-list))))

(defn determine-field-list
  "Determinse the field list for a query based on input. Always adds id."
  [describe-client input object]
  (cond
    (= :required input) (determine-field-list describe-client (like {:createable true :nillable false :defaultedOnCreate false}) object)
    (#{:all "*"} input) (determine-field-list describe-client identity object)
    (or (keyword? input) (fn? input)) (add-id (field-desc->field-list (filter input (:fields (describe/describe-object describe-client object)))))
    (coll? input) (add-id input)
    :else input))

(defn- in-string [id-set]
  (str " ('" (apply str (interpose "','" id-set)) "')"))

(defn- field-string [field-or-fields]
  (if (coll? field-or-fields)
    (apply str (interpose "," (map name field-or-fields)))
    (name field-or-fields)))

(defn build-query-string [obj field-or-fields options]
  (let [field-string (field-string field-or-fields)]
    (str "SELECT " field-string " FROM " obj
         (if (:where options)
           (str " WHERE " (:where options)))
         (if (:sort options)
           (str " ORDER BY " (:sort options)))
         (if (:in options)
           (str (if (:where options)
                  " AND "
                  " WHERE ")
                (name (first (:in options)))
                " IN " (in-string (second (:in options)))))
         (if (:limit options)
           (str " LIMIT " (:limit options))))))


(ns cloudship.datascript.data
  (:require [cloudship.data :as data]
            [cloudship.datascript.describe :as dsd]
            [com.rpl.specter :refer :all]
            [datascript.core :as d]
            [cloudship.util :as u]
            [clojure.string :as str]))

(defn sobject-with-type->namespace [sobject]
  (let [special-namespace {:Id :sobject
                           :type :sobject}
        t (:type sobject)]
    (transform [MAP-KEYS (collect-one STAY) NAMESPACE]
               (fn [k _] (name (special-namespace k t)))
               sobject)))

(defn sobjects-with-type->namespace [sobjects]
  (mapv sobject-with-type->namespace sobjects))

(defn remove-nil-vals [sobjects]
  (setval [ALL MAP-VALS nil?] NONE sobjects))

(defn sobject-field-keys [describe-db]
  (d/q '[:find [?key ...]
         :where
         [?f :field/key ?key]
         [?f :field/type "reference"]]
       @describe-db))

(defn generate-schema [describe-db]
  (let [ref-field-schema (zipmap (sobject-field-keys describe-db)
                                 (repeat {:db/valueType :db.type/ref}))]
    (merge {:sobject/Id {:db/unique :db.unique/identity}
            :sobject/org {:db/valueType :db.type/ref}
            :org/id {:db/unique :db.unique/identity}
            :org/name {:db/unique :db.unique/identity}
            :migrate/target {:db/valueType :db.type/ref
                             :db/cardinality :db.cardinality/one}}
           ref-field-schema)))


(defn ref-fields->db-ids [ref-fields records]
  (transform [ALL (submap ref-fields) MAP-VALS (complement str/blank?)] (fn [id] {:sobject/Id id})
             records))

;Transacting Objects later might not work because right now the schema is not updated!
; also updating the schema on the db does not work as expected
(defn transact-sobjects!
  [db org-id sobjects]
  (d/transact! db (->> sobjects
                       (sobjects-with-type->namespace)
                       (remove-nil-vals)
                       (setval [ALL :sobject/org] {:org/id org-id})
                       (ref-fields->db-ids (sobject-field-keys db)))))

(defn create-db [con sobjects]
  (let [types (remove nil? (distinct (map :type sobjects)))
        describe-data  (data/describe-objects con types)
        describe-db (dsd/describe->datascript describe-data)
        describe-schema (dsd/describe->schema (data/describe-objects con types))
        schema (merge (generate-schema describe-db) describe-schema)
        db     (d/create-conn schema)]
    (doto db
      (d/transact! (dsd/describe->init-transaction describe-data))
      (dsd/transact-field-keys!)
      (transact-sobjects! (u/org-id con) sobjects))))

(defn pull-sobject [db id]
  (let [ref-pull (zipmap (sobject-field-keys db)
                         (repeat [:sobject/Id]))]
    (->> (d/pull @db ["*" ref-pull] id)
         (transform [MAP-VALS map? #(contains? % :sobject/Id) (collect-one :sobject/Id)] (fn [id _] id))
         (setval [:db/id] NONE)
         (transform [MAP-KEYS] #(keyword (name %))))))

(defn pull-sobjects [db]
  (let [ids (d/q '[:find [?id ...]
                   :where [?id :sobject/Id _]]
                 @db)]
    (mapv (partial pull-sobject db) ids)))

(defn refresh-db
  "Recreates a db using all the existing data with a newly extracted schema"
  [con old-db]
  (create-db con (pull-sobjects old-db)))




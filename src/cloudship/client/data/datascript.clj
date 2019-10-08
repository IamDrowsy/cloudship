(ns cloudship.client.data.datascript
  (:require [cloudship.data :as data]
            [meander.epsilon :as m]
            [com.rpl.specter :refer :all]
            [datascript.core :as d]
            [clojure.string :as str]
            [clojure.set :as set]))

(defn key->namespace [key]
  (case key
    :Id :sobject
    :type :sobject))


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

(defn ref-fields [describe-data]
  (m/search describe-data
            (m/scan {:fields (m/scan {:type "reference"
                                      :name ?fieldname})
                     :name ?object})
            (keyword ?object ?fieldname)))

(defn generate-schema
  [con sobject-types]
  (let [describe-data (apply (partial data/describe-objects con) sobject-types)
        ref-fields (zipmap (ref-fields describe-data)
                           (repeat {:db/valueType :db.type/ref}))]
    (merge {:sobject/Id {:db/unique :db.unique/identity}} ref-fields)))

(defn db->ref-fields [db]
  (keys (setval [MAP-VALS (selected? :db/valueType #(not= :db.type/ref %))] NONE (:schema @db))))

(defn ref-fields->db-ids [ref-fields records]
  (transform [ALL (submap ref-fields) MAP-VALS (complement str/blank?)] (fn [id] {:sobject/Id id})
             records))


;Transacting Objects later might not work because right now the schema is not updated!
; also updating the schema on the db does not work as expected
(defn transact-sobjects!
  [db sobjects]
  (d/transact! db (->> sobjects
                        (sobjects-with-type->namespace)
                        (remove-nil-vals)
                        (ref-fields->db-ids (db->ref-fields db)))))

(defn create-db [con sobjects]
  (let [schema (generate-schema con (map :type sobjects))
        db     (d/create-conn schema)]
    (transact-sobjects! db sobjects)
    db))

(defn pull-sobject [db id]
  (let [ref-pull (zipmap (db->ref-fields db)
                         (repeat [:sobject/Id]))]
    (->> (d/pull @db ["*" ref-pull] id)
         (transform [MAP-VALS map? #(contains? % :sobject/Id) (collect-one :sobject/Id)] (fn [id _]
                                                                                           id))
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
  (let [all-types (ffirst (d/q '[:find (distinct ?t) :where [_ :sobject/type ?t]]
                               @old-db))
        new-schema (generate-schema con all-types)
        new-db (d/create-conn new-schema)]
    (transact-sobjects! new-db (pull-sobjects old-db))
    new-db))




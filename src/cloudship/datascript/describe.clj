(ns cloudship.datascript.describe
  (:require [datascript.core :as d]
            [com.rpl.specter :refer :all]
            [clojure.string :as str]))

(defn- string-seq? [val]
  (and (sequential? val) (every? string? val) (not-empty val)))

(defn- simple-val? [val]
  (or (string? val) (number? val) (boolean? val) (string-seq? val)))

(def MAP-VALS-WITH-COLLECTED-KEY
  (path [ALL (collect-one FIRST) LAST]))

(def NESTED-MAPS
  (recursive-path [] p
                  [(multi-path [STAY]
                               [MAP-VALS-WITH-COLLECTED-KEY
                                (if-path simple-val?
                                         STOP
                                         [ALL p])])]))

(defn- singular [s]
  (str/replace s #"s$" ""))

(defn- add-parent-namespace [parent-namespace m]
  (transform [ALL (putval parent-namespace) NESTED-MAPS MAP-KEYS NAMESPACE]
             (fn [& args]
               (let [last-parent (second (reverse args))]
                 (singular (name last-parent))))
             m))

(defn- ref-fields [describe-data]
  (->> (select [ALL (putval :sobject) NESTED-MAPS] describe-data)
       (filter #(< 2 (count %)))
       (map (fn [args]
              (let [[_ rel-name parent & _] (reverse args)]
                (keyword (singular (name parent)) (name rel-name)))))
       (into #{})))

(defn- invalid? [val]
  (or (nil? val)
      (and (not (simple-val? val))
           (empty? val))))

(defn- remove-invalid-values [describe-data]
  (setval [ALL NESTED-MAPS MAP-VALS invalid?] NONE describe-data))

(def id-fields
  [:sobject/name
   :field/key])

(defn describe->schema
  "Returns the schema needed for the describe-data datascript db"
  [describe-data]
  (merge (zipmap (ref-fields describe-data)
                 (repeat {:db/valueType :db.type/ref
                          :db/cardinality :db.cardinality/many}))
         (zipmap id-fields
                 (repeat {:db/unique :db.unique/identity}))))

(defn describe->init-transaction
  "Returns the initial transaction data for the describe-data datascript db
  You also want to transact-field-keys! into this db."
  [describe-data]
  (->> describe-data
       (add-parent-namespace :sobject)
       (remove-invalid-values)))

(defn transact-field-keys!
  "Transacts the field-keys into an existing describe-data datascript db"
  [db]
  (let [transact-data (map (fn [[db-id object field]]
                             [:db/add db-id :field/key (keyword object field)])
                           (d/q '[:find ?f ?object ?field
                                  :where
                                  [?f :field/name ?field]
                                  [?o :sobject/fields ?f]
                                  [?o :sobject/name ?object]]
                                @db))]
    transact-data
    (d/transact! db transact-data)))

(defn describe->datascript
  "Returns a fully populised describe-data datascript db"
  [describe-data]
  (let [schema (describe->schema describe-data)
        db (d/create-conn schema)]
    (d/transact! db (describe->init-transaction describe-data))
    (transact-field-keys! db)
    db))

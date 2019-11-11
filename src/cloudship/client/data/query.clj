(ns ^:no-doc cloudship.client.data.query
  (:require [cloudship.client.data.describe :as describe]
            [ebenbild.core :refer [like]]
            [clojure.string :as str]
            [instaparse.core :as insta]))

(def soql-grammar
  "<query> = <'SELECT'> fieldList <'FROM'> object options?
   fieldList = fieldListEntry [<','> fieldListEntry]*
   fieldListEntry = (field|subquery|typeof)
   <typeof> = 'TYPEOF' <any* 'END'>
   subquery = <'('> query <')'>
   <field> = fieldname|aggregate
   <aggregate> = #'[a-zA-Z0-9]+' '(' fieldname? ')'
   object = objectname
   <options> = ['WHERE'|'WITH'|'GROUP BY'|'ORDER BY'|'LIMIT'|'OFFSET'|'FOR'] any*
   <any> = <#'.'>
   <objectname> = #'[a-zA-Z0-9_]+'
   <fieldname> = #'[a-zA-Z0-9_.]+'")

(def whitespace-parser
  (insta/parser
    "whitespace = #'\\s+'"))

(def soql-parser
  (insta/parser soql-grammar
                :auto-whitespace whitespace-parser
                :string-ci true))

(defn object-from-query [query-string]
  (second (last (soql-parser query-string))))

(defn- field-desc->field-list [fields]
  (map (comp keyword :name) fields))

(defn- add-id [field-list]
  (let [field-set (set field-list)]
    (if (or (field-set :Id)
            (field-set "Id"))
      field-list
      (cons :Id field-list))))

(defn determine-field-list
  "Determine the field list for a query based on input. Always adds id."
  [describe-client input object]
  (cond
    (= :required input) (determine-field-list describe-client (like {:createable true :nillable false :defaultedOnCreate false}) object)
    (#{:all "*"} input) (determine-field-list describe-client identity object)
    (or (keyword? input) (fn? input)) (add-id (field-desc->field-list (filter input (:fields (describe/describe-object describe-client object)))))
    (coll? input) (add-id input)
    :else input))

(defn- in-string [id-set]
  (str " ('" (str/join "','" id-set) "')"))

(defn- field-string [field-or-fields]
  (if (coll? field-or-fields)
    (str/join "," (map name field-or-fields))
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


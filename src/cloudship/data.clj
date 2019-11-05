(ns ^{:doc "Namespace with the data api"}
  cloudship.data
  (:refer-clojure :exclude [update])
  (:require [cloudship.client.data.protocol :as p]
            [cloudship.client.core :as c]
            [cloudship.client.data.describe :as describe]
            [cloudship.client.data.query :as query]
            [cloudship.client.data.conversion :as conv]
            [cloudship.spec.data :as data]
            [cloudship.util.result :as result]
            [cloudship.util.misc :as misc]
            [taoensso.timbre :as t]
            [cloudship.util.user-interact :as interact]
            [ebenbild.core :refer [like]]
            [clojure.spec.alpha :as s]
            [clojure.core.protocols :as cp]
            [clojure.string :as str]
            [com.rpl.specter :refer :all])
  (:import (java.net URL)))

(declare datafy-result-set)
(declare datafy-row)
(declare datafy-object-description)
(declare datafy-object-descriptions)
(declare datafy-child-relations)
(declare datafy-child-relation)

(defn describe
  "Returns the global describe data for the given cloudship."
  [cloudship]
  (p/describe-global cloudship))

(defn describe-objects
  "Returns the describe data for the given cloudship and objects (as list or as varargs)."
  [cloudship & object-names]
  (datafy-object-descriptions
    cloudship
    (p/describe-objects cloudship (misc/normalize-simple-var-args object-names))))

(defn describe-object
  "Returns the describe data for the given cloudship and a single given object."
  [cloudship object-name]
  (describe/describe-object cloudship object-name))

(defn describe-id
  "Returns the Objectnames for the given cloudship and salesforce id(-prefix).
  Only checks the first 3 letters of a given id as they determine the object type.
  As there are edge cases where more than one object with a given prefix, a list is returned.
  If now Object is found, returns nil and logs a warning."
  [cloudship id]
  (describe/describe-id cloudship id))

(defn- resolved-api-call [client-description api-call & other-args]
  (apply (partial api-call client-description client-description) other-args))

(defn query
  "Returns the result of this SOQL query-string for the given cloudship.
  Have a look at 'q' for a more structured way to query."
  ([cloudship query-string]
   (query cloudship query-string {}))
  ([cloudship query-string options]
   (cond-> (resolved-api-call cloudship p/query query-string options)
           (:datafy options) ((partial datafy-result-set cloudship)))))

(s/fdef query
        :ret (s/coll-of ::data/sObject))

(defn- query-opt-error? [{:keys [in]}]
  (cond (and in (empty? (second in))) (do (t/error "In collection is empty.") true)
        :else nil))

(defn- in-query-too-long? [in query-string]
  (and in (> (count query-string) 15000)))

(declare q)

(defn- split-up-in-query [con object field-or-fields options]
  (let [[in-field in-ids] (:in options)]
    (t/debug "Query for " object " has to many in-ids, splitting in two queries")
    (doall (mapcat #(q con object field-or-fields (assoc options :in [in-field %]))
                   (partition-all (/ (count in-ids) 2) in-ids)))))

(defn q
  "Structured version of the query function.
   Is not limited to 2000 records (uses QueryLocator if needed).
   Also understands '*' or ':all' (all fields), ':required' (required fields) as field-or-fields.
   Given a function for field-or-fields, it will run it as predicate on the field metadata.
   This especially works for keywords like ':updateable' or ':createable'.
   Always queries Id.
   Options are {:where  WhereString, :in [infield idset] :all true (query-all)
                :limit limitnumber :bulk true}
   If the idset of :in is too big and results in a too long query, it will automaticly split the query and concat the results again."
  ([cloudship object field-or-fields]
   (q cloudship object field-or-fields {}))
  ([cloudship object field-or-fields {:keys [in all] :as options}]
   (let [client (c/resolve-cloudship-client cloudship)
         field-list (query/determine-field-list client field-or-fields object)
         query-string (query/build-query-string object field-list options)]
     (cond (query-opt-error? options) []
           (in-query-too-long? in query-string) (split-up-in-query client object field-list options)
           :else (query client query-string options)))))

(defn count-records
  "Returns the number of records for a given Object and options. Options are the same as q."
  ([cloudship object]
   (count-records cloudship object {}))
  ([cloudship object options]
   (:expr0 (first (q cloudship object "count(Id)" options)))))

;CRUD stuff

(defn- resolved-crud-call [client-description api-call & other-args]
  (result/report-results!
    (apply (partial resolved-api-call client-description api-call) other-args)))

(defn insert
  "Insert for given maps.
  All records must have a valid :type.
  Possible options are: {:batch-size size, :soap-parallel true, :bulk true, :bulk-serial true}."
 ([cloudship records]
  (insert cloudship records {}))
 ([cloudship records options]
  (if (empty? records)
    (do (t/info "Nothing to insert.") [])
    (resolved-crud-call cloudship p/insert records options))))
(s/fdef insert
        :ret (s/coll-of ::data/result))

(defn update
  "Update for given maps.
  All records must have :Id and a valid :type.
  Possible options are: {:batch-size size, :soap-parallel true, :bulk true, :bulk-serial true}."
  ([cloudship records]
   (update cloudship records {}))
  ([cloudship records options]
   (if (empty? records)
     (do (t/info "Nothing to update.") [])
     (resolved-crud-call cloudship p/update records options))))
(s/fdef update
        :ret (s/coll-of ::data/result))

(defn upsert
  "Upsert for given maps.
  All records must have :Id and a valid :type.
  The :upsert-key option needs to be set, therefor upsert has no arity without options.
  Other possible options are: {:batch-size size, :soap-parallel true, :bulk true, :bulk-serial true}."
  [cloudship records options]
  (if (empty? records)
    (do (t/info "Nothing to upsert") [])
    (resolved-crud-call cloudship p/upsert records options)))
(s/fdef upsert
        :ret (s/coll-of ::data/result))

(defn ->id [id-or-map]
  (if (string? id-or-map)
    id-or-map
    (:Id id-or-map)))

(defn delete
  "Deletes the given maps (with ids) or ids.
  If option :dont-ask is not set, asks before deleting records.
  Other possible option are: {:batch-size size, :soap-parallel true, :bulk true, :bulk-serial true}"
  ([cloudship records-or-ids]
   (delete cloudship records-or-ids {}))
  ([cloudship records-or-ids options]
   (if (empty? records-or-ids)
     (do (t/info "Nothing to delete.") [])
     (let [ids (map ->id records-or-ids)]
       (if (or (:dont-ask options)
               (interact/ask-to-continue!
                 (str "You're about to delete " (count ids) " records "
                        " of Object '" (first (describe-id cloudship (first ids)))
                        "' on Connection " cloudship)))
         (resolved-crud-call cloudship p/delete ids options)
         (do (t/info "Aborted by user.") []))))))
(s/fdef delete
        :ret (s/coll-of ::data/result))

(defn undelete
  "Undeletes the given maps (with ids) or ids"
  ([cloudship records-or-ids]
   (undelete cloudship records-or-ids {}))
  ([cloudship records-or-ids options]
   (if (empty? records-or-ids)
     (do (t/info "Nothing to undelete.") [])
     (let [ids (map ->id records-or-ids)]
       (resolved-crud-call cloudship p/undelete ids options)))))

(defn remove-from-bin
  "Removes the given maps (with ids) or ids from the bin"
  ([cloudship records-or-ids]
   (remove-from-bin cloudship records-or-ids {}))
  ([cloudship records-or-ids options]
   (if (empty? records-or-ids)
     (do (t/info "Nothing to remove from bin.") [])
     (let [ids (map ->id records-or-ids)]
       (resolved-crud-call cloudship p/remove-from-bin ids options)))))

(defn evict
  "Removes the connection for this keyword/prop-map from the cache"
  [cloudship]
  (c/evict-cloudship-client cloudship))

(defn info
  "Returns information for this client.
   Usally contains username, session, url and the underlying client."
  [cloudship]
  (p/info cloudship))

(defn user-info
  "Returns the userinfo for a cloudship"
  [cloudship]
  (:user (:data-client (p/info cloudship))))

(defn pull-all-data [cloudship row]
  (datafy-row cloudship (setval [MAP-VALS nil?] NONE (first (q cloudship (:type row) "*" {:in [:Id [(:Id row)]]})))))

;; datafy
(defn- navize-row [cloudship row]
  (let [object-type (:type row)
        describe-data (datafy-object-description
                        cloudship
                        {:context-id (:Id row)}
                        (describe-object cloudship object-type))]
    (with-meta row
               {`cp/nav (fn [coll k v]
                          (let [describe-data (transform [MAP-VALS seq?] vec (describe-object cloudship (:type coll)))]
                            (case k
                              :type (datafy-object-description cloudship {:context-id (:Id row)} describe-data)
                              :cloudship/describe-data (datafy-object-description cloudship {:context-id (:Id row)} describe-data)
                              :cloudship/all-data (pull-all-data cloudship row)
                              :cloudship/link (URL. (str/replace (:urlDetail describe-data) #"\{ID\}" (:Id coll)))
                              :cloudship/children (datafy-child-relations cloudship {:context-id (:Id row)}
                                                                          (mapv #(select-keys % [:field :relationshipName :childSObject]) (:childRelationships describe-data)))
                              (let [field-type (conv/field-type cloudship object-type (name k))]
                                (if (and (= field-type "reference")
                                         (not (nil? v)))
                                  (let [target-object (first (describe-id cloudship v))]
                                    (pull-all-data cloudship {:type target-object :Id v}))
                                  v)))))})))

(defn special-keys [row]
  #:cloudship{:link :nav-to-pull
              :children :nav-to-pull
              :describe-data (:type row)
              :all-data :nav-to-pull})

(defn- datafy-row [cloudship row]
  (with-meta (merge row (special-keys row))
             {`cp/datafy (partial navize-row cloudship)}))

(defn datafy-result-set [cloudship rs]
  (mapv (partial datafy-row cloudship) rs))

(defn- navize-object-description [cloudship options object-description]
  (with-meta object-description
             {`cp/nav (fn [coll k v]
                        (case k
                          :childRelationships
                          (datafy-child-relations cloudship options v)
                          v))}))

(defn datafy-object-description [cloudship options object-description]
  (with-meta object-description
             {`cp/datafy (partial navize-object-description cloudship options)}))

(defn datafy-object-descriptions [cloudship object-descriptions]
  (mapv (partial datafy-object-description cloudship {}) object-descriptions))

(defn- navize-child-relations [cloudship {:keys [context-id]} child-relations]
  (with-meta child-relations
             {`cp/nav (fn [coll k v]
                        (if context-id
                          (let [object-type (:childSObject v)
                                parent-field (:field v)]
                            (into [] (q cloudship object-type [:Id]
                                        {:where (str parent-field "='" context-id "'")
                                         :datafy true})))
                          v))}))

(defn datafy-child-relations [cloudship options child-relations]
  (with-meta child-relations
             {`cp/datafy (partial navize-child-relations cloudship options)}))
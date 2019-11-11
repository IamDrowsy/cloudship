(ns ^{:doc "Namespace with the data api"}
  cloudship.data
  (:refer-clojure :exclude [update])
  (:require [cloudship.client.data.protocol :as p]
            [cloudship.client.core :as c]
            [cloudship.client.data.describe :as describe]
            [cloudship.client.data.query :as query]
            [cloudship.client.data.conversion :as conv]
            [cloudship.client.data.datafy :as datafy]
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

(declare q)

(defn describe
  "Returns the global describe data for the given cloudship."
  [cloudship]
  (p/describe-global cloudship))

(defn describe-objects
  "Returns the describe data for the given cloudship and objects (as list or as varargs)."
  [cloudship & object-names]
  (datafy/datafy-object-descriptions
    cloudship
    q
    (p/describe-objects cloudship (misc/normalize-simple-var-args object-names))))

(defn describe-object
  "Returns the describe data for the given cloudship and a single given object."
  [cloudship object-name]
  (datafy/datafy-object-description
    cloudship
    q
    {}
    (describe/describe-object cloudship object-name)))

(defn describe-id
  "Returns the Objectnames for the given cloudship and salesforce id(-prefix).
  Only checks the first 3 letters of a given id as they determine the object type.
  As there are edge cases where more than one object with a given prefix, a list is returned.
  If now Object is found, returns nil and logs a warning."
  [cloudship id]
  (describe/describe-id cloudship id))

(defn- resolved-api-call [client-description api-call & other-args]
  (apply (partial api-call client-description client-description) other-args))

(defn datafy-results
  "EXPERIMENTAL.

  Adds implementation for datafy to a result of [[q]].
  Also adds navigatable special keys to all records.

  | key              | description |
  |------------------| ------------|
  | `:cloudship/link`| navigates to the url of this record
  | `:cloudship/children` | navigates to the describe data of all child refs. From there you can navigate to all child records.
  | `:cloudship/describe-data` | navigates to the describe data of this type
  | `:cloudship/all-data` | navigates to the same record but queries all fields
  "
  [cloudship rs]
  (mapv (partial datafy/datafy-row cloudship q) rs))

(defn query
  "Returns the result of this SOQL `query-string` for the given `cloudship`.
  Have a look at [[q]] for a more structured way to query."
  ([cloudship query-string]
   (query cloudship query-string {}))
  ([cloudship query-string options]
   (cond-> (resolved-api-call cloudship p/query query-string options)
           (:datafy options) ((partial datafy-results cloudship)))))

(s/fdef query
        :ret (s/coll-of ::data/sObject))

(defn- query-opt-error? [{:keys [in]}]
  (cond (and in (empty? (second in))) (do (t/error "In collection is empty.") true)
        :else nil))

(defn- in-query-too-long? [in query-string]
  (and in (> (count query-string) 15000)))

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
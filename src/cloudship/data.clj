(ns ^{:doc "Namespace with the data api"}
  cloudship.data
  (:refer-clojure :exclude [update])
  (:require [cloudship.client.protocols :as p]
            [cloudship.client.core :as c]
            [cloudship.client.describe :as describe]
            [cloudship.spec :as cs]
            [cloudship.util.result :as result]
            [cloudship.client.query :as query]
            [taoensso.timbre :as t]
            [cloudship.util.user-interact :as interact]
            [ebenbild.core :refer [like]]
            [clojure.spec.alpha :as s]))

(defn- normalize-simple-var-args
  "Normalizes varargs by flattening sequential inputs
  so [[1 2 3] 1 2 [1 2]] becomes [1 2 3 1 2 1 2].
  Only works for one level."
  [var-args]
  (mapcat #(if (sequential? %) % [%]) var-args))

(defn describe-global
  "Resolves client-description and calls describe-global with it."
  [client-description]
  (p/describe-global (c/resolve-cloudship-client client-description)))

(defn describe-objects
  "Resolves client-description and returns the describe data of the given objects"
  [client-description & object-names]
  (p/describe-objects (c/resolve-cloudship-client client-description) (normalize-simple-var-args object-names)))

(defn describe-object
  "Resolves client-description and returns the describe data of a single object"
  [client-description object-name]
  (describe/describe-object (c/resolve-cloudship-client client-description) object-name))

(defn describe-id
  "Object name of an id"
  [client-description id]
  (describe/describe-id (c/resolve-cloudship-client client-description) id))

(defn- resolved-api-call [client-description api-call & other-args]
  (let [cloudship-client (c/resolve-cloudship-client client-description)]
    (apply (partial api-call cloudship-client cloudship-client) other-args)))

(defn query
  "Resolves the client and returns the result of this SOQL query-string.
  Have a look at 'q' for a more structured way to query."
  ([client-description query-string]
   (query client-description query-string {}))
  ([client-description query-string options]
   (resolved-api-call client-description p/query query-string options)))
(s/fdef query
        :ret (s/coll-of ::cs/sObject))

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
   Also understands '*' or ':all' (all fields), ':required' (required fields) as field.
   Given a function for field-or-fields, it will run it as predicate on the field metadata.
   This especially works for keywords like ':updateable' or ':createable'.
   Always queries Id.
   Options are {:where  WhereString, :in [infield idset] :all true (query-all)
                :limit limitnumber :bulk true}
   If the idset of in is too big and results in a too long query, it will automaticly split the query and concat the results again."
  ([client-description object field-or-fields]
   (q client-description object field-or-fields {}))
  ([client-description object field-or-fields {:keys [in all] :as options}]
   (let [client (c/resolve-cloudship-client client-description)
         field-list (query/determine-field-list client field-or-fields object)
         query-string (query/build-query-string object field-list options)]
     (cond (query-opt-error? options) []
           (in-query-too-long? in query-string) (split-up-in-query client object field-list options)
           :else (query client query-string options)))))

;CRUD stuff

(defn- resolved-crud-call [client-description api-call & other-args]
  (result/report-results!
    (apply (partial resolved-api-call client-description api-call) other-args)))

(defn insert
  "Insert for given maps.
  All records must have a valid :type.
  Possible options are: {:partition-size size, :bulk true, :serial true}."
 ([client-description records]
  (insert client-description records {}))
 ([client-description records options]
  (if (empty? records)
    (do (t/info "Nothing to insert.") [])
    (resolved-crud-call client-description p/insert records options))))
(s/fdef insert
        :ret (s/coll-of ::cs/result))

(defn update
  "Update for given maps.
  All records must have :Id and a valid :type.
  Possible options are: {:partition-size size, :bulk true, :serial true}."
  ([client-description records]
   (update client-description records {}))
  ([client-description records options]
   (if (empty? records)
     (do (t/info "Nothing to update.") [])
     (resolved-crud-call client-description p/update records options))))
(s/fdef update
        :ret (s/coll-of ::cs/result))

(defn upsert
  "Upsert for given maps.
  All records must have :Id and a valid :type.
  The :upsert-key option needs to be set, there upsert has no arity without options.
  Possible options are: {:partition-size size, :bulk true, :serial true}."
  [client-description records options]
  (if (empty? records)
    (do (t/info "Nothing to upsert") [])
    (resolved-crud-call client-description p/upsert records options)))
(s/fdef upsert
        :ret (s/coll-of ::cs/result))

(defn ->id [id-or-map]
  (if (string? id-or-map)
    id-or-map
    (:Id id-or-map)))

(defn delete
  "Deletes the given maps or ids"
  ([client-description records-or-ids]
   (delete client-description records-or-ids {}))
  ([client-description records-or-ids options]
   (if (empty? records-or-ids)
     (do (t/info "Nothing to delete.") [])
     (let [ids (map ->id records-or-ids)]
       (if (or (:dont-ask options)
               (interact/ask-to-continue!
                 (str "You're about to delete " (count ids) " records "
                        " of Object '" (first (describe-id client-description (first ids)))
                        "' on Connection " client-description)))
         (resolved-crud-call client-description p/delete ids options)
         (do (t/info "Aborted by user.") []))))))
(s/fdef delete
        :ret (s/coll-of ::cs/result))

(defn evict
  "Removes the connection for this keyword/prop-map from the cache"
  [client-description]
  (c/evict-cloudship-client client-description))
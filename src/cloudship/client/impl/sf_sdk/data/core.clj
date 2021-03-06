(ns ^:no-doc cloudship.client.impl.sf-sdk.data.core
  (:refer-clojure :exclude [update])
  (:require [taoensso.timbre :as t]
            [cloudship.client.data.protocol :refer [DataClient]]
            [cloudship.client.impl.sf-sdk.data.coerce :as coerce]
            [cloudship.client.impl.sf-sdk.data.bulk :as bulk]
            [cloudship.client.impl.sf-sdk.util.reflect :as reflect]
            [clojure.string :as str]
            [ebenbild.core :as e]
            [com.rpl.specter :refer :all]
            [clojure.java.data :as jd])
  (:import (com.sforce.soap.partner PartnerConnection QueryResult ProcessRequest)
           (com.sforce.soap.partner.fault MalformedQueryFault)
           (java.util Map)
           (com.sforce.soap.partner.sobject SObject)))

; -------------- query --------------------------

(defn- query-more*
  "takes a query result and calls queryMore as long as needed
   also flattes the result into one list of records"
  [^PartnerConnection client ^QueryResult first-result]
  (loop [locator (.getQueryLocator first-result)
         all-records (.getRecords first-result)]
    (if (nil? locator)
      all-records
      (let [result (.queryMore client locator)]
        (recur (.getQueryLocator result)
               (concat all-records (.getRecords result)))))))

(defn- query*
  "Internal. Like query, but returns raw SObjects"
  [^PartnerConnection client query-string {:keys [all]}]
  (try
    (let [query-fn (if  all
                     #(.queryAll client query-string)
                     #(.query client query-string))
          result ^QueryResult (query-fn)]
      (query-more* client result))
    (catch MalformedQueryFault e
      (t/error "Error Using query " query-string)
      (t/error e))))

(defn query
  [^PartnerConnection client data-describe-client query-string options]
  (if (:bulk options)
    (bulk/query client data-describe-client query-string options)
    (doall (map #(coerce/sobj->map data-describe-client %)
                (query* client query-string options)))))

; ------------------ crud calls --------------------------

(defn- result-to-map
  "Takes a SaveResult/DeleteResult and turns it into a map"
  [item]
  (jd/from-java item))

(defn- sanatize-error
  [{:keys [fields statusCode message] :as e}]
  (if (= statusCode "UNABLE_TO_LOCK_ROW")
    (assoc e :message "") ;on rowlock we remove the message as it might differ and is useless
    e))

(defn- parse-error
  "Parses errors, these could be strings (bulk api) or IErrors (soap)"
  [error]
  (sanatize-error
    (cond (instance? Map error)
          {:fields     (str (into [] (:fields error)))
           :message    (:message error)
           :statusCode (str (:statusCode error))}
          (instance? String error)
          (let [parts (str/split error #":")]
            {:fields     (last parts)
             :message    (apply str (drop 1 (butlast parts))) ; the message could contain :
             :statusCode (first parts)})
          :else
          (do (t/warn error " is not a known error type")
              {}))))

(defn- soap-action* [client action type inputs {:keys [batch-size soap-parallel] :or {batch-size 200}}]
  (let [mapfn (if soap-parallel pmap map)]
    (map result-to-map
         (apply concat
                (doall
                  (mapfn
                    (comp (partial action client) #(into-array type %))
                    (partition-all batch-size inputs)))))))

(defn- insert*
  "Internal. Like insert but takes sobjects"
  [client sobjects options]
  (soap-action* client #(.create ^PartnerConnection %1 %2) SObject sobjects options))

(defn insert
  "Insert for given sobjects as maps.
 Possible options are: {:batch-size size, :bulk true, :serial true}."
  [client data-describe-client records {:keys [bulk] :as options}]
  (if bulk
    (bulk/insert client data-describe-client records options)
    (insert* client (map (partial coerce/map->sobj data-describe-client) records) options)))

(defn ^:no-doc update*
  "Internal. Like update but takes sobjects"
  [client sobjects options]
  (soap-action* client #(.update ^PartnerConnection %1 %2) SObject sobjects options))

(defn update
  "Soap update for given maps.
Possible options are: {:batch-size size, :bulk true, :serial true}."
  [client data-describe-client records {:keys [bulk] :as options}]
  (if bulk
    (bulk/update client data-describe-client records options)
    (update* client (map (partial coerce/map->sobj data-describe-client) records) options)))

(defn ^:no-doc upsert*
  "Internal. Like upsert but takes idfieldname and sobjects"
  [client idfieldname sobjects options]
  (soap-action* client #(.upsert ^PartnerConnection %1 idfieldname %2) SObject sobjects options))

(defn upsert
  "Soap upsert for given idkey and records.
  You need to provide :upsert-key as option.
  Other options are: {:batch-size size, :bulk true, :serial true}."
  [client data-describe-client records {:keys [bulk upsert-key] :as options}]
  (if bulk
    (bulk/upsert client data-describe-client upsert-key records options)
    (upsert* client (name upsert-key) (map (partial coerce/map->sobj data-describe-client) records) options)))

(defn delete
  "Deletes records with the given ids or maps (with :Id key).
Asks for your permission to delete stuff if ':dont-ask true' is not set as option.
Other options are: {:batch-size size, :bulk true, :hard true, :serial true}."
  [client data-describe-client ids {:keys [bulk hard] :as options}]
  (cond bulk (bulk/delete client data-describe-client ids options)
        hard (throw (IllegalArgumentException. "Cannot hard delete via SOAP API"))
        :else (soap-action* client #(.delete ^PartnerConnection %1 %2) String ids options)))

(defn undelete
  "Undeletes records with the given ids.
Possible options are: {:batch-size size, parallel? true}.
Parallel will just use 10 soap cons so be aware of row locks."
  ([client ids]
   (undelete client ids {}))
  ([client ids options]
   (soap-action* client #(.undelete ^PartnerConnection %1 %2) String ids options)))

(defn remove-from-bin
  "Removes records with the given ids from the bin."
  ([client ids]
   (remove-from-bin client ids {}))
  ([client ids options]
   (soap-action* client #(.emptyRecycleBin ^PartnerConnection %1 %2) String ids options)))

(defn- fix-workitem-request
  "For ProcessWorkitemRequest the action must be 'Removed' instead of 'Remove' (which is documented). We fix this here."
  [workitem-request]
  (setval [ALL (e/like {:type "ProcessWorkitemRequest"}) :action (pred= "Remove")] "Removed" workitem-request))

(defn- to-lower-keys [m]
  (transform [ALL MAP-KEYS NAME (regex-nav #"^.")] str/lower-case m))


(defn process*
  [client records]
  (soap-action* client #(.process ^PartnerConnection %1 %2) ProcessRequest records {}))

(defn process
  [client records]
  (process* client (map (partial reflect/map->obj :partner) (fix-workitem-request (to-lower-keys records)))))


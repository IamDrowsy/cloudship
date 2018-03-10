(ns cloudship.client.sf-sdk.data.core
  (:require [taoensso.timbre :as t]
            [cloudship.client.protocols :refer [DataClient]]
            [cloudship.client.sf-sdk.data.coerce :as coerce])
  (:import (com.sforce.soap.partner PartnerConnection QueryResult)
           (com.sforce.soap.partner.fault MalformedQueryFault)))

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

(defn- query
  [^PartnerConnection client data-describe-client query-string options]
  (doall (map #(coerce/sobj->map data-describe-client %)
              (query* client query-string options))))

(extend-protocol DataClient
  PartnerConnection
  (query [this describe-client query-string options]
    (query this describe-client query-string options)))
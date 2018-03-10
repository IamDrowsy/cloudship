(ns cloudship.client.protocols
  (:refer-clojure :exclude [update]))

(defprotocol DataClient
  (query [this describe-client query options])
  (insert [this describe-client records options])
  (update [this describe-client records options])
  (upsert [this describe-client records options])
  (delete [this describe-client ids options])
  (undelete [this describe-client ids options])
  (remove-from-bin [this describe-client ids options]))

(defprotocol DataDescribeClient
  (describe-global [this])
  (describe-sobjects [this object-names]))
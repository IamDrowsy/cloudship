(ns ^:no-doc cloudship.client.data.protocol
  (:refer-clojure :exclude [update]))

(defprotocol BaseClient
  (info [this]))

(defprotocol DataClient
  (query [this describe-client query options])
  (insert [this describe-client records options])
  (update [this describe-client records options])
  (upsert [this describe-client records options])
  (delete [this describe-client ids options])
  (undelete [this describe-client ids options])
  (remove-from-bin [this describe-client ids options])
  (process [this describe-client ids]))

(defprotocol DataDescribeClient
  (describe-global [this])
  (describe-objects [this object-names]))
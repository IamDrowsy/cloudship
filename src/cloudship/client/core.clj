(ns cloudship.client.core
  (:require [cloudship.client.protocols :as p :refer [DataDescribeClient DataClient]]
            [cloudship.connection.props.core :as props]
            [cloudship.client.mem.describe :as md]
            [cloudship.client.sf-sdk.data.init :as init]))

(defrecord CloudshipClient [data-describe-client data-client metadata-client]
  DataDescribeClient
  (describe-global [this] (p/describe-global (:data-describe-client this)))
  (describe-objects [this object-names] (p/describe-objects (:data-describe-client this) object-names))
  DataClient
  (query [this describe-client query options]
    (p/query (:data-client this) describe-client query options))
  (insert [this describe-client records options]
    (p/insert (:data-client this) describe-client records options))
  (update [this describe-client records options]
    (p/update (:data-client this) describe-client records options))
  (upsert [this describe-client records options]
    (p/upsert (:data-client this) describe-client records options))
  (delete [this describe-client records options]
    (p/delete (:data-client this) describe-client records options)))

(defn init-cloudship-client [props]
  (let [partner-con (init/->partner-connection props)]
    (->CloudshipClient (md/memoize-describe-client partner-con)  partner-con partner-con)))

(defn resolve-cloudship-client [resolveable]
  (cond (instance? CloudshipClient resolveable)
        resolveable
        (or (keyword? resolveable) (map? resolveable))
        (init-cloudship-client (props/->props resolveable))
        :else (throw (ex-info (str resolveable " is not resolveable to a cloudship client")
                              {:resolvable resolveable}))))

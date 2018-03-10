(ns cloudship.client.core
  (:require [cloudship.client.protocols :as p :refer [DataDescribeClient DataClient]]
            [cloudship.connection.props.core :as props]
            [cloudship.client.sf-sdk.data.init :as init]))

(defrecord Client [data-describe-client data-client metadata-client]
  DataDescribeClient
  (describe-global [this] (p/describe-global (:data-describe-client this)))
  (describe-sobjects [this object-names] (p/describe-sobjects (:data-describe-client this) object-names))
  DataClient
  (query [this describe-client query options]
    (p/query (:data-client this) describe-client query options)))


(defn init-client [kw-or-props]
  (let [props (props/->props kw-or-props)
        partner-con (init/->partner-connection props)]
    (->Client partner-con partner-con partner-con)))

(defn resolve-data-describe-client [resolveable]
  (if (satisfies? DataDescribeClient resolveable)
    resolveable
    (init-client resolveable)))

(defn resolve-data-client [resolveable]
  (if (satisfies? DataClient resolveable)
    resolveable
    (init-client resolveable)))

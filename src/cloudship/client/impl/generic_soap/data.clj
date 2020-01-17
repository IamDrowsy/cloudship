(ns cloudship.client.impl.generic-soap.data
  (:require [cloudship.client.impl.generic-soap.core :as impl]))

(defn query* [client query {:keys [all]}]
  (impl/send-soap client :query {:queryString query}))

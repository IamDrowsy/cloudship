(ns ^:no-doc cloudship.client.data.describe
  (:require [taoensso.timbre :as t]
            [cloudship.client.data.protocol :as p]))

(defn describe-object
  "Resolves client-description and returns the describe data of a single object"
  [describe-client object-name]
  (first (p/describe-objects describe-client [object-name])))

(defn describe-id
  "Object name of an id"
  [describe-client id]
  (let [prefix (subs id 0 3)
        matching (filter #(= prefix (:keyPrefix %)) (p/describe-global describe-client))]
    (if (empty? matching)
      (t/info "No Description found for prefix " prefix)
      (map :name matching))))
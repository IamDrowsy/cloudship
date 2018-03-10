(ns ^{:doc "Namespace with the data api"}
  cloudship.data
  (:require [cloudship.client.protocols :as p]
            [cloudship.client.core :as c]
            [cloudship.client.mem.describe :as md]))

(defn- normalize-simple-var-args
  "Normalizes varargs by flattening sequential inputs
  so [[1 2 3] 1 2 [1 2]] becomes [1 2 3 1 2 1 2].
  Only works for one level."
  [var-args]
  (mapcat #(if (sequential? %) % [%]) var-args))

(defn describe-global
  "Resolves client-description and calls describe-global with it."
  [client-description]
  (p/describe-global (c/resolve-data-describe-client client-description)))

(defn describe-objects
  "Resolves client-description and returns the describe data of the given objects"
  [client-description & object-names]
  (p/describe-sobjects (c/resolve-data-describe-client client-description) (normalize-simple-var-args object-names)))

(defn describe-object
  "Resolves client-description and returns the describe data of a single object"
  [client-description object-name]
  (first (describe-objects (c/resolve-data-describe-client client-description) object-name)))

(defn query
  "Resolves the client and returns the result of this SOQL query-string.
  Have a look at 'q' for a more structured way to query."
  ([client-description query-string]
   (query client-description query-string{}))
  ([client-description query-string options]
   (let [resolved-client (c/resolve-data-client client-description)]
     (p/query resolved-client (md/memoize-describe-client resolved-client) query-string options))))
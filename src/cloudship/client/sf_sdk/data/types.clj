(ns ^{:doc "Internal namespace to transform types from cloudship to the salesforce java sdk."}
  cloudship.client.sf-sdk.data.types
  (:require [ebenbild.core :refer [like]]
            [taoensso.timbre :as t]
            [clojure.string :as str]
            [cloudship.client.protocols :as p]
            [clj-time.format :as timef]
            [clj-time.core :as time])
  (:import [java.util Base64 Base64$Decoder]))

(defn- find-field [obj-data fieldname]
  (first (filter (like {:name (re-pattern fieldname)})
                 (:fields obj-data))))

(defn- find-field-by-reference-name [objdata reference-name]
  (first (filter (like {:relationshipName reference-name})
                 (:fields objdata))))

(defn- field-type [data-describe-client objname fieldname]
  (let [obj-data (first (p/describe-objects data-describe-client [objname]))]
    (if-let [field
             (or (find-field obj-data fieldname) (find-field-by-reference-name obj-data fieldname))]
      (:type field)
      (t/warn "no field" (str objname "." fieldname) "found"))))

(defmulti sf->clj-fn
  "Multimethod to get the cast fn from a salesforce field to the correct type"
  (fn [c o f] (field-type c o f))
  :default "string")

(defn sf->clj
  [c o f v]
  ((sf->clj-fn c o f) v))

(defn- nil-or-blank? [value]
  (or (nil? value) (str/blank? value)))

(defn- wrap-parse-fn-empty-safe
  "Returns a fn that Parses value with parse-fn if v is not empty, otherwise returns nil"
  [parse-fn]
  (fn [value]
    (if (nil-or-blank? value)
      nil
      (parse-fn value))))

(defn- coerce-empty-to-nil [value]
  (if (nil-or-blank? value)
    nil
    value))

(def sf-datetime-formatter (timef/formatter :date-time))
(def sf-date-formatter (timef/formatter :date))

(defmethod sf->clj-fn "string" [c o f] coerce-empty-to-nil)
(defmethod sf->clj-fn "reference" [c o f] coerce-empty-to-nil)
(defmethod sf->clj-fn "id" [c o f] coerce-empty-to-nil)
(defmethod sf->clj-fn "int" [c o f] (wrap-parse-fn-empty-safe #(Integer/valueOf %)))
(defmethod sf->clj-fn "picklist" [c o f] coerce-empty-to-nil)
(defmethod sf->clj-fn "multipicklist" [c o f] coerce-empty-to-nil)
(defmethod sf->clj-fn "combobox" [c o f] coerce-empty-to-nil)
(defmethod sf->clj-fn "base64" [c o f] #(.decode ^Base64$Decoder (Base64/getDecoder) %))
(defmethod sf->clj-fn "boolean" [c o f] (wrap-parse-fn-empty-safe #(Boolean/valueOf %)))
(defmethod sf->clj-fn "currency" [c o f] coerce-empty-to-nil)
(defmethod sf->clj-fn "textarea" [c o f] coerce-empty-to-nil)
(defmethod sf->clj-fn "double" [c o f] (wrap-parse-fn-empty-safe #(Double/valueOf %)))
(defmethod sf->clj-fn "percent" [c o f] (wrap-parse-fn-empty-safe #(Double/valueOf %)))
(defmethod sf->clj-fn "phone" [c o f] coerce-empty-to-nil)
(defmethod sf->clj-fn "date" [c o f] (wrap-parse-fn-empty-safe (partial timef/parse sf-date-formatter)))
(defmethod sf->clj-fn "datetime" [c o f] (wrap-parse-fn-empty-safe (partial timef/parse sf-datetime-formatter)))
(defmethod sf->clj-fn "encryptedstring" [c o f] coerce-empty-to-nil)
(defmethod sf->clj-fn "datacategorygroupreference" [c o f] coerce-empty-to-nil)
(defmethod sf->clj-fn "location" [c o f] coerce-empty-to-nil)
(defmethod sf->clj-fn "address" [c o f] coerce-empty-to-nil)
(defmethod sf->clj-fn "anytype" [c o f] coerce-empty-to-nil)
(defmethod sf->clj-fn "complexvalue" [c o f] coerce-empty-to-nil)

(defmulti clj->sf
  "Multimethod to cast not correctly typed entries to the needed sf values"
  (fn [c o f v] [(field-type c o f) (type v)])
  :default ["string" String])

(defmacro def-clj->sf [sf-type clj-type f]
  `(defmethod clj->sf [~sf-type ~clj-type]
     [con# obj# field# val#] (~f val#)))

(def-clj->sf "string" String identity)
(def-clj->sf "date" String (wrap-parse-fn-empty-safe (partial timef/unparse sf-date-formatter)))
(def-clj->sf "datetime" String (wrap-parse-fn-empty-safe (partial timef/unparse sf-datetime-formatter)))
(def-clj->sf "int" String (wrap-parse-fn-empty-safe #(Integer/valueOf %)))
(def-clj->sf "int" Long (fnil #(int %) nil))
(def-clj->sf "boolean" String #(Boolean/valueOf %))
(def-clj->sf "base64" String #(.getBytes %))
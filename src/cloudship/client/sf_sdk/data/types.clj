(ns ^{:doc "Internal namespace to transform types from cloudship to the salesforce java sdk."}
  cloudship.client.sf-sdk.data.types
  (:require [ebenbild.core :refer [like]]
            [taoensso.timbre :as t]
            [clojure.string :as str]
            [cloudship.data :as data])
  (:import [java.util Base64 Base64$Decoder]))

(defn- find-field [obj-data fieldname]
  (first (filter (like {:name (re-pattern fieldname)})
                 (:fields obj-data))))

(defn- find-field-by-reference-name [objdata reference-name]
  (first (filter (like {:relationshipName reference-name})
                 (:fields objdata))))

(defn- field-type [data-describe-client objname fieldname]
  (let [obj-data (data/describe-object data-describe-client objname)]
    (if-let [field
             (or (find-field obj-data fieldname) (find-field-by-reference-name obj-data fieldname))]
      (:value (:type field))
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

(defmethod sf->clj-fn "string" [c o f] coerce-empty-to-nil)
(defmethod sf->clj-fn "reference" [c o f] coerce-empty-to-nil)
(defmethod sf->clj-fn "id" [c o f] coerce-empty-to-nil)
(defmethod sf->clj-fn "int" [c o f] (coerce-empty-to-nil #(Integer/valueOf %)))
(defmethod sf->clj-fn "picklist" [c o f] coerce-empty-to-nil)
(defmethod sf->clj-fn "multipicklist" [c o f] coerce-empty-to-nil)
(defmethod sf->clj-fn "combobox" [c o f] coerce-empty-to-nil)
(defmethod sf->clj-fn "base64" [c o f] #(.decode ^Base64$Decoder (Base64/getDecoder) %))
(defmethod sf->clj-fn "boolean" [c o f] (coerce-empty-to-nil #(Boolean/valueOf %)))
(defmethod sf->clj-fn "currency" [c o f] coerce-empty-to-nil)
(defmethod sf->clj-fn "textarea" [c o f] coerce-empty-to-nil)
(defmethod sf->clj-fn "double" [c o f] (coerce-empty-to-nil #(Double/valueOf %)))
(defmethod sf->clj-fn "percent" [c o f] (coerce-empty-to-nil #(Double/valueOf %)))
(defmethod sf->clj-fn "phone" [c o f] coerce-empty-to-nil)
(defmethod sf->clj-fn "date" [c o f] (coerce-empty-to-nil clojure.instant/read-instant-date))
(defmethod sf->clj-fn "datetime" [c o f] (coerce-empty-to-nil clojure.instant/read-instant-calendar))
(defmethod sf->clj-fn "encryptedstring" [c o f] coerce-empty-to-nil)
(defmethod sf->clj-fn "datacategorygroupreference" [c o f] coerce-empty-to-nil)
(defmethod sf->clj-fn "location" [c o f] coerce-empty-to-nil)
(defmethod sf->clj-fn "address" [c o f] coerce-empty-to-nil)
(defmethod sf->clj-fn "anytype" [c o f] coerce-empty-to-nil)
(defmethod sf->clj-fn "complexvalue" [c o f] coerce-empty-to-nil) 
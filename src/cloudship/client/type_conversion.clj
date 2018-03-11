(ns cloudship.client.type-conversion
  (:require [ebenbild.core :refer [like]]
            [taoensso.timbre :as t]
            [clojure.string :as str]
            [cloudship.client.protocols :as p]
            [java-time.format :as jtf]
            [java-time.core :as jt]
            [java-time.zone :as jtz]
            [java-time.local :as jtl]
            [java-time.temporal :as jtt]
            [java-time.convert :as jtc])
  (:import (java.util Base64 Base64$Decoder)))

(defn- find-field [obj-data fieldname]
  (first (filter (like {:name (re-pattern fieldname)})
                 (:fields obj-data))))

(defn- find-field-by-reference-name [objdata reference-name]
  (first (filter (like {:relationshipName reference-name})
                 (:fields objdata))))

(defn field-type [data-describe-client object-name field-name]
  (let [obj-data (first (p/describe-objects data-describe-client [object-name]))]
    (if-let [field
             (or (find-field obj-data field-name) (find-field-by-reference-name obj-data field-name))]
      (:type field)
      (do (t/warn "no field" (str object-name "." field-name) "found, defaulting to string") "string"))))

(defmulti string->cloudship-fn*
  "Returns a function that parses a given string into the cloudship type used for the given object and field (using the data-describe client)."
  (fn [field-type] field-type))

(defn string->cloudship-fn
  "Returns a function that parses a given string into the cloudship type used for the given type
   or a data-describe client, object-name and field-name."
  ([field-type]
   (let [parse-fn (string->cloudship-fn* field-type)]
     (fn [string]
       (if (or (nil? string) (str/blank? string))
         nil
         (parse-fn string)))))
  ([data-describe-client object-name field-name]
   (string->cloudship-fn (field-type data-describe-client object-name field-name))))

(defn string->cloudship
  ([field-type string]
   ((string->cloudship-fn field-type) string))
  ([data-describe-client object-name field-name string]
   ((string->cloudship-fn data-describe-client object-name field-name) string)))

(def datetime-formatter (jtf/formatter :iso-date-time))
(def date-formatter (jtf/formatter :iso-date))

(defmethod string->cloudship-fn* "string" [type] identity)
(defmethod string->cloudship-fn* "reference" [type] identity)
(defmethod string->cloudship-fn* "id" [type] identity)
(defmethod string->cloudship-fn* "int" [type] #(Integer/valueOf ^String %))
(defmethod string->cloudship-fn* "picklist" [type] identity)
(defmethod string->cloudship-fn* "multipicklist" [type] identity)
(defmethod string->cloudship-fn* "combobox" [type] identity)
(defmethod string->cloudship-fn* "base64" [type] #(.decode ^Base64$Decoder (Base64/getDecoder) ^String %))
(defmethod string->cloudship-fn* "boolean" [type] #(Boolean/valueOf ^String %))
(defmethod string->cloudship-fn* "currency" [type] identity)
(defmethod string->cloudship-fn* "textarea" [type] identity)
(defmethod string->cloudship-fn* "double" [type]  #(Double/valueOf ^String %))
(defmethod string->cloudship-fn* "percent" [type] #(Double/valueOf ^String %))
(defmethod string->cloudship-fn* "phone" [type] identity)
(defmethod string->cloudship-fn* "date" [type] (partial jtl/local-date date-formatter))
(defmethod string->cloudship-fn* "datetime" [type] (partial jtt/instant datetime-formatter))
(defmethod string->cloudship-fn* "encryptedstring" [type] identity)
(defmethod string->cloudship-fn* "datacategorygroupreference" [type] identity)
(defmethod string->cloudship-fn* "location" [type] identity)
(defmethod string->cloudship-fn* "address" [type] identity)
(defmethod string->cloudship-fn* "anytype" [type] identity)
(defmethod string->cloudship-fn* "complexvalue" [type] identity)

(defmulti cloudship->string-fn*
  (fn [field-type] field-type))

(defmethod cloudship->string-fn* "string" [type] str)
(defmethod cloudship->string-fn* "reference" [type] str)
(defmethod cloudship->string-fn* "id" [type] str)
(defmethod cloudship->string-fn* "int" [type] str)
(defmethod cloudship->string-fn* "picklist" [type] str)
(defmethod cloudship->string-fn* "multipicklist" [type] str)
(defmethod cloudship->string-fn* "combobox" [type] str)
(defmethod cloudship->string-fn* "base64" [type] str)
(defmethod cloudship->string-fn* "boolean" [type] str)
(defmethod cloudship->string-fn* "currency" [type] str)
(defmethod cloudship->string-fn* "textarea" [type] str)
(defmethod cloudship->string-fn* "double" [type]  str)
(defmethod cloudship->string-fn* "percent" [type] str)
(defmethod cloudship->string-fn* "phone" [type] str)
(defmethod cloudship->string-fn* "date" [type] (partial jtf/format date-formatter))
(defmethod cloudship->string-fn* "datetime" [type] (partial jtf/format datetime-formatter))
(defmethod cloudship->string-fn* "encryptedstring" [type] str)
(defmethod cloudship->string-fn* "datacategorygroupreference" [type] str)
(defmethod cloudship->string-fn* "location" [type] str)
(defmethod cloudship->string-fn* "address" [type] str)
(defmethod cloudship->string-fn* "anytype" [type] str)
(defmethod cloudship->string-fn* "complexvalue" [type] str)

(defn cloudship->string-fn
  "Returns a function that formats a given object into the string for the given field-type
   or a data-describe client, object-name and field-name."
  ([field-type]
   (let [format-fn (cloudship->string-fn* field-type)]
     (fn [value]
       (cond (string? value) value
             (nil? value) nil
             :else (format-fn value)))))
  ([data-describe-client object-name field-name]
   (cloudship->string-fn (field-type data-describe-client object-name field-name))))

(defn cloudship->string
  ([field-type value]
   ((cloudship->string-fn field-type) value))
  ([data-describe-client object-name field-name value]
   ((cloudship->string-fn data-describe-client object-name field-name) value)))
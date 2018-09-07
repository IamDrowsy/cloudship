(ns cloudship.client.data.conversion
  (:require [ebenbild.core :refer [like]]
            [taoensso.timbre :as t]
            [clojure.string :as str]
            [cloudship.client.data.protocol :as p]
            [java-time.format :as jtf]
            [java-time.core :as jt]
            [java-time.zone :as jtz]
            [java-time.local :as jtl]
            [java-time.temporal :as jtt]
            [java-time.convert :as jtc]
            [com.rpl.specter :refer :all])
  (:import (java.util Base64 Base64$Decoder)))

(defn- find-field [obj-data fieldname]
  (first (filter (like {:name (re-pattern fieldname)})
                 (:fields obj-data))))

(defn- find-field-by-reference-name [objdata reference-name]
  (first (filter (like {:relationshipName reference-name})
                 (:fields objdata))))

(defn- cross-reference-field? [field-name]
  (str/includes? field-name "."))

(declare field-type)

(defn- reference-object-type-of-reference-field [data-describe-client source-object-name reference-field-name]
  (let [obj-data (first (p/describe-objects data-describe-client [source-object-name]))
        reference-field (find-field-by-reference-name obj-data reference-field-name)]
    (if-let [references (:referenceTo reference-field)]
      (if (< 1 (count references))
        (t/warn "field" reference-field-name "is polymorphic with" (:referenceTo reference-field) " defaulting to first")
        (first (:referenceTo reference-field)))
      (throw (ex-info (str "no field " reference-field-name " found, defaulting to string") {:describe-client data-describe-client
                                                                                             :source-object-name source-object-name
                                                                                             :reference-field-name reference-field-name})))))

(defn- field-type-of-cross-reference-field [data-describe-client source-object-data cross-reference-field-name]
  (let [parts (str/split cross-reference-field-name #"\.")
        reference-field (first parts)
        other (str/join "." (rest parts))
        reference-field (find-field-by-reference-name source-object-data reference-field)]
    (if-let [references (:referenceTo reference-field)]
      (do (if (< 1 (count references))
            (t/warn "field" cross-reference-field-name "is polymorphic with" (:referenceTo reference-field) " defaulting to first"))
          (field-type data-describe-client (first references) other))
      (do (t/warn "no field" cross-reference-field-name "found, defaulting to string") "string"))))

(defn field-type [data-describe-client object-name field-name]
  (if (= field-name "type")
    "string"
    (let [obj-data (first (p/describe-objects data-describe-client [object-name]))]
      (if (cross-reference-field? field-name)
        (field-type-of-cross-reference-field data-describe-client obj-data field-name)
        ;find-field-by-reference is needed here, because when we query Account.Name but there is no account attached we get back :Account nil
        (if-let [field (or (find-field obj-data field-name) (find-field-by-reference-name obj-data field-name)
                           ({"AggregateResult" "unknown"} object-name))]
          (:type field)
          (do (t/warn "no field" (str object-name "." field-name) "found, cannot coerce type (leaving it untouched).") "unknown"))))))

(defmulti string->cloudship-fn*
  "Returns a function that parses a given string into the cloudship type used for the given object and field (using the data-describe client)."
  (fn [field-type] (str/lower-case field-type)))

(defn string->cloudship-fn
  "Returns a function that parses a given string into the cloudship type used for the given type
   or a data-describe client, object-name and field-name."
  ([field-type]
   (let [parse-fn (string->cloudship-fn* field-type)]
     (fn [string]
       (when-not (or (nil? string) (and (string? string) (str/blank? string)))
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

(defn coerce-bool [s]
  ;only special case as 0 becomes false automaticly
  (if (= s "1")
    true
    (Boolean/parseBoolean s)))

(defmethod string->cloudship-fn* "unknown" [type] identity)
(defmethod string->cloudship-fn* "string" [type] identity)
(defmethod string->cloudship-fn* "email" [type] identity)
(defmethod string->cloudship-fn* "reference" [type] identity)
(defmethod string->cloudship-fn* "id" [type] identity)
(defmethod string->cloudship-fn* "int" [type] #(Integer/parseInt %))
(defmethod string->cloudship-fn* "picklist" [type] identity)
(defmethod string->cloudship-fn* "multipicklist" [type] identity)
(defmethod string->cloudship-fn* "combobox" [type] identity)
(defmethod string->cloudship-fn* "base64" [type] #(.decode ^Base64$Decoder (Base64/getDecoder) ^String %))
(defmethod string->cloudship-fn* "boolean" [type] coerce-bool)
(defmethod string->cloudship-fn* "currency" [type] #(Double/parseDouble %))
(defmethod string->cloudship-fn* "textarea" [type] identity)
(defmethod string->cloudship-fn* "double" [type]  #(Double/parseDouble %))
(defmethod string->cloudship-fn* "percent" [type] #(Double/parseDouble %))
(defmethod string->cloudship-fn* "phone" [type] identity)
(defmethod string->cloudship-fn* "date" [type] jtl/local-date)
(defmethod string->cloudship-fn* "datetime" [type] jtz/zoned-date-time)
(defmethod string->cloudship-fn* "encryptedstring" [type] identity)
(defmethod string->cloudship-fn* "datacategorygroupreference" [type] identity)
(defmethod string->cloudship-fn* "location" [type] identity)
(defmethod string->cloudship-fn* "address" [type] identity)
(defmethod string->cloudship-fn* "anytype" [type] identity)
(defmethod string->cloudship-fn* "complexvalue" [type] identity)
(defmethod string->cloudship-fn* "url" [type] identity)

(defmulti cloudship->string-fn*
  (fn [field-type] (str/lower-case field-type)))

(defmethod cloudship->string-fn* "string" [type] str)
(defmethod cloudship->string-fn* "email" [type] str)
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
(defmethod cloudship->string-fn* "datetime" [type] (fn [zoned-date-time] (jtf/format datetime-formatter (jtz/with-zone-same-instant zoned-date-time "Z"))))
(defmethod cloudship->string-fn* "encryptedstring" [type] str)
(defmethod cloudship->string-fn* "datacategorygroupreference" [type] str)
(defmethod cloudship->string-fn* "location" [type] str)
(defmethod cloudship->string-fn* "address" [type] str)
(defmethod cloudship->string-fn* "anytype" [type] str)
(defmethod cloudship->string-fn* "complexvalue" [type] str)
(defmethod cloudship->string-fn* "url" [type] str)

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

(defn string->cloudship-cast-map [data-describe-client object-name keywords]
  (zipmap keywords (map (fn [k] (string->cloudship-fn data-describe-client object-name k)) (map name keywords))))

(defn cloudship->string-cast-map [data-describe-client object-name keywords]
  (zipmap keywords (map (fn [k] (cloudship->string-fn data-describe-client object-name k)) (map name keywords))))


; Flatten nested maps and nest dotted keywords
(declare nest-map)

(defn group-in-maps-by-prefix [m]
  (reduce (fn [result [k v]]
            (let [parts (str/split (name k) #"\.")
                  prefix (if (= 1 (count parts)) nil (first parts))
                  path (if (= 1 (count parts)) parts (rest parts))]
              (setval [(keypath prefix) (nil->val {}) (keyword (str/join "." path))] v
                      result)))
          {}
          m))

(defn- inner-map [describe-client outer-object [ref-field submap]]
  (if (nil? ref-field)
    (assoc submap :type outer-object)
    (let [inner-object (reference-object-type-of-reference-field describe-client outer-object ref-field)]
      {(keyword ref-field) (nest-map describe-client inner-object submap)})))

(defn nest-map
  "Expands all Keywords containing dots into nested maps.
   Needs a :type of the outer map, adds type to all inner maps by checking the reference type of the keys"
  ([describe-client m]
   (nest-map describe-client (:type m) m))
  ([describe-client outer-type m]
   (when-not (every? nil? (vals m))
     (let [grouped-by-nesting (group-in-maps-by-prefix m)]
       (apply merge (map (partial inner-map describe-client outer-type) grouped-by-nesting))))))

(declare flatten-map)

(defn- add-prefix-to-keys [prefix m]
  (reduce (fn [m [k v]]
            (if (and prefix (= k :type))
              m ;skip prefixed type
              (assoc m (keyword (str prefix "." (name k))) v)))
          {}
          m))

(defn- flatten-key-val
  [[k v]]
  (if (map? v)
    (add-prefix-to-keys (name k) (flatten-map v))
    {k v}))

(defn flatten-map
  [m]
  (reduce (fn [m kv]
            (merge m (flatten-key-val kv)))
          {}
          m))
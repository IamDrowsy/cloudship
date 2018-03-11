(ns ^{:doc "Internal namespace to transform types from cloudship to the salesforce java sdk."}
  cloudship.client.sf-sdk.data.type-conversion
  (:require [taoensso.timbre :as t]
            [clojure.string :as str]
            [cloudship.client.type-conversion :as type])
  (:import [java.util Date GregorianCalendar]
           (java.time LocalDate Instant ZoneId ZonedDateTime)))

(defmulti cloudship->sdk-client*
  "Multimethod to cast not correctly typed entries to the needed sf values"
  (fn [field-type value] [field-type (type value)])
  :default ["string" String])

(defn cloudship->sdk-client
  [field-type value]
  (if (or (nil? value) (and (string? value) (str/blank? value)))
    nil
    (cloudship->sdk-client* field-type value)))

(defmacro def-cloudship->sdk-client* [sf-type clj-type f]
  `(defmethod cloudship->sdk-client* [~sf-type ~clj-type]
     [field-type# val#] (~f val#)))

(defn local-date->date [^LocalDate local-date]
  (Date/from
    (.toInstant (.atStartOfDay local-date (ZoneId/of "Z")))))

(defn instant->calendar [^Instant instant]
  (GregorianCalendar/from (ZonedDateTime/ofInstant instant (ZoneId/of "Z"))))

(def-cloudship->sdk-client* "string" String identity)
(def-cloudship->sdk-client* "date" String #(local-date->date (type/string->cloudship "date" %)))
(def-cloudship->sdk-client* "datetime" String #(instant->calendar (type/string->cloudship "datetime" %)))
(def-cloudship->sdk-client* "date" LocalDate local-date->date)
(def-cloudship->sdk-client* "datetime" Instant instant->calendar)
(def-cloudship->sdk-client* "int" String #(Integer/valueOf ^String %))
(def-cloudship->sdk-client* "int" Long (fnil #(int %) nil))
(def-cloudship->sdk-client* "boolean" String #(Boolean/valueOf ^String %))
(def-cloudship->sdk-client* "base64" String #(.getBytes %))
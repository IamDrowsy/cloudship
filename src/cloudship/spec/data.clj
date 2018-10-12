(ns ^:no-doc cloudship.spec.data
  (:require [clojure.spec.alpha :as s]))

(s/def ::Id string?)
(s/def ::type string?)

(s/def ::sObject (s/and (s/keys :req-un [::type] :opt-un [::type])
                        (s/map-of keyword? any?)))


(s/def ::message string?)
(s/def ::statusCode string?)
(s/def ::field string?)
(s/def ::fields (s/coll-of ::field))

(s/def ::error (s/keys :req-un [::message ::statusCode ::fields]))
(s/def ::errors (s/coll-of ::error))
(s/def ::success boolean?)

(s/def ::result (s/keys :req-un [::errors ::success]))
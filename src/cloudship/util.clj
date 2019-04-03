(ns cloudship.util
  (:require [cloudship.data :as data]))

(defn org-id [cloudship]
  (:Id (first (data/q cloudship "Organization" [:Id]))))
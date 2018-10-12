(ns ^:no-doc cloudship.util.java-data-extension
  (:require [clojure.java.data :as jd]))

(defmethod jd/from-java Boolean [bool]
  (when-not (nil? bool)
    (.booleanValue bool)))
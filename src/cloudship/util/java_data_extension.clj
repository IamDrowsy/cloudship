(ns cloudship.util.java-data-extension
  (:require [clojure.java.data :as jd]))

(defmethod jd/from-java Boolean [bool]
  (if (nil? bool) nil (.booleanValue bool)))
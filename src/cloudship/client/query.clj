(ns cloudship.client.query
  (:require [instaparse.core :as insta]
            [clojure.java.data :as jd])
  (:import (org.mule.tools.soql SOQLParserHelper)
           (org.mule.tools.soql.query SOQLQuery)))

(defn parse-soql-query [query-string]
  (jd/from-java (SOQLParserHelper/createSOQLData query-string)))
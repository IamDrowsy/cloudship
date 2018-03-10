(ns cloudship.core
  (:require [cloudship.client.sf-sdk.data.describe :as describe]
            [cloudship.client.sf-sdk.data.init :as init]
            [cloudship.connection.props.core :as props]
            [cloudship.data :as data]
            [ebenbild.core :refer [like]]))



(defn foo
  "I don't do a whole lot."
  [x]
  (println x "Hello, World!"))

(defn get-p [] (init/->partner-connection (props/->props :absc)))
(ns cloudship.connection.props.flags.sfdx
  (:require [clojure.spec.alpha :as s]
            [cloudship.util.sfdx :as sfdx]
            [cloudship.spec.config :as cs]))


(s/def ::prop-before-sfdx (s/keys :req-un [::cs/org] :opt-un [::cs/username]))

(defn- sfdx-result->login-props [{:keys [result]}]
  (let [{:keys [accessToken instanceUrl]} result]
       {:session accessToken
        :url instanceUrl}))

(defn- expand-sfdx-login-data [{:keys [org username] :as props}]
  (let [opts {:u (if username username (name org))}]
    (->> (sfdx/run-sfdx-command :force:org:display opts)
         (sfdx-result->login-props)
         (merge props))))

(defn resolve-sfdx-flag []
  expand-sfdx-login-data)
(ns cloudship.auth.sfdx
  (:require [clojure.spec.alpha :as s]
            [cloudship.util.sfdx :as sfdx]
            [cloudship.spec.config :as sc]
            [cloudship.auth.method :as am]
            [taoensso.timbre :as t]))

(defn- org-or-username? [{:keys [org username]}]
  (or org username))

(s/def ::prop-before-sfdx (s/and (s/keys :opt-un [:sc/org ::sc/username])
                                 org-or-username?))

(defn- session+url [{:keys [result]}]
  (let [{:keys [accessToken instanceUrl]} result]
    {:session accessToken
     :url instanceUrl}))

(defn- sfdx-auth [{:keys [org username]}]
  (let [opts {:u (if username username (name org))}]
    (t/info "Calling sfdx :force:org:display -u" (:u opts))
    (->> (sfdx/run-sfdx-command :force:org:display opts)
         (session+url))))

(defmethod am/auth :sfdx
  [config] (sfdx-auth config))



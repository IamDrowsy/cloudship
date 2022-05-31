(ns ^:no-doc cloudship.auth.sfdx
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
  (let [{:keys [accessToken instanceUrl username]} result]
    {:session accessToken
     :url instanceUrl
     :username username}))

(defn- sfdx-auth [{:keys [org username sandbox]}]
  (let [opts {:u (cond-> (if username username (name org))
                         sandbox (str "_" sandbox))}]
    (t/info "Calling sfdx force:org:display -u" (:u opts))
    (->> (sfdx/run-sfdx-command :force:org:display opts)
         (session+url))))

(defmethod am/auth :sfdx
  [config] (sfdx-auth config))



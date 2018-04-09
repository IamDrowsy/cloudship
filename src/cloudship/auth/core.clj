(ns cloudship.auth.core
  (:require [cloudship.auth.method :as am]
            [cloudship.auth.soap]
            [cloudship.auth.sfdx]
            [cloudship.spec.config :as sc]
            [clojure.spec.alpha :as s]))

(defn auth
  "Takes a preauth-config and returns a authed-config"
  [preauth-config]
  (merge (select-keys preauth-config [:url :cache-name :api-version :proxy])
         (am/auth preauth-config)))
(s/fdef auth
        :args (s/cat :preauth-config ::sc/preauth-config)
        :ret ::sc/authed-config)
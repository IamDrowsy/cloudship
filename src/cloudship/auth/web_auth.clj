(ns cloudship.auth.web-auth
  (:require [cloudship.auth.method :as am]
            [cloudship.spec.config :as sc]
            [cloudship.util.web-auth :as web-auth]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]))

(defn- run-web-auth [config]
  (web-auth/run-web-auth config))

(s/fdef run-web-auth
        :args (s/cat :config (s/merge ::sc/preauth-config
                                      (s/keys :req-un [::sc/url ::sc/session]))))

(defmethod am/auth :web
  [config] (merge config (run-web-auth config)))

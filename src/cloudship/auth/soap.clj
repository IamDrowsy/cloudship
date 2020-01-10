(ns ^:no-doc cloudship.auth.soap
  (:require [cloudship.auth.method :as am]
            [cloudship.client.impl.generic-xml.core :as generic]
            [cloudship.spec.config :as sc]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]))

(defn parse-url [server-url]
  (let [[p _ url services soap u api-version _] (str/split server-url #"/")]
    [(str "https://" url "/") api-version]))

; for now we are using the full partner connection.
(defn- session+url [config]
  (let [login-result (generic/login config)
        [url api-version] (parse-url (:serverUrl login-result))]
    {:session (:sessionId login-result)
     :url url}))

(defn- soap-auth [config]
  (session+url config))
(s/fdef soap-auth
        :args (s/cat :config (s/merge ::sc/preauth-config
                               (s/keys :req-un [::sc/url ::sc/api-version ::sc/username ::sc/password]))))

(defmethod am/auth :soap
  [config] (soap-auth config))

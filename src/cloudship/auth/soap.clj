(ns cloudship.auth.soap
  (:require [cloudship.auth.method :as am]
            [cloudship.client.impl.sf-sdk.data.init :as sdk]
            [cloudship.spec.config :as sc]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]))

(defn- ->base-url [url]
  (str/join "/" (take 3 (str/split url #"/"))))

; for now we are using the full partner connection.
(defn- session+url [config]
  (let [config (.getConfig (sdk/->partner-connection config))]
    {:session (.getSessionId config)
     :url (->base-url (.getServiceEndpoint config))}))

(defn- soap-auth [config]
  (session+url config))
(s/fdef soap-auth
        :args (s/cat :config (s/merge ::sc/preauth-config
                               (s/keys :req-un [::sc/url ::sc/api-version ::sc/username ::sc/password]))))

(defmethod am/auth :soap
  [config] (soap-auth config))

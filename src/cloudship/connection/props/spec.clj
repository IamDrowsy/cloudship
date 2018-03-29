(ns cloudship.connection.props.spec
  (:require [clojure.spec.alpha :as s]))

(s/def ::org string?)
(s/def ::sandbox string?)
(s/def ::full keyword?)
(s/def ::url string?)
(s/def ::username string?)
(s/def ::security-token string?)
(s/def ::password string?)
(s/def ::session string?)
(s/def ::my-domain string?)
(s/def ::instance string?)

(s/def ::host string?)
(s/def ::port int?)
(s/def ::proxy (s/keys :req-un [::host ::port]))

(s/def ::raw-api-version (s/or :int (s/and int? pos?)
                               :float (s/and float? pos?)
                               :string (s/and string? #(re-matches #"[\d]+(\.\d)?" %))))

(s/def ::api-version (s/and string? #(re-matches #"[\d]+(\.\d)" %)))

(defn login-data-or-session? [{:keys [username password session]}]
  (or session (and username password)))

(s/def ::final-props (s/and (s/keys :req-un [::url ::api-version]
                                    :opt-un [::username ::security-token ::password ::session ::proxy ::my-domain ::instance])
                            login-data-or-session?))
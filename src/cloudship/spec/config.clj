(ns ^:no-doc cloudship.spec.config
  (:require [clojure.spec.alpha :as s]))

(s/def ::org string?)
(s/def ::sandbox string?)
(s/def ::full keyword?)
(s/def ::url string?)
(s/def ::login-url string?)
(s/def ::username string?)
(s/def ::security-token string?)
(s/def ::password string?)
(s/def ::session string?)
(s/def ::my-domain string?)
(s/def ::instance string?)

(s/def ::host string?)
(s/def ::port int?)
(s/def ::proxy (s/or :explicite-false false?
                     :proxy-set (s/keys :req-un [::host ::port])))

(s/def ::raw-api-version (s/or :int (s/and int? pos?)
                               :float (s/and float? pos?)
                               :string (s/and string? #(re-matches #"[\d]+(\.\d)?" %))))

(s/def ::api-version (s/and string? #(re-matches #"[\d]+(\.\d)" %)))


(s/def ::auth-method #{:sfdx "sfdx" :web "web" :soap "soap" :pathom "pathom"})
(s/def ::preauth-config (s/and (s/keys :req-un [::auth-method]
                                       :opt-un [::proxy])))

(s/def ::authed-config (s/and (s/keys :req-un [::url ::api-version ::session]
                                      :opt-un [::proxy ::my-domain ::instance])))

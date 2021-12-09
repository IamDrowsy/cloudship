(ns cloudship.connection.props.flags.totp
  (:require [one-time.core :as ot]
            [com.rpl.specter :refer :all]
            [taoensso.timbre :refer [infof info]]))

(defn- add-totp-to-password [{:keys [password totp-secret] :as config}]
  (if (not password)
    (do
      (info "cannot decorate password with one-time token, because password is not present"))
    (if totp-secret
      (do
        (infof "decorating password with one-time token")
        (setval [:password END] (ot/get-totp-token totp-secret) config))
      (do
        (infof "no totp-secret present, skipping password decoration")
        config))))

(defn resolve-totp-flag []
  add-totp-to-password)

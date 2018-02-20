(ns cloudship.connection.props.flags.keepass
  (:require [cloudship.util.keepass :as kp]
            [clojure.spec.alpha :as s]
            [cloudship.util.spec :as u]
            [taoensso.timbre :refer [infof info]]))

(s/def ::kpdb string?)
(s/def ::kppath (s/coll-of string? :min-count 1))
(s/def ::kppass string?)

(s/def ::prop-before-kp (s/keys :req-un [::kpdb ::kppath]
                                :opt-un [::kppass]))

(defn- expand-keypass-login-data [{:keys [kpdb kppath kppass full] :as con-props}]
  (if (u/input-valid? ::prop-before-kp con-props)
    (do
      (infof "Expanding login-data for %s from keepass" full)
      (merge
        con-props
        (select-keys (kp/entry kpdb kppath kppass) [:username :password])))
    (do (info "Input not valid for keepass flag. Skipping it.")
        con-props)))

(defn resolve-kp-flag []
  expand-keypass-login-data)
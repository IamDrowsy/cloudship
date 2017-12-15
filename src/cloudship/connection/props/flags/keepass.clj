(ns cloudship.connection.props.flags.keepass
  (:require [cloudship.util.keepass :as kp]
            [clojure.spec.alpha :as s]
            [cloudship.util.spec :as u]
            [taoensso.timbre :refer [infof]]))

(s/def ::kpdb string?)
(s/def ::kppath (s/coll-of string? :min-count 1))
(s/def ::kppass string?)

(s/def ::prop-before-kp (s/keys :req-un [::kpdb ::kppath]
                                :opt-un [::kppass]))

(defn- expand-keypass-login-data [{:keys [kpdb kppath kppass full] :as con-props}]
  (u/assert-input ::prop-before-kp con-props)
  (infof "Expanding login-data for %s from keepass" full)
  (merge
    con-props
    (select-keys (kp/entry kpdb kppath kppass) [:username :password])))

(defn resolve-kp-flag []
  expand-keypass-login-data)
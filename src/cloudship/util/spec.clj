(ns cloudship.util.spec
  (:require [clojure.spec.alpha :as s]
            [cloudship.connection.props.spec :as cs]
            [expound.alpha :refer [expound-str]]))

(def secret-keys [::cs/password ::cs/kppass :password :kppass])

(defn obscure-key [m key]
  (if (key m)
    (assoc m key "*******")
    m))

(defn obscure-keys [m keys]
  (reduce obscure-key m keys))

(defn assert-input [spec input]
  (if (not (s/valid? spec input))
    (throw (ex-info (expound-str spec (obscure-keys input secret-keys))
                    {:spec spec
                     :input (obscure-keys input secret-keys)}))
    input))


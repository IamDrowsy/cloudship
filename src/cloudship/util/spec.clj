(ns cloudship.util.spec
  (:require [clojure.spec.alpha :as s]
            [cloudship.spec.config :as sc]
            [expound.alpha :refer [expound-str]]
            [taoensso.timbre :as timbre]))

(def secret-keys [::sc/password ::sc/kppass :password :kppass])

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

(defn input-valid? [spec input]
  (if (not (s/valid? spec input))
    (do (timbre/debug (expound-str spec (obscure-keys input secret-keys)))
        false)
    true))
(ns cloudship.auth.method
  (:require [cloudship.spec.config :as sc]))

(defmulti auth
  "Takes a config and returns a authed config"
  (comp keyword :auth-method))
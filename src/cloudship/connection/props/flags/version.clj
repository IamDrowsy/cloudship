(ns ^:no-doc cloudship.connection.props.flags.version
  (:require [clojure.string :as str]
            [cloudship.util.spec :as u]
            [cloudship.spec.config :as sc]))

(defn coerce-api-version [api-version]
  (u/assert-input ::sc/raw-api-version api-version)
  (cond (int? api-version)
        (str api-version ".0")
        (float? api-version)
        (str api-version)
        (and (string? api-version) (str/includes? api-version "."))
        api-version
        :else
        (str api-version ".0")))

(defn resolve-version-flag [{:keys [opt]}]
  (fn [props]
    (assoc props :api-version (coerce-api-version opt))))
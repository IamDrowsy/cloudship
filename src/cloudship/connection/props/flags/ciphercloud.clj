(ns cloudship.connection.props.flags.ciphercloud
  (:require [clojure.string :as str]
            [clojure.spec.alpha :as s]
            [cloudship.connection.props.spec :as prop-spec]
            [cloudship.util.spec :as u]))

(s/def ::cc-alias string?)
(s/def ::cc-domain string?)

(defn- sandbox-or-cc-alias? [{:keys [sandbox cc-alias]}]
  (or sandbox cc-alias))

(s/def ::prop-before-cc (s/and (s/keys :req-un [::cc-domain ::prop-spec/url]
                                       :opt-un [::cc-alias ::prop-spec/sandbox])
                               sandbox-or-cc-alias?))

(defn- change-to-cc-url
  [cc-domain cc-alias sandbox url]
  (let [alias (or cc-alias sandbox)]
    (str alias
         "-"
         (str/replace (str/replace url #"-" "--") #"\." "-")
         (str "." cc-domain))))

(defn resolve-cc-flag []
  (fn [{:keys [cc-domain cc-alias sandbox] :as con-props}]
    (u/assert-input ::prop-before-cc con-props)
    (update con-props :url (partial change-to-cc-url cc-domain cc-alias sandbox))))
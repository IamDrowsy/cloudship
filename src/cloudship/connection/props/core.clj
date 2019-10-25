(ns cloudship.connection.props.core
  (:require [cloudship.connection.props.keyword :as kw]
            [cloudship.connection.props.flags.version :as v]
            [clojure.spec.alpha :as s]
            [cloudship.spec.config :as config-spec]
            [cloudship.util.spec :as u]
            [cloudship.connection.props.flags :as flags]
            [cloudship.connection.props.proxy :as proxy]
            [clojure.string :as str]
            [cloudship.connection.props.load :as l]))

(def default-api-version "47.0")

(defn- +api-version [props]
  (if (:api-version props)
    (update props :api-version v/coerce-api-version)
    (assoc props :api-version default-api-version)))

(defn- instance-when-my-domain-and-sandbox [{:keys [instance my-domain sandbox]}]
  (if (and sandbox my-domain)
    (not (nil? instance))
    true))

(s/def ::props-before-url (s/and
                            (s/keys :opt-un [::config-spec/sandbox ::config-spec/my-domain
                                             ::config-spec/url ::config-spec/instance])
                            instance-when-my-domain-and-sandbox))

(defn- base-url [my-domain sandbox instance]
  (cond (and sandbox my-domain instance)
        (str my-domain "--" sandbox ".cs" instance
             ".my.salesforce.com")
        (and sandbox my-domain)
        (str my-domain "--" sandbox ".my.salesforce.com")
        sandbox
        (str "test.salesforce.com")
        my-domain
        (str my-domain ".my.salesforce.com")
        :else
        "login.salesforce.com"))

(defn- +url [{:keys [sandbox my-domain url instance] :as props}]
  ; we don't want to assert here, because the sfdx flags doesn't need this. (and will provide url later)
  ; also, because we check the final result later.
  ; TODO We might want to make an url flag or some other mechanism to get this assertion back.
  #_
  (u/assert-input ::props-before-url props)
  (if url
    props
    (assoc props :url (base-url my-domain sandbox instance))))

(defn- +flags [props]
  (flags/expand-props props))

(defn- +protocol [{:keys [url] :as props}]
  (if (str/starts-with? url "https://")
    props
    (assoc props :url (str "https://" url))))

(defn- +sandbox-username-extension [{:keys [username sandbox] :as props}]
  (if (and sandbox
          (not (clojure.string/ends-with? username sandbox)))
    (-> props
        (assoc :base-username username)
        (update :username #(str % "." (name sandbox))))
    props))

(defn- +proxy [{:keys [url proxy] :as props}]
  (if (nil? proxy)
    (if-let [default-proxy (proxy/find-default-proxy url)]
      (assoc props :proxy default-proxy)
      props)
    props))


(defn ->props [kw-or-map]
  (let [props (cond (keyword? kw-or-map) #_=> (l/find-and-merge-props (kw/kw->props kw-or-map))
                    (map? kw-or-map)     #_=> (l/find-and-merge-props kw-or-map))]
    (u/assert-input ::config-spec/preauth-config
                    (-> props
                        +api-version
                        +url
                        +flags
                        +protocol
                        +proxy
                        +sandbox-username-extension))))


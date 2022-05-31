(ns cloudship.connection.props.pathom
  (:require [com.wsscode.pathom3.connect.operation :as pco]
            [com.wsscode.pathom3.interface.eql :as p.eql]
            [com.wsscode.pathom3.plugin :as p.plugin]
            [com.wsscode.pathom3.connect.indexes :as pci]
            [cloudship.auth.method :as am]
            [cloudship.connection.props.proxy :as proxy]
            [cloudship.connection.props.flags.windp :as windp]
            [cloudship.util.keepass :as kp]
            [clojure.string :as str]
            [taoensso.timbre :as t]
            [clojure.pprint :as pp]))

(pco/defresolver connection-by-props [config]
  {::pco/input    [:login-url :api-version :username :password (pco/? :proxy)]
   ::pco/output   [:session :url]
   ::pco/priority 10}
  (am/auth (assoc config :auth-method :soap)))

(pco/defresolver connection-by-sfdx-alias [config]
  {::pco/input [:org (pco/? :sandbox)]
   ::pco/output [:session :url :username]
   ::pco/priority 6}
  (am/auth (assoc config :auth-method :sfdx)))

(pco/defresolver connection-by-sfdx-username [{:keys [username] :as config}]
  {::pco/output [:session :url]
   ::pco/priority 5}
  (am/auth (assoc config :auth-method :sfdx)))

(pco/defresolver connection-by-web-auth [{:keys [username login-url] :as config}]
  {::pco/output [:session :url]
   ::pco/priority 0}
  (am/auth (assoc config :auth-method :web)))

(pco/defresolver api-version-resolver []
  {:api-version "53.0"})

(pco/defresolver proxy-resolver [{:keys [login-url]}]
  {:proxy (proxy/find-default-proxy login-url)})

(pco/defresolver keypass-resolver [{:keys [kpdb kppath kppass]}]
  {::pco/output [:base-username :password]}
  (kp/entry kpdb kppath kppass))

(pco/defresolver winddp-resolver [config]
  {::pco/input  [(pco/? :password.enc) (pco/? :kppass.enc)]
   ::pco/output [:password :kppass]}
  (cond-> {}
          (:password.enc config) (assoc :password (windp/decrypt (:password.enc config)))
          (:kppass.enc config) (assoc :kppass (windp/decrypt (:kppass.enc config)))))

(pco/defresolver default-url-resolver [config]
  {::pco/input [(pco/? :sandbox)]
   ::pco/output [:login-url]
   ::pco/priority 0}
  {:login-url (if (:sandbox config)
                "https://test.salesforce.com" "https://login.salesforce.com")})

(pco/defresolver username-resolver [{:keys [base-username sandbox]}]
  {::pco/input [:base-username (pco/? :sandbox)]
   ::pco/output [:username]}
  {:username (if (and sandbox
                      (not (clojure.string/ends-with? base-username sandbox)))
               (str base-username "." sandbox)
               base-username)})

(pco/defresolver my-domain-url-resolver [{:keys [my-domain sandbox instance]}]
  {::pco/input  [:my-domain (pco/? :sandbox) (pco/? :instance)]
   ::pco/output [:login-url]
   ::pco/priority 20}
  {:login-url  (cond-> (str "https://" my-domain)
                       sandbox (str "--" sandbox)
                       instance (str ".cs" instance)
                       true (str ".my.salesforce.com"))})

(def resolver [connection-by-props connection-by-sfdx-alias connection-by-sfdx-username #_connection-by-web-auth
               api-version-resolver proxy-resolver keypass-resolver winddp-resolver default-url-resolver my-domain-url-resolver username-resolver])

(def env (-> (pci/register resolver)
             (p.plugin/register
               {::p.plugin/id 'err
                :com.wsscode.pathom3.connect.runner/wrap-resolver-error
                (fn [_]
                  (fn [env node error]
                    (println "Error: " (ex-message error))))})))

(defn auth-with-pathom
  [props]
  (let [result (p.eql/process env props [:username :session :url :api-version :proxy])]
    (t/info "Pathom resolved to " (with-out-str (pp/pprint (select-keys result [:username :url :api-version :proxy]))))
    result))

(defmethod am/auth :pathom
  [config] (merge config (auth-with-pathom config)))
(ns cloudship.connection.props.pathom
  (:require [com.wsscode.pathom3.connect.operation :as pco]
            [com.wsscode.pathom3.interface.eql :as p.eql]
            [com.wsscode.pathom3.connect.indexes :as pci]
            #_[com.wsscode.pathom.viz.ws-connector.core :as pvc]
            #_[com.wsscode.pathom.viz.ws-connector.pathom3 :as p.connector]
            [cloudship.auth.method :as am]
            [cloudship.connection.props.proxy :as proxy]
            [cloudship.connection.props.core :as props]
            [cloudship.connection.props.flags.windp :as windp]
            [cloudship.util.keepass :as kp]))

(defn catched-sfdx-auth [config]
  (try (am/auth (assoc config :auth-method :sfdx))
       (catch Exception e
         (if (= "NamedOrgNotFound" (:name (:out (ex-data e))))
           {}
           (throw e)))))

(pco/defresolver connection-by-props [config]
  {::pco/input    [:login-url :api-version :username :password (pco/? :proxy)]
   ::pco/output   [:session :url]
   ::pco/priority 10}
  (am/auth (assoc config :auth-method :soap)))

(pco/defresolver connection-by-sfdx-alias [{:keys [org] :as config}]
  {::pco/output [:session :url]
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
  {::pco/output [:username :password]}
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

#_(pco/defresolver my-domain-url-resolver [{:keys [my-domain sandbox instance]}]
    {::pco/input  [:my-domain (:pco/? :sandbox) (:pco/? :instance)]
     ::pco/output [:login-url]
     ::pco/priority 20}
    {:url "Blub"})

(def resolver [connection-by-props connection-by-sfdx-alias connection-by-sfdx-username connection-by-web-auth
               api-version-resolver proxy-resolver keypass-resolver winddp-resolver default-url-resolver])

(def env (pci/register resolver))

(defn auth-with-pathom
  [keyword]
  (p.eql/process env (props/->props keyword) [:session :url :api-version]))
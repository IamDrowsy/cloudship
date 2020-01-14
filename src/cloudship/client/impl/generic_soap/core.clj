(ns cloudship.client.impl.generic-soap.core
  (:require [cloudship.client.data.protocol :refer [DataDescribeClient]]
            [com.rpl.specter :as s]
            [ebenbild.core :as e]
            [clj-http.client :as http]
            [clojure.data.xml :as xml]
            [cloudship.client.impl.generic-soap.convert :as c]
            [clojure.string :as str]))

(def alias-uris
  {'xsd "http://www.w3.org/2001/XMLSchema"
   'xsi "http://www.w3.org/2001/XMLSchema-instance"
   'env "http://schemas.xmlsoap.org/soap/envelope/"
   'partner "urn:partner.soap.sforce.com"
   'tooling "urn:tooling.soap.sforce.com"})

(defrecord SoapClient [base-url api-version session])

(doseq [[k v] alias-uris]
  (xml/alias-uri k v))

(def apis
  {:meta    {:url-part  "m"
             :namespace 'metadata}
   :data    {:url-part  "u"
             :namespace 'partner}
   :tooling {:url-part  "T"
             :namespace 'tooling}})

(defn in-api-ns [key api]
  (str "{" (get alias-uris (:namespace (apis api))) "}" (name key)))


(defn add-envelop [body header]
  {:tag ::env/Envelope
   :content [{:tag ::env/Header
              :content header}
             {:tag ::env/Body
              :content body}]})

(defn strip-envelop [envelop]
  ; for now we strip the response header as we don't need it
  (s/select-one [:content s/ALL (e/like {:tag :Body}) :content s/FIRST] envelop))

(defn send-soap*
  ([target action body]
   (send-soap* target action body {}))
  ([target action body soap-headers]
   (-> (http/post target
                  {:body    (xml/emit-str (add-envelop body soap-headers))
                   :headers {"content-type" "text/xml"
                             "SOAPAction"   action}})
       (:body)
       (xml/parse-str)
       (strip-envelop)
       (c/xml->map)
       second
       (:result))))

(defn- add-service-part [api-version base-url api]
  (str base-url "/services/Soap/" (:url-part (apis api)) "/" api-version "/"))

(defn- has-no-service-part? [url]
  (not (str/includes? url "services/Soap/")))

(defn- add-soap-service-part-if-missing [api-version url api]
  (if (has-no-service-part? url)
    (add-service-part api-version url api)
    url))

(defn- ->soap-url [api-version base-url api]
  (add-soap-service-part-if-missing api-version base-url api))

(defn send-soap
  ([client action]
   (send-soap client action []))
  ([client action body]
   (send-soap client action body :data))
  ([client action body api]
   (let [target (->soap-url (:api-version client) (:base-url client) api)]
     (send-soap* target (name action)
                 {:tag     (in-api-ns action api)
                  :content body}
                 {:tag     (in-api-ns :SessionHeader api)
                  :content {:tag     (in-api-ns :sessionId api)
                            :content (:session client)}}))))

(defn login [{:keys [url username password api-version]}]
  (first (send-soap* (->soap-url api-version url :data)
                     "login"
                     {:tag ::partner/login
                      :content [{:tag :username
                                 :content username}
                                {:tag :password
                                 :content password}]})))
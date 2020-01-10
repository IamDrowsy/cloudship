(ns cloudship.client.impl.generic-xml.core
  (:require [cloudship.client.data.protocol :refer [DataDescribeClient]]
            [com.rpl.specter :as s]
            [ebenbild.core :as e]
            [clj-http.client :as http]
            [clojure.data.xml :as xml]
            [cloudship.client.impl.generic-xml.convert :as c]
            [clojure.string :as str]))

(def alias-uris
  {'xsd "http://www.w3.org/2001/XMLSchema"
   'xsi "http://www.w3.org/2001/XMLSchema-instance"
   'env "http://schemas.xmlsoap.org/soap/envelope/"
   'partner "urn:partner.soap.sforce.com"})

(defrecord SoapClient [base-url api-version session])

(doseq [[k v] alias-uris]
  (xml/alias-uri k v))

(defn in-partner-ns [key]
  (str "{" (get alias-uris 'partner) "}" (name key)))

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

(defn- add-service-part [api-version base-url]
  (str base-url "/services/Soap/u/" api-version "/"))

(defn- has-no-service-part? [url]
  (not (str/includes? url "services/Soap/u")))

(defn- add-soap-service-part-if-missing [api-version url]
  (if (has-no-service-part? url)
    (add-service-part api-version url)
    url))

(defn- ->soap-url [api-version base-url]
  (add-soap-service-part-if-missing api-version base-url))

(defn send-soap
  ([client action]
   (send-soap client action []))
  ([client action body]
   (let [target (->soap-url (:api-version client) (:base-url client))]
     (send-soap* target (name action)
                 {:tag     (in-partner-ns action)
                  :content body}
                 {:tag     ::partner/SessionHeader
                  :content {:tag     ::partner/sessionId
                            :content (:session client)}}))))

(defn login [{:keys [url username password api-version]}]
  (first (send-soap* (->soap-url api-version url)
                     "login"
                     {:tag ::partner/login
                      :content [{:tag :username
                                 :content username}
                                {:tag :password
                                 :content password}]})))


(defn describe-global [client]
  (send-soap client :describeGlobal))

(defn describe-sobjects* [client sobject-names]
  (send-soap client :describeSObjects (mapv (fn [name]
                                              {:tag (in-partner-ns :sObjectType)
                                               :content [name]})
                                            sobject-names)))

(defn describe-sobjects [client sobject-names]
  (mapcat (partial describe-sobjects* client) (partition-all 100 sobject-names)))

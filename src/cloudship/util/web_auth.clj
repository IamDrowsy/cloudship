(ns ^:no-doc cloudship.util.web-auth
  (:require [clojure.string :as str]
            [com.rpl.specter :refer :all]
            [clj-http.util :as u]
            [clj-http.client :as http]
            [cheshire.core :as json]
            [ring.middleware.params :as params]
            [org.httpkit.server :as server]
            [taoensso.timbre :as t])
  (:import (java.util.concurrent ArrayBlockingQueue TimeUnit)
           (java.awt Desktop)
           (java.net URI)))

(defn check-and-parse-callback [current-url options]
  (when (and (str/starts-with? current-url (:callback-url options))
             (str/includes? current-url "#"))
    (as-> current-url u
          (str/split u #"#")
          (second u)
          (str/split u #"&")
          (map #(str/split % #"=") u)
          (into {} u)
          (transform [MAP-KEYS] keyword u))))

(defonce server (atom nil))
; holds all running request, keys are
; :options, -> to original option map
; :callback-queue, -> the queue for writing the final answer
; :state -> one of #{:open, :got-code, :done}
;  key is :cache-name or "default"
(defonce open-requests (atom {}))

(defn- queue-for-request ^ArrayBlockingQueue [request-key]
  (:callback-queue (@open-requests request-key)))

(defn- state-for-request [request-key]
  (:state (@open-requests request-key)))

(defn- options-for-request [request-key]
  (:options (@open-requests request-key)))

(defn- auth-url [base-url]
  (str base-url "/services/oauth2/authorize"))

(defn- token-url [base-url]
  (str base-url "/services/oauth2/token"))

(defn complete-web-server-flow [request-key code]
  (let [{:keys [base-url consumer-key callback-url consumer-secret]} (options-for-request request-key)
        optional (if consumer-secret {:client_secret consumer-secret} {})
        auth-result (http/post (token-url base-url)
                               {:form-params (merge {:grant_type   "authorization_code"
                                                     :code         code
                                                     :client_id    consumer-key
                                                     :redirect_uri callback-url}
                                                    optional)})]
    (json/parse-string (:body auth-result) true)))

(defn- handle-code-callback [request-key params]
  (let [code (get params "code")]
    (swap! open-requests assoc-in [request-key :state] :got-code)
    (.put (queue-for-request request-key) (complete-web-server-flow request-key code))))

(defn- handle-request [req]
  (let [params (:params (params/params-request req))
        request-key (params "state")]
    (cond (= (state-for-request request-key) :open)
          (handle-code-callback request-key params)
          :else
          {:status 200
           :headers {"Content-Type" "text/html"}
           :body "Success"})))

(defn- stop-server []
  (when-not (nil? @server)
    (@server :timeout 100)
    (reset! server nil)))

(defn- start-server [options]
  (when (nil? @server)
    (reset! server (server/run-server #'handle-request {:port (:callback-port options 8080)}))))

(def default-options
  {:consumer-key "3MVG98_Psg5cppyZCaw1PcLjYvU32k1LsZ8ZU76RP_vq5uycVvzL05cYgeQJ97bFsynhV94lXqWbc2Xdbzsex"
   :url "https://login.salesforce.com"
   :callback-url "http://localhost"
   :response-type "code"
   :callback-port 8090
   :callback-timeout 60})

(defn- build-url [request-key options]
  (let [url
        (str (auth-url (:url options))
             "?response_type=" (u/url-encode (:response-type options))
             "&client_id=" (u/url-encode (:consumer-key options))
             "&state=" (u/url-encode request-key)
             ; we don't need the url here, as salesforce always redirects to the port configured in the connected app
             "&redirect_uri=" (u/url-encode (:callback-url options)))]
    url))

(defn- open-auth-url [request-key options]
  (.browse (Desktop/getDesktop) (URI. (build-url request-key options))))

(defn- await-callback [request-key options]
  (let [answer (.poll (queue-for-request request-key) (:callback-timeout options) TimeUnit/SECONDS)]
    (if (nil? answer)
      (throw (ex-info "Requested Auth timed out." {}))
      answer)))

(defn- start-and-await-browser-callback [request-key options]
  (start-server options)
  (open-auth-url request-key options)
  (let [result (await-callback request-key options)
        remaining-requests (swap! open-requests dissoc request-key)]
    (if (empty? remaining-requests)
      (stop-server))
    result))

(defn- init-request-state [request-key options]
  (swap! open-requests merge {request-key {:options options
                                           :callback-queue (ArrayBlockingQueue. 1)
                                           :state :open}}))

(defn run-web-auth [options]
  (let [full-options (merge default-options options)
        request-key (str (:cache-name options "default"))
        _ (init-request-state request-key full-options)
        result (start-and-await-browser-callback request-key full-options)]
    {:session (:access_token result)
     :url (:instance_url result)
     :refresh-token  (or (:refresh_token result) "")}))
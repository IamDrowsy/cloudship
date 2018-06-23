(ns cloudship.util.web-auth
  (:require [clojure.string :as str]
            [com.rpl.specter :refer :all]
            [clj-http.util :as u])
  (:import (org.eclipse.swt.browser Browser)
           (org.eclipse.swt.widgets Display Shell)
           (org.eclipse.swt SWT)
           (org.eclipse.swt.layout FillLayout)))

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

(defn gui-loop [browser display shell options]
  (loop []
    (if (.isDisposed shell)
      (.dispose display)
      (do
        (if-not (.readAndDispatch display)
          (.sleep display))
        (if-let [result (check-and-parse-callback (.getUrl browser) options)]
          (do (.dispose display)
              result)
          (recur))))))

(def default-options
  {:consumer-key "3MVG98_Psg5cppybVAyG0nED1FctTXymm0nXYgOZxeI2hSj3dKRhO6w3rtDgWchoNsOeu.4EzrezJRbJyW58C"
   :base-url "https://login.salesforce.com"
   :callback-url "http://localhost"
   :response-type "token"})

(defn build-url [options]
  (let [url
        (str (str (:base-url options) "/services/oauth2/authorize")
             "?response_type=" (u/url-encode (:response-type options))
             "&client_id=" (u/url-encode (:consumer-key options))
             "&redirect_uri=" (u/url-encode (:callback-url options)))]
    url))

(defn show-browser [options]
  (let [display (Display.)
        shell (Shell. display)
        layout (FillLayout. SWT/VERTICAL)
        browser (Browser. shell, SWT/NONE)]
    (.setUrl browser (build-url options))
    (doto shell
      (.setText "cloudship Web Auth")
      (.setLayout layout)
      (.setSize 640 800)
      (.pack)
      (.open))
    (gui-loop browser display shell options)))

(defn run-web-auth [options]
  (let [full-options (merge default-options options)
        result (show-browser full-options)]
    {:session (u/url-decode (:access_token result))
     :url (u/url-decode (:instance_url result))
     :refresh-token (u/url-decode (or (:refresh_token result) ""))}))
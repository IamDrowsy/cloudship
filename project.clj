(defproject cloudship "0.1.0-SNAPSHOT"
  :description "cloudship is a clojure toolkit to explore and manipulate your salesforce instances"
  :url "https://github.com/IamDrowsy/cloudship"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [instaparse "1.4.8"]
                 [com.taoensso/timbre "4.10.0"]
                 [expound "0.3.4"]
                 [de.slackspace/openkeepass "0.6.1"]]

  :profiles {:dev {:resource-paths ["resources-test"]
                   :dependencies [[org.clojure/test.check "0.10.0-alpha2"]]}})

(defproject cloudship "0.1.0-SNAPSHOT"
  :description "cloudship is a clojure toolkit to explore and manipulate your salesforce instances"
  :url "https://github.com/IamDrowsy/cloudship"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [instaparse "1.4.8"]
                 [semantic-csv "0.2.1-alpha1"]
                 [com.taoensso/timbre "4.10.0"]
                 [expound "0.3.4"]
                 [de.slackspace/openkeepass "0.6.1"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/java.data "0.1.1"]
                 [clojure.java-time "0.3.1"]
                 [com.taoensso/nippy "2.14.0"]
                 [ebenbild "0.1.1"]
                 [com.rpl/specter "1.0.5"]
                 [com.force.api/force-partner-api "42.0.0"]
                 [com.force.api/force-wsc "42.0.0"]
                 [com.force.api/force-metadata-api "42.0.0"]]

  :profiles {:dev {:resource-paths ["resources-test"]
                   :dependencies [[org.clojure/test.check "0.10.0-alpha2"]
                                  [orchestra "2017.11.12-1"]]}})

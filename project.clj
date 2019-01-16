(defproject cloudship "0.1.2-SNAPSHOT"
  :description "cloudship is a clojure toolkit to explore and manipulate your salesforce instances"
  :url "https://github.com/IamDrowsy/cloudship"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [instaparse "1.4.9"]
                 [semantic-csv "0.2.1-alpha1"]
                 [com.taoensso/timbre "4.10.0"]
                 [expound "0.7.1"]
                 [de.slackspace/openkeepass "0.8.2"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/java.data "0.1.1"]
                 [clojure.java-time "0.3.2"]
                 [org.clojure/core.cache "0.7.1"]
                 [com.taoensso/nippy "2.14.0"]
                 [http-kit "2.3.0"]
                 [ring "1.7.0"]
                 [cheshire "5.8.1"]
                 [ebenbild "0.1.1"]
                 [com.rpl/specter "1.1.1"]
                 [clj-http "3.9.1"]
                 [com.force.api/force-partner-api "44.0.0"]
                 [com.force.api/force-wsc "44.0.0"]
                 [com.force.api/force-metadata-api "44.0.0"]]

  :profiles {:dev {:resource-paths ["resources-test"]
                   :dependencies [[org.clojure/test.check "0.10.0-alpha2"]
                                  [orchestra "2018.09.10-1"]
                                  [criterium "0.4.4"]]}})

(defproject cloudship "0.2.2-SNAPSHOT"
  :description "cloudship is a clojure toolkit to explore and manipulate your salesforce instances"
  :url "https://github.com/IamDrowsy/cloudship"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [instaparse "1.4.10"]
                 [semantic-csv "0.2.1-alpha1" :exclusions [[org.clojure/clojurescript]]]
                 [com.taoensso/timbre "4.10.0"]
                 [expound "0.7.2"]
                 [de.slackspace/openkeepass "0.8.2"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/java.data "0.1.4"]
                 [clojure.java-time "0.3.2"]
                 [org.clojure/core.cache "0.8.2"]
                 [com.taoensso/nippy "2.14.0"]
                 [http-kit "2.3.0"]
                 [ring "1.7.1"]
                 [cheshire "5.9.0"]
                 [ebenbild "0.2.0"]
                 [com.rpl/specter "1.1.3"]
                 [clj-http "3.10.0"]
                 [com.force.api/force-partner-api "47.0.0"]
                 [com.force.api/force-wsc "47.0.0"]
                 [com.force.api/force-metadata-api "47.0.0"]]

  :profiles {:dev {:resource-paths ["resources-test"]
                   :dependencies [[org.clojure/test.check "0.10.0"]
                                  [orchestra "2019.02.06-1"]
                                  [criterium "0.4.5"]]}})

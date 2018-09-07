(defproject cloudship "0.0.2"
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
                 [org.clojure/core.cache "0.6.5"]
                 [org.mule.tools/salesforce-soql-parser "2.0"]
                 [com.taoensso/nippy "2.14.0"]
                 [ebenbild "0.1.1"]
                 [com.rpl/specter "1.1.0"]
                 [org.eclipse.swt/org.eclipse.swt.win32.win32.x86_64 "4.5.1"]
                 [clj-http "3.6.1"]
                 [com.force.api/force-partner-api "44.0.0"]
                 [com.force.api/force-wsc "44.0.0"]
                 [com.force.api/force-metadata-api "44.0.0"]]

  :repositories [["mulesoft-releases" "https://repository.mulesoft.org/releases/"]
                 ["swt" "https://maven-eclipse.github.io/maven"]]

  :profiles {:dev {:resource-paths ["resources-test"]
                   :dependencies [[org.clojure/test.check "0.10.0-alpha2"]
                                  [orchestra "2017.11.12-1"]
                                  [criterium "0.4.4"]]}})

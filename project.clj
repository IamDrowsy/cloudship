(defproject cloudship "0.2.4"
  :description "cloudship is a clojure toolkit to explore and manipulate your salesforce instances"
  :url "https://github.com/IamDrowsy/cloudship"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.11.0"]
                 [instaparse "1.4.10"]
                 [semantic-csv "0.2.1-alpha1" :exclusions [[org.clojure/clojurescript]]]
                 [com.taoensso/timbre "5.2.1"]
                 [expound "0.9.0"]
                 [de.slackspace/openkeepass "0.8.2"]
                 [org.clojure/data.json "2.4.0"]
                 [org.clojure/java.data "1.0.95"]
                 [clojure.java-time "0.3.3"]
                 [org.clojure/core.cache "1.0.225"]
                 [com.taoensso/nippy "3.1.1"]
                 [http-kit "2.5.3"]
                 [ring "1.9.5"]
                 [cheshire "5.10.2"]
                 [ebenbild "0.2.0"]
                 [com.rpl/specter "1.1.4"]
                 [clj-http "3.12.3"]
                 [org.flatland/useful "0.11.6"]
                 [org.clojure/data.xml "0.2.0-alpha6"]
                 [one-time "0.7.0"]
                 [clemence "0.3.0"]
                 [com.force.api/force-partner-api "54.0.0"]
                 [com.force.api/force-wsc "54.0.0"]
                 [com.force.api/force-metadata-api "54.0.0"]]

  :profiles {:dev {:resource-paths ["resources-test"]
                   :dependencies [[org.clojure/test.check "1.1.1"]
                                  [orchestra "2021.01.01-1"]
                                  [criterium "0.4.6"]]}})

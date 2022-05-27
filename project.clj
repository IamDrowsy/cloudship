(defproject cloudship "0.2.6-SNAPSHOT"
  :description "cloudship is a clojure toolkit to explore and manipulate your salesforce instances"
  :url "https://github.com/IamDrowsy/cloudship"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [instaparse "1.4.12"]
                 [semantic-csv "0.2.1-alpha1" :exclusions [[org.clojure/clojurescript]]]
                 [com.taoensso/timbre "5.2.1"]
                 [expound "0.9.0"]
                 [de.slackspace/openkeepass "0.8.2"]
                 [org.clojure/data.json "2.4.0"]
                 [org.clojure/java.data "1.0.95"]
                 [clojure.java-time "0.3.3"]
                 [org.clojure/core.cache "1.0.225"]
                 [com.taoensso/nippy "3.1.1"]
                 [http-kit "2.6.0"]
                 [ring "1.9.5"]
                 [cheshire "5.11.0"]
                 [ebenbild "0.2.0"]
                 [com.rpl/specter "1.1.4"]
                 [clj-http "3.12.3"]
                 [org.flatland/useful "0.11.6"]
                 [org.clojure/data.xml "0.2.0-alpha6"]
                 [one-time "0.7.0"]
                 [clemence "0.3.0"]
                 [com.wsscode/pathom3 "2022.05.19-alpha"]
                 [com.github.peter-gergely-horvath/windpapi4j "1.0"]
                 [com.force.api/force-partner-api "55.1.0"]
                 [com.force.api/force-wsc "55.1.0"]
                 [com.force.api/force-metadata-api "55.1.0"]]
  :repositories {"jitpack" "https://jitpack.io"} ;needed for transitive dependency com.github.kenglxn.qrgen of one-time. cljdoc needs this

  :profiles {:dev {:resource-paths ["resources-test"]
                   :dependencies [[org.clojure/test.check "1.1.1"]
                                  [orchestra "2021.01.01-1"]
                                  [criterium "0.4.6"]
                                  [com.wsscode/pathom-viz-connector "2022.02.14"]]}})

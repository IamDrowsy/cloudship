(ns cloudship.client.query-test
  (:require [clojure.test :refer :all]
            [cloudship.client.query :refer :all]))

(deftest query-parser-test
  (testing "Testing query-parser"
    (is (= {:object "Account"
            :fields ["Id" "Name"]}
           (parse-query "SELECT Id,Name FROM Account")))))
(ns cloudship.client.query-test
  (:require [clojure.test :refer :all]
            [cloudship.client.data.query :refer :all]))

;not needed as parser now is a dependency
#_
(deftest query-parser-test
  (testing "Testing query-parser"
    (is (= {:object "Account"
            :fields ["Id" "Name"]}
           (parse-query "SELECT Id,Name FROM Account")))))
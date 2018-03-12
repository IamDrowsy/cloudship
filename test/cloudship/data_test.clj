(ns cloudship.data-test
  (:require [clojure.test :refer :all]
            [cloudship.data :as data]))

(def test-con :absc)


(deftest basic-data-interaction
  (testing "Insert of account with owner"))

(deftest bulk-data-cross-check
  (testing "Query of bulk and data api should return same result"
    (let [basic-query "SELECT Id,FirstName,LastName FROM Contact LIMIT 10"]
      (is (= (data/query :absc basic-query)
             (data/query :absc basic-query {:bulk true}))))))
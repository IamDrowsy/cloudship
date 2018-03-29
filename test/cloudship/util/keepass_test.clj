(ns cloudship.util.keepass-test
  (:require [clojure.test :refer :all]
            [cloudship.util.keepass :refer :all]
            [clojure.java.io :as io]))

(def test-db (io/file (io/resource "Keepass-test.kdbx")))
(def test-db-pw "12345678")

(deftest keepass-connection
  (reset-dbs!)
  (testing "Get basic properties"
    (is (= {:username "User Name"
            :password "Password"
            :url "http://keepass.info/"
            :title "Sample Entry"
            :notes "Notes"
            :other-prop "other"
            :encrypted-prop "encrypted"}
           (entry test-db ["Sample Entry"] test-db-pw))))
  (testing "Get inner entries"
    (is (= {:username "TestName"
            :password "TestPassword"
            :title "TestEntry"
            :url ""
            :notes ""}
           (entry test-db ["Group1" "Group2" "TestEntry"] test-db-pw))))
  (testing "Get history"
    (let [history (entry-history test-db ["TestHistory"] test-db-pw)]
      (is (= "Name1" (:username (first history))))
      (is (= "Name2" (:username (second history)))))))



(ns cloudship.client.conversion-test
  (:require [clojure.test :refer :all]
            [cloudship.client.conversion :refer :all]
            [cloudship.client.mem.describe :as mem]
            [clojure.java.io :as io]))

(defonce describe-client (mem/from-nippy (io/resource "data-describe.nippy")))

(deftest conversion-test
  (testing "field-type"
    (is (= "string" (field-type describe-client "Account" "Name")))
    (is (= "datetime" (field-type describe-client "Account" "Owner.LastModifiedDate"))))
  (testing "nest and flatten maps"
    (is (= (flatten-map {:Test 1 :key {:type "XX" :nested {:nested-again "Test"} :unnested "Test"}})
           {:Test 1 :key.nested.nested-again "Test" :key.unnested "Test"}))
    (is (= (nest-map describe-client
                     {:type "Contact" :Id "Test" :Account.Name "AccountName" :Account.Owner.Firstname "TestFirstname"})
           {:type "Contact" :Id "Test" :Account {:type "Account" :Name "AccountName" :Owner {:type "User" :Firstname "TestFirstname"}}}))
    (is (= (nest-map describe-client
                     {:type "Contact" :Account.Name nil :Account.Id nil})
           {:type "Contact" :Account nil}))))

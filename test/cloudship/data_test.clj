(ns cloudship.data-test
  (:require [clojure.test :refer :all]
            [cloudship.data :as data])
  (:import (java.util UUID)))

;These test can only run if you have an org with the alias 'cloudship-test' connected to sfdx
;we need to replace this test when we have an offline mock client, but for now we need to test against a real connection

(def test-con {:cache-name :test
               :auth-method "sfdx"
               :org "cloudship-test"})

(deftest basic-data-interaction
  (testing "Insert of account with owner"))

(deftest bulk-data-query-cross-check
  (testing "Query of bulk and data api should return same result"
    (let [basic-query "SELECT Id,FirstName,LastName FROM Contact LIMIT 10"
          nested-query "SELECT Id,FirstName,Account.Name,Account.Id FROM Contact LIMIT 10"
          subquery "SELECT Name,(SELECT FirstName FROM Account.Contacts) FROM Account LIMIT 10"]
      (is (= (data/query test-con basic-query)
             (data/query test-con basic-query {:bulk true})))
      (is (= (data/query test-con nested-query)
             (data/query test-con nested-query {:bulk true})))
      #_;not working as bulk with csv doesn't support inner queries
      (is (= (data/query test-con subquery)
             (data/query test-con subquery {:bulk true}))))))

(defn query-account
  ([id]
   (query-account id {}))
  ([id options]
   (data/query test-con (str "SELECT Name FROM Account WHERE Id = '" id "'") options)))

(defn query-account-name
  ([id]
   (query-account-name id {}))
  ([id options]
   (:Name (first (query-account id options)))))

(defn simple-round-trip [test-options]
  (let [account-name (str (UUID/randomUUID))
        account-name2 (str (UUID/randomUUID))
        id (:id (first (data/insert test-con [{:type "Account" :Name account-name}] test-options)))]
    (is (= account-name (query-account-name id)))
    (data/update test-con [{:type "Account" :Id id :Name account-name2}] test-options)
    (is (= account-name2 (query-account-name id)))
    (data/delete test-con [{:type "Account" :Id id}] (assoc test-options :dont-ask true))
    (is (empty? (query-account id)))
    (data/undelete test-con [id])
    (is (= account-name2 (query-account-name id)))
    (data/delete test-con [id] (assoc test-options :dont-ask true))
    (is (empty? (query-account id)))
    (is (= account-name2 (query-account-name id {:all true})))
    (data/remove-from-bin test-con [id])
    (is (= "Entity is not in the recycle bin"
           (-> (data/undelete test-con [{:Id id}])
               first :errors first :message)))
    ;Query all still finds the record even when undelete does not.
    #_(is (empty? (query-account id {:all true})))))

(deftest data-round-trip
  (testing "Insert, upsert, delete, undelete, remove-from-bin of simple data"
    (simple-round-trip {}))
  (testing "Insert, upsert and delete of simple data for bulk"
    (simple-round-trip {:bulk true})))

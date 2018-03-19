(ns cloudship.util.csv-test
  (:require [clojure.test :refer :all]
            [cloudship.util.csv :refer :all]
            [cloudship.client.impl.mem.describe :as mem]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defonce describe-client (mem/from-nippy (io/resource "data-describe.nippy")))

(def example-csv
  "Id,type,FirstName,Account.Name,Owner.LastName,Account.AnnualRevenue,Account.Owner.FirstName,Account.NumberOfEmployees
00,Contact,Contactname,Accountname,OwnerLastname,30.0,AccountOwnerFirstname,30")

(def example-maps
  [{:Id "00" :type "Contact"
    :FirstName "Contactname"
    :Account {:type "Account" :Name "Accountname" :Owner {:type "User" :FirstName "AccountOwnerFirstname"}
              :AnnualRevenue 30.0, :NumberOfEmployees 30}
    :Owner {:type "User" :LastName "OwnerLastname"}}])

(deftest csv-parse-test
  (testing "parse-csv"
    (is (= example-maps (parse-csv example-csv {:describe-client describe-client})))
    (is (= example-csv (str/trim (csv-string example-maps {:describe-client describe-client
                                                           :header-sort     (comp count name)}))))))
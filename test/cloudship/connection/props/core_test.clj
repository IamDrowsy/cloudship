(ns cloudship.connection.props.core-test
  (:require [clojure.test :refer :all]
            [cloudship.connection.props.core :refer :all]))

(deftest readme-examples
  (testing "Check all readme examples"
    (is (= (->props :org1)
           {:api-version "40.0",
            :full :org1,
            :org "org1",
            :password "very-secret1!",
            :url "https://login.salesforce.com",
            :username "my@username.de"}))

    (is (= (->props :org1:new)
           {:api-version "41.0",
            :base-username "my@username.de",
            :full :org1:new,
            :org "org1",
            :password "very-secret1!",
            :sandbox "new",
            :url "https://test.salesforce.com",
            :username "my@username.de.new"}))
    (is (= (->props :org1:other)
           {:api-version   "40.0",
            :base-username "my@username.de",
            :full          :org1:other,
            :org           "org1",
            :password      "very-secret1!",
            :sandbox       "other",
            :url           "https://test.salesforce.com",
            :username      "my@username.de.other"}))

    (is (= (->props :org1.v:39)
           {:api-version "39.0",
            :full :org1.v:39,
            :org "org1",
            :password "very-secret1!",
            :resolved-flags [{:flag-name "v", :opt "39"}],
            :url "https://login.salesforce.com",
            :username "my@username.de"}))))



(ns cloudship.connection.props.core-test
  (:require [clojure.test :refer :all]
            [cloudship.connection.props.core :refer :all]))

(deftest readme-examples
  (testing "Check all readme examples"
    (is (= (select-keys (->props :org1) [:api-version :cache-name :org :password :url :username])
           {:api-version "51.0",
            :cache-name :org1,
            :org "org1",
            :password "very-secret1!",
            :url "https://login.salesforce.com",
            :username "my@username.de"}))

    (is (= (select-keys (->props :org1:new) [:api-version :base-username :cache-name :org :password :sandbox :url :username])
           {:api-version "52.0",
            :base-username "my@username.de",
            :cache-name :org1:new,
            :org "org1",
            :password "very-secret1!",
            :sandbox "new",
            :url "https://test.salesforce.com",
            :username "my@username.de.new"}))
    (is (= (select-keys (->props :org1:other) [:api-version :base-username :cache-name :org :password :sandbox :url :username])
           {:api-version   "51.0",
            :base-username "my@username.de",
            :cache-name          :org1:other,
            :org           "org1",
            :password      "very-secret1!",
            :sandbox       "other",
            :url           "https://test.salesforce.com",
            :username      "my@username.de.other"}))

    (is (= (select-keys (->props :org1.v:39) [:api-version :cache-name :org :password :resolved-flags :url :username])
           {:api-version "39.0",
            :cache-name :org1.v:39,
            :org "org1",
            :password "very-secret1!",
            :resolved-flags [{:flag-name "v", :opt "39"}],
            :url "https://login.salesforce.com",
            :username "my@username.de"}))))



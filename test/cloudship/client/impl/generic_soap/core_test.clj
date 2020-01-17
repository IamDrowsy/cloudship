(ns cloudship.client.impl.generic-soap.core-test
  (:require [clojure.test :refer :all]
            [cloudship.client.impl.generic-soap.core :refer [api-ns]]
            [cloudship.client.impl.generic-soap.convert :refer [xml->map map->xml tag+content->xml]]))

; string -> parsed xml -> simplified xml
; simplified xml -> parsed xml -> string

(def test-cases
  [{:name "simple attribute"
    :xml {:tag     :tagname
          :content ["String"]}
    :map {:tagname "String"}}
   {:name "condens to map"
    :xml {:tag :nodes
          :content [{:tag :key1 :content ["Attr1"]}
                    {:tag :key2 :content ["Attr2"]}]}
    :map {:nodes {:key1 "Attr1" :key2 "Attr2"}}}
   {:name "condens to list"
    :xml {:tag :nodes
          :content [{:tag :part :content ["Part1"]}
                    {:tag :part :content ["Part2"]}
                    {:tag :part :content [{:tag :other :content ["X"]}]}]}
    :map {:nodes {:part ["Part1" "Part2" {:other "X"}]}}}
   {:name "condens mixed"
    :xml {:tag :nodes
          :content [{:tag :part :content ["Part1"]}
                    {:tag :part :content ["Part2"]}
                    {:tag :other :content ["Other"]}]}
    :map {:nodes {:part ["Part1" "Part2"], :other "Other"}}}])

(deftest test-fn:xml->map
  (doseq [test-case test-cases]
    (testing {:name test-case}
      (is (= (xml->map (:xml test-case)) (:map test-case))))))

(deftest test-fn:tag+content->xml
  (doseq [test-case test-cases]
    (testing {:name test-case}
      (let [[tag content] (first (:map test-case))]
        (is (= (tag+content->xml tag content) (:xml test-case)))))))

(deftest basic-calls
  (testing "describe-calls"
    (is (= (tag+content->xml (api-ns :data) :describeGlobal)
           {:tag "{urn:partner.soap.sforce.com}", :content [":describeGlobal"]}))
    (is (= (tag+content->xml (api-ns :data) :describeSObjects {:sObjectType ["Account" "Case"]})
           {:tag "{urn:partner.soap.sforce.com}describeSObjects",
            :content [{:tag "{urn:partner.soap.sforce.com}sObjectType", :content ["Account"]}
                      {:tag "{urn:partner.soap.sforce.com}sObjectType", :content ["Case"]}]}))))
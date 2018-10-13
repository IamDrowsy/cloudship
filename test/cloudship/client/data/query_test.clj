(ns cloudship.client.data.query-test
  (:require [clojure.test :refer :all]
            [cloudship.client.data.query :refer :all]))

(def queries
  ["SELECT Id FROM Contact WHERE Name LIKE 'A%' AND MailingCity = 'California'"
   "SELECT Name FROM Account ORDER BY Name DESC NULLS LAST"
   "SELECT Name FROM Account WHERE Industry = 'media' LIMIT 125"
   "SELECT Name FROM Account WHERE Industry = 'media' ORDER BY BillingPostalCode ASC NULLS LAST LIMIT 125"
   "SELECT COUNT() FROM Contact"
   "SELECT LeadSource, COUNT(Name) FROM Lead GROUP BY LeadSource"
   "SELECT Name, COUNT(Id) FROM Account GROUP BY Name HAVING COUNT(Id) > 1"
   "SELECT Name, Id FROM Merchandise__c ORDER BY Name OFFSET 100"
   "SELECT Name, Id FROM Merchandise__c ORDER BY Name LIMIT 20 OFFSET 100"
   "SELECT Contact.FirstName, Contact.Account.Name FROM Contact"
   "SELECT Name, (SELECT LastName FROM Contacts) FROM Account"
   "SELECT Name, (SELECT LastName FROM Contacts WHERE CreatedBy.Alias = 'x') FROM Account WHERE Industry = 'media'"
   "SELECT Id, FirstName__c, Mother_of_Child__r.FirstName__c FROM Daughter__c WHERE Mother_of_Child__r.LastName__c LIKE 'C%'"
   "SELECT Name, (SELECT Name FROM Line_Items__r) FROM Merchandise__c WHERE Name LIKE ‘Acme%’"
   "SELECT Id, Owner.Name FROM Task WHERE Owner.FirstName like 'B%'"
   "SELECT TYPEOF What WHEN Account THEN Phone, NumberOfEmployees WHEN Opportunity THEN Amount, CloseDate ELSE Name, Email END FROM Event"
   "SELECT Name, (SELECT CreatedBy.Name FROM Notes) FROM Account"
   "SELECT UserId, LoginTime from LoginHistory"
   "SELECT UserId, COUNT(Id) from LoginHistory WHERE LoginTime > 2010-09-20T22:16:30.000Z AND LoginTime < 2010-09-21T22:16:30.000Z GROUP BY UserId"
   "SELECT Id, Name, IsActive, SobjectType, DeveloperName, Description FROM RecordType"])

(def expected-objects
  ["Contact" "Account" "Account" "Account" "Contact" "Lead"
   "Account" "Merchandise__c" "Merchandise__c" "Contact" "Account"
   "Account" "Daughter__c" "Merchandise__c" "Task" "Event"
   "Account" "LoginHistory" "LoginHistory" "RecordType"])

(deftest soql-parser-does-not-fail
  (testing "parser does not fail"
    (is (not (nil? (map soql-parser queries))))))

(deftest extract-objects
  (testing "objects get extractet"
    (is (= (map object-from-query queries) expected-objects))))
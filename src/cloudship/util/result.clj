(ns ^:no-doc cloudship.util.result
  (:require [taoensso.timbre :as t]
            [clojure.spec.alpha :as s]
            [cloudship.spec.data :as data]))


(defn- aggregate-results
  "aggregates a flattend list of results. Returns a map with {:success SuccessCount :error-count ErrorCount :errors [list of errors]}"
  [result-list]
  (reduce (fn [result item]
            (if (:success item)
              (update result :success inc)
              (-> result
                  (update :error-count inc)
                  (update :errors into (:errors item)))))
          {:success 0
           :error-count 0
           :errors []}
          result-list))

(defn- error-count-map [errors]
  (reduce (fn [m e] (let [k (str (:statusCode e) ":" (:message e ) ":" (into [] (:fields e)))]
                      (if (m k)
                        (update m k inc)
                        (assoc m k 1))))
          {}
          errors))
(s/fdef error-count-map
        :args (s/cat :errors (s/coll-of ::data/error))
        :ret (s/map-of string? int?))

(defn report-results!
  "Aggregates and prints out a list of results. Returns the original list."
  [results]
  (let [examined (aggregate-results results)]
    (t/info "Success: " (:success examined) ", Errors: " (:error-count examined))
    (if (not (empty? (:errors examined)))
      (do (t/info "Errors:")
          (t/info (error-count-map (:errors examined))))))
  results)
(s/fdef report-results!
        :args (s/cat :results (s/coll-of ::data/result))
        :ret ::data/result)

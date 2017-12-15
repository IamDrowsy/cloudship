(ns cloudship.util.spec
  (:require [clojure.spec.alpha :as s]
            [expound.alpha :refer [expound-str]]))

(defn assert-input [spec input]
  (if (not (s/valid? spec input))
    (throw (ex-info (expound-str spec input)
                    {:spec spec
                     :input input}))))


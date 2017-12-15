(ns ^{:doc "Loads the basic properties."}
 cloudship.connection.props.load
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn])
  (:import (java.io File)))

(def ^:private conf-file "cloudship.edn")
(def ^:private sep (System/getProperty "file.separator"))

(defn deep-merge
  "Deep merge that merges maps and concats vectors"
  [v & vs]
  (letfn [(rec-merge [v1 v2]
            (cond (and (map? v1) (map? v2))
                  (merge-with deep-merge v1 v2)
                  (and (vector? v1) (vector? v2))
                  (into [] (concat v1 v2))
                  :else v2))]
    (when (some identity vs)
      (reduce #(rec-merge %1 %2) v vs))))

(defn- slurp-prop-file-if-present
  [file]
  (if (and file (.exists (clojure.java.io/as-file file)))
    (edn/read-string (slurp file))
    {}))

(defn- prop-files
  "Returns a vector of the searched prop files"
  []
  [(io/resource conf-file)
   (str (System/getProperty "user.home") sep ".cloudship" sep conf-file)
   "cloudship.edn"])

(defn slurp-and-merge-props
  "Slurps and deep-merges all present property files.
  Searches for 'cloudship.edn' in resources, user.home and current path (in this order).
  Merges all found conf-files."
  []
  (apply deep-merge (map slurp-prop-file-if-present (prop-files))))
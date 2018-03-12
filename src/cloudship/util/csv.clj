(ns cloudship.util.csv
  (:require [clojure-csv.core :as csv]
            [semantic-csv.core :as sc]
            [cloudship.client.conversion :as convert])
  (:import (java.io Reader)))

(defn ^:no-doc skip-bom-if-present! [^Reader rdr]
  (.mark rdr 4)
  (if (not= 65279 (.read rdr))
    (.reset rdr)))

(def default-opts {:encoding "UTF-8"})

(defn- apply-with-named-args
  "Calls f with args while the last arg should be a map and is expanded to named args"
  [f & args]
  (apply f (concat (butlast args) (flatten (seq (last args))))))

(defn parse-csv
  ([csv-string]
   (parse-csv csv-string default-opts))
  ([csv-string {:keys [describe-client sdl object] :as opts}]
   (let [lines (apply-with-named-args csv/parse-csv csv-string opts)]
     (if (or (empty? lines) (empty? (rest lines)))
       [{}]
       (let [header (map keyword (first lines))
             object-name (or object (:type (first (sc/mappify (take 2 lines)))))]
         (cond->> lines
                  true (sc/remove-comments)
                  true (sc/mappify)
                  describe-client (sc/cast-with (convert/string->cloudship-cast-map describe-client object-name header))
                  describe-client (map (partial convert/nest-map describe-client))
                  true (doall)))))))

(defn csv-string
  ([maps]
   (csv-string maps default-opts))
  ([maps {:keys [describe-client object header header-sort] :as opts}]
   (if (empty? maps)
     []
     (let [object-name (or object (:type (first maps)))
           sort-fn (or header-sort identity)
           header-vec (sort-by sort-fn (or header (keys (convert/flatten-map (first maps)))))]
       (cond->> maps
                true (map convert/flatten-map)
                describe-client (sc/cast-with (convert/cloudship->string-cast-map describe-client object-name header-vec))
                true (sc/vectorize {:header header-vec})
                true (#(apply-with-named-args csv/write-csv % opts)))))))


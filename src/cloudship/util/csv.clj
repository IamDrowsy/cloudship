(ns cloudship.util.csv
  (:require [clojure-csv.core :as csv]
            [semantic-csv.core :as sc]
            [cloudship.client.data.conversion :as convert]
            [clojure.java.io :as io]
            [cloudship.util.sdl :as sdl])
  (:import (java.io Reader)))

(defn ^:no-doc skip-bom-if-present! [^Reader rdr]
  (.mark rdr 4)
  (if (not= 65279 (.read rdr))
    (.reset rdr)))

(def default-opts {:encoding "UTF-8"})

(defn- map->varargs [m]
  (reduce-kv conj [] m))

(defn- apply-with-named-args
  "Calls f with args while the last arg should be a map and is expanded to named args"
  [f & args]
  (apply f (concat (butlast args) (map->varargs (last args)))))

(defn- cast-with [describe-client maps]
  (let [first-line (first maps)
        object (:type first-line)
        header (remove #(= :type %) (keys first-line))]
    (sc/cast-with (convert/string->cloudship-cast-map describe-client object header) maps)))


(defn parse-csv
  "Parses a csv-string with the given options. Also attaches the original header as metadata to the result seq"
  ([csv-string]
   (parse-csv csv-string default-opts))
  ([csv-string {:keys [describe-client sdl object] :as opts}]
   (let [lines (apply-with-named-args csv/parse-csv csv-string opts)]
     (if (empty? lines)
       []
       (let [header (map keyword (first lines))]
         (with-meta
           (if (empty? (rest lines))
             []
             (cond->> lines
                     true (sc/remove-comments)
                     true (sc/mappify opts)
                     sdl (sdl/apply-sdl sdl)
                     object (map #(assoc % :type object))
                     describe-client (cast-with describe-client)
                     describe-client (map (partial convert/nest-map describe-client))
                     true (doall)))
           {:header header}))))))

(defn csv-string
  ([maps]
   (csv-string maps default-opts))
  ([maps {:keys [describe-client object header header-sort] :as opts}]
   (if (empty? maps)
     []
     (let [object-name (or object (:type (first maps)))
           header-keys (or header (keys (convert/flatten-map (first maps))))
           header-vec (if header-sort (sort-by header-sort header-keys) header-keys)]
       (cond->> maps
                true (map convert/flatten-map)
                describe-client (sc/cast-with (convert/cloudship->string-cast-map describe-client object-name header-vec))
                true (sc/vectorize {:header header-vec})
                true (sc/cast-with str)
                true (#(apply-with-named-args csv/write-csv % opts)))))))


(defn read-csv
  "Reads a csv file eagerly into a seq of maps."
  ([file]
   (read-csv file default-opts))
  ([file opts]
   (with-open [rdr (io/reader (io/file file) :encoding (:encoding (merge default-opts opts)))]
     (skip-bom-if-present! rdr)
     (parse-csv rdr opts))))

(defn write-csv
  "Writes a seq of maps as csv. "
  ([file table]
   (write-csv file table default-opts))
  ([file table opts]
   (io/make-parents file)
   (apply (partial spit file (csv-string table (merge (meta table) opts)))
          (map->varargs (merge default-opts opts)))))
(ns cloudship.client.sf-sdk.data.bulk
  (:require [clojure.data.json :as json
              [clojure.set :as set]
              [clojure.string :as str]
              [sfclj.data.csv :as typed-csv]
              [sfclj.util.csv :as csv]
              [clojure.pprint :as pp]
              [sfclj.data.expand :as exp]
              [taoensso.timbre :as t]
              [com.rpl.specter :as s]])
  (:refer-clojure :exclude [update])
  (:import [com.sforce.async JobInfo OperationEnum ContentType JobStateEnum BatchStateEnum BulkConnection BatchInfo BatchInfoList ConcurrencyMode]
   [java.io ByteArrayInputStream]))

(defn- ^BulkConnection resolve-con [con-or-kw]
  (con-cache/resolve-bulk-con con-or-kw))


(def table-widths
  [15 15 7 4 4 4 4 2 10 10 10 10])

(defn- untrim [i v]
  (str (apply str (repeat (- (table-widths i) (count (str v))) " ")) v))


(defn- table-line [vals]
  (apply str (interpose " | " (map-indexed untrim vals))))

(defn print-job-status-header [^JobInfo jobinfo]
  (t/info "Running Job " (.getId jobinfo))
  (t/info (table-line ["TotalTime" "ProcessedTime" "Batches" "Que" "InPr" "Comp" "Fail" "||" "RecProc" "RecComp" "RecFail"])))


(defn print-job-status-line [^JobInfo jobinfo]
  (t/info (table-line [(.getTotalProcessingTime jobinfo)
                       (.getApiActiveProcessingTime jobinfo)
                       (.getNumberBatchesTotal jobinfo)
                       (.getNumberBatchesQueued jobinfo)
                       (.getNumberBatchesInProgress jobinfo)
                       (.getNumberBatchesCompleted jobinfo)
                       (.getNumberBatchesFailed jobinfo)
                       "  "
                       (.getNumberRecordsProcessed jobinfo)
                       (- (.getNumberRecordsProcessed jobinfo) (.getNumberRecordsFailed jobinfo))
                       (.getNumberRecordsFailed jobinfo)])))

(defn- ^JobInfo create-job [con-or-kw {:keys [object op id-field serial] :as job-info}]
  (let [ji (JobInfo.)]
    (if (= op :upsert)
      (.setExternalIdFieldName ji (name id-field)))
    (if serial
      (.setConcurrencyMode ji (ConcurrencyMode/Serial)))
    (doto ji
      (.setObject object)
      (.setOperation (OperationEnum/valueOf (name op)))
      (.setContentType ContentType/CSV))
    (.createJob (resolve-con con-or-kw) ji)))

(defn transform-result-maps [result-map]
  {:success (Boolean/valueOf (:Success result-map))
   :created (Boolean/valueOf (:Created result-map))
   :errors  [(:Error result-map)]
   :id      (:Id result-map)})

(defn- all-complete-or-failed? [^BatchInfoList batchinfolist]
  (every? #(or (= BatchStateEnum/Completed (.getState ^BatchInfo %))
               (= BatchStateEnum/Failed (.getState ^BatchInfo %))) batchinfolist))
(defn- await-completion [con-or-kw jobid]
  (let [con (resolve-con con-or-kw)]
    (loop [c 0]
      (let [jobinfo (.getJobStatus con jobid)
            infolist (.getBatchInfo (.getBatchInfoList con jobid ContentType/CSV))]
        (if (zero? c)
          (print-job-status-header jobinfo))
        (print-job-status-line jobinfo)
        (if (all-complete-or-failed? infolist)
          infolist
          (do (Thread/sleep (min 5000 (* c 1000)))
              (recur (inc c))))))))

(defn- extract-batch-result [^BulkConnection bulk-con jobid batchid]
  (map transform-result-maps
       (csv/csv-to-maps
         (csv/parse-csv (slurp (.getBatchResultStream bulk-con jobid batchid))))))

(defn- extract-batch-results [bulk-con batches]
  (if (empty? batches)
    []
    (let [jobid (.getJobId ^BatchInfo (first batches))
          batchids (map #(.getId ^BatchInfo %) batches)]
      (reduce (fn [result batchid]
                (into result (extract-batch-result bulk-con jobid batchid)))
              []
              batchids))))

(defn- extract-batch-input [^BulkConnection bulk-con jobid batchid]
  (csv/csv-to-maps
    (csv/parse-csv
      (slurp (.getBatchRequestInputStream bulk-con jobid batchid)))))

(defn- close-job [^BulkConnection con jobid]
  (.updateJob con
              (doto (JobInfo.)
                (.setId jobid)
                (.setState JobStateEnum/Closed))))

(defn- extract-query-result [con-or-key object jobid ^BatchInfo batchinfo]
  (let [con (resolve-con con-or-key)
        result-ids (.getResult (.getQueryResultList con jobid (.getId batchinfo) ContentType/CSV))
        result-fn #(->> (.getQueryResultStream con jobid (.getId batchinfo) %)
                        (slurp)
                        (csv/parse-csv)
                        (typed-csv/typed-csv-to-maps con-or-key object))]
    (doall
      (mapcat result-fn result-ids))))

(defn create-batch [^BulkConnection con job ^String data]
  (.createBatchFromStream con job (ByteArrayInputStream. (.getBytes data "UTF-8"))))

(defn q [con-or-kw object query-string]
  (let [con (resolve-con con-or-kw)
        job (create-job con {:object object :op :query})
        batches [(create-batch con job query-string)]]
    (close-job con (.getId job))
    (let [results (await-completion con (.getId job))]
      (extract-query-result con-or-kw object (.getId job) (first results)))))

(defn- bulk-action*
  [op con-or-kw object datastrings opts]
  (let [con (resolve-con con-or-kw)
        job (create-job con (merge {:object object :op op} opts))
        batches (doall (map (partial create-batch con job) datastrings))]
    (close-job con (.getId job))
    (let [results (await-completion con (.getId job))]
      (extract-batch-results con batches))))

(defn- type-key? [key]
  (= "type" (last (str/split (name key ) #"\."))))

(defn- type-keys
  "Returns all :type or :*.type keys"
  [map]
  (filter type-key? (keys map)))

(defn- remove-type-keys [map]
  (apply (partial dissoc map) (type-keys map)))

(defn- ref-field? [kw]
  (str/includes? (name kw) "."))

(defn- ref->id-field
  "Transforms :Account.Name to :AccountId or :Test__r.Name to :Test__c"
  [kw]
  (let [parts (str/split (name kw) #"\.")]
    (cond (= 1 (count parts)) #_=> kw

          (str/ends-with? (first parts) "__r")
          (keyword (str/replace (first parts) #"__r" "__c"))

          :else
          (keyword (str (first parts) "Id")))))

(defn- NA-ref-keys [m]
  (->> (keys m)
       (filter ref-field?)
       (filter #(= "#N/A" (m %)))))

(defn- shrink-to-clean-ref-fields
  "To remove an entry with bulk api you need to set #N/A as it's val.
  However this does not work if your are setting by external id.
  Instead :Account.External_Field #N/A will become :AccountId #N/A"
  [m]
  (let [ks (NA-ref-keys m)]
    (set/rename-keys m (zipmap ks (map ref->id-field ks)))))

(defn- replace-delete-with-na [m]
  (s/setval [s/MAP-VALS (s/pred= :delete)] "#N/A" m))

(defn prepare-data-for-bulk [maps]
  (into []
        (comp
          (map exp/flatten-map)
          (map remove-type-keys)
          (map replace-delete-with-na)
          (map shrink-to-clean-ref-fields)
          (partition-all 10000)
          (map csv/maps-to-csv)
          (map csv/csv-string))
        maps))

(defn- bulk-action
  ([op con-or-kw maps]
   (bulk-action op con-or-kw maps {}))
  ([op con-or-kw maps options]
   (let [object (:type (first maps))
         data (prepare-data-for-bulk maps)]
     (bulk-action* op con-or-kw object data options))))

(defn insert
  ([con-or-kw maps]
   (insert con-or-kw maps {}))
  ([con-or-kw maps opts]
   (bulk-action :insert con-or-kw maps opts)))

(defn delete*
  ([con-or-kw ids method]
   (delete* con-or-kw ids method {}))
  ([con-or-kw ids method opts]
   (let [object (first (i/id con-or-kw (first ids)))
         maps (map (fn [x] {:Id x :type object}) ids)]
     (bulk-action method con-or-kw maps opts))))

(defn delete
  ([con-or-kw ids]
   (delete con-or-kw ids {}))
  ([con-or-kw ids opts]
   (delete* con-or-kw ids :delete opts)))

(defn hard-delete
  ([con-or-kw ids]
   (hard-delete con-or-kw ids {}))
  ([con-or-kw ids opts]
   (delete* con-or-kw ids :hardDelete opts)))

(defn update
  ([con-or-kw maps]
   (update con-or-kw maps {}))
  ([con-or-kw maps opts]
   (bulk-action :update con-or-kw maps opts)))

(defn upsert
  ([con-or-kw id-field maps]
   (upsert con-or-kw id-field maps {}))
  ([con-or-kw id-field maps opts]
   (bulk-action :upsert con-or-kw maps (merge {:id-field id-field} opts))))

(defn- bulk-batch-data [con jobid batchid]
  (let [error-data (extract-batch-result con jobid batchid)
        input-data (extract-batch-input con jobid batchid)]
    (map merge input-data error-data)))

(defn- bulk-batches-data [con jobid batchids]
  (reduce (fn [result batchid]
            (into result (bulk-batch-data con jobid batchid)))
          []
          batchids))
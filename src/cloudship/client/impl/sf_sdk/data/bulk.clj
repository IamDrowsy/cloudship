(ns ^:no-doc cloudship.client.impl.sf-sdk.data.bulk
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [cloudship.util.csv :as csv]
            [cloudship.client.data.conversion :as convert]
            [cloudship.client.data.query :as query]
            [taoensso.timbre :as t]
            [com.rpl.specter :as s]
            [cloudship.client.data.describe :as describe])
  (:refer-clojure :exclude [update])
  (:import [com.sforce.async JobInfo OperationEnum ContentType JobStateEnum BatchStateEnum BulkConnection BatchInfo BatchInfoList ConcurrencyMode]
           [java.io ByteArrayInputStream]
           (com.sforce.soap.partner PartnerConnection)
           (com.sforce.ws ConnectorConfig)))

(defn- ->async-url [server-url]
  (str/join "/" (clojure.string/split
                  (clojure.string/replace server-url "Soap/u" "async")
                  #"/")))

(defn- ^BulkConnection ->bulk-connection [^PartnerConnection pc]
  (let [pc-config (.getConfig pc)
        session (.getSessionId pc-config)
        endpoint (.getServiceEndpoint pc-config)
        bulk-config (ConnectorConfig.)
        proxy (.getProxy pc-config)
        async-url (->async-url endpoint)]
    (println async-url)
    (doto bulk-config
      (.setSessionId session)
      (.setRestEndpoint async-url)
      (.setCompression true)
      (.setTraceMessage false)
      (.setProxy proxy))
    (BulkConnection. bulk-config)))


(def table-widths
  [15 15 7 4 4 4 4 2 10 10 10 10])

(defn- untrim [i v]
  (str (str/join (repeat (- (table-widths i) (count (str v))) " ")) v))


(defn- table-line [vals]
  (str/join " | " (map-indexed untrim vals)))

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

(defn- ^JobInfo create-job [^BulkConnection bulk-con {:keys [object op id-field serial] :as job-info}]
  (let [ji (JobInfo.)]
    (if (= op :upsert)
      (.setExternalIdFieldName ji (name id-field)))
    (if serial
      (.setConcurrencyMode ji (ConcurrencyMode/Serial)))
    (doto ji
      (.setObject object)
      (.setOperation (OperationEnum/valueOf (name op)))
      (.setContentType ContentType/CSV))
    (.createJob bulk-con ji)))

(defn transform-result-maps [result-map]
  {:success (Boolean/valueOf (:Success result-map))
   :created (Boolean/valueOf (:Created result-map))
   :errors  [(:Error result-map)]
   :id      (:Id result-map)})

(defn- all-complete-or-failed? [^BatchInfoList batchinfolist]
  (every? #(or (= BatchStateEnum/Completed (.getState ^BatchInfo %))
               (= BatchStateEnum/Failed (.getState ^BatchInfo %))) batchinfolist))
(defn- await-completion [^BulkConnection bulk-con jobid]
  (loop [c 0]
    (let [jobinfo (.getJobStatus bulk-con jobid)
          infolist (.getBatchInfo (.getBatchInfoList bulk-con jobid ContentType/CSV))]
      (if (zero? c)
        (print-job-status-header jobinfo))
      (print-job-status-line jobinfo)
      (if (all-complete-or-failed? infolist)
        infolist
        (do (Thread/sleep (min 5000 (* c 1000)))
            (recur (inc c)))))))

(defn- extract-batch-result [^BulkConnection bulk-con jobid batchid]
  (map transform-result-maps
       (csv/parse-csv (slurp (.getBatchResultStream bulk-con jobid batchid)))))

(defn- extract-batch-results [bulk-con batches]
  (if (empty? batches)
    []
    (let [jobid (.getJobId ^BatchInfo (first batches))
          batchids (map #(.getId ^BatchInfo %) batches)]
      (reduce (fn [result batchid]
                (into result (extract-batch-result bulk-con jobid batchid)))
              []
              batchids))))

(defn- extract-batch-input [^BulkConnection bulk-con describe-client jobid batchid]
    (csv/parse-csv (slurp (.getBatchRequestInputStream bulk-con jobid batchid)) {:describe-client describe-client}))

(defn- close-job [^BulkConnection con jobid]
  (.updateJob con
              (doto (JobInfo.)
                (.setId jobid)
                (.setState JobStateEnum/Closed))))

(defn- extract-query-result [bulk-con describe-client object jobid ^BatchInfo batchinfo]
  (let [result-ids (.getResult (.getQueryResultList bulk-con jobid (.getId batchinfo) ContentType/CSV))
        result-fn #(-> (.getQueryResultStream bulk-con jobid (.getId batchinfo) %)
                       (slurp)
                       (csv/parse-csv {:describe-client describe-client
                                       :object object}))]
    (doall
      (mapcat result-fn result-ids))))

(defn create-batch! [^BulkConnection con job ^String data]
  (.createBatchFromStream con job (ByteArrayInputStream. (.getBytes data "UTF-8"))))

(defn query [partner-connection describe-client query-string {:keys [all] :as options}]
  (let [bulk-con (->bulk-connection partner-connection)
        object (query/object-from-query query-string)
        job (create-job bulk-con {:object object :op (if all :queryAll :query)})
        batches [(create-batch! bulk-con job query-string)]]
    (close-job bulk-con (.getId job))
    (let [results (await-completion bulk-con (.getId job))]
      (extract-query-result bulk-con describe-client object (.getId job) (first results)))))

(defn- bulk-action*
  [op partner-connection describe-client object datastrings opts]
  (let [bulk-con (->bulk-connection partner-connection)
        job (create-job bulk-con (merge {:object object :op op} opts))
        batches (doall (map (partial create-batch! bulk-con job) datastrings))]
    (close-job bulk-con (.getId job))
    (let [results (await-completion bulk-con (.getId job))]
      (extract-batch-results bulk-con batches))))

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

(defn- prepare-data-for-bulk [describe-client object maps {:keys [batch-size]}]
  (into []
        (comp
          (map convert/flatten-map)
          (map remove-type-keys)
          (map replace-delete-with-na)
          (map shrink-to-clean-ref-fields)
          (partition-all (or batch-size 10000))
          (map #(csv/csv-string % {:describe-client describe-client
                                   :object object})))
        maps))

(defn- bulk-action
  [op partner-connection describe-client maps options]
  (let [object (:type (first maps))
        data (prepare-data-for-bulk describe-client object maps options)]
    (bulk-action* op partner-connection describe-client object data options)))

(defn insert
  [partner-connection describe-client maps opts]
  (bulk-action :insert partner-connection describe-client maps opts))

(defn delete
  [partner-connection describe-client ids opts]
  (let [object (first (describe/describe-id describe-client (first ids)))
        maps (map (fn [x] {:Id x :type object}) ids)
        method (if (:hard opts) :hardDelete :delete)]
    (bulk-action method partner-connection describe-client maps opts)))

(defn update
  [partner-connection describe-client maps opts]
  (bulk-action :update partner-connection describe-client maps opts))

(defn upsert
  [partner-connection describe-client id-field maps opts]
  (bulk-action :upsert partner-connection describe-client maps (merge {:id-field id-field} opts)))
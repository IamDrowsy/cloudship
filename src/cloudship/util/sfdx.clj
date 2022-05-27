(ns cloudship.util.sfdx
  (:require [clojure.java.shell :as sh]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [clojure.data.json :as json]))

(defn windows? []
  (str/starts-with? (System/getProperty "os.name") "Windows"))

(def ^:dynamic *sfdx-executable* (if windows? "sfdx.cmd"
                                              "sfdx"))

(defn kw->command [kw]
  (name kw))

(def sh-opts #{:in :in-enc :out-enc :env :dir})

(s/def ::primitive (s/or :number number? :string string? :keyword keyword? :bool boolean?))
(s/def ::key (s/or :sh sh-opts :sfdx keyword?))
(s/def ::opts (s/map-of ::key ::primitive))

(s/def ::sfdx-short-key-string (s/and string? #(= (count %) 2) #(re-matches #"-[a-zA-z]" %)))
(s/def ::sfdx-normal-key-string (s/and string? #(< (count %) 2) #(re-matches #"--[a-zA-z]+" %)))

(s/def ::sh-opt (s/cat :key sh-opts :val ::primitive))
(s/def ::sfdx-opt (s/cat :key (s/or :short ::sfdx-short-key-string
                                    :normal ::sfdx-normal-key-string)
                         :val ::primitive))
(s/def ::sfdx-sh-args (s/cat :sh-opts (s/* ::sh-opt) :sfdx-opts (s/* ::sfdx-opt)))


(defn opt-key->sh-string [opt-key]
  (let [s (name opt-key)]
    (cond
      (sh-opts opt-key) opt-key
      (= 1 (count s)) (str "-" s)
      :else           (str "--" s))))

(defn opt-val->sh-string [opt-val]
  (str opt-val))

(defn conj-opts [sh-opts [opt-key opt-val]]
  (conj sh-opts
        (opt-key->sh-string opt-key)
        (opt-val->sh-string opt-val)))

(defn map->sh-args [opts-map]
  (let [opts-sorted (sort-by (comp not sh-opts key) opts-map)]
    (reduce conj-opts [] opts-sorted)))
(s/fdef map->sh-args
        :args (s/cat :opts ::opts)
        :ret ::sfdx-sh-args)

(defn run-sfdx-command*
  "Like run-sfdx-command but without json in/out and error parsing"
  ([command]
   (run-sfdx-command* command {}))
  ([command opts]
   (apply (partial sh/sh *sfdx-executable* (kw->command command))
          (map->sh-args opts))))

(defn safe-json-read [s]
  (if (str/blank? s)
    s
    (json/read-str s :key-fn keyword)))

(defn run-sfdx-command
  ([command]
   (run-sfdx-command command {}))
  ([command opts]
   (let [result
         (apply (partial sh/sh *sfdx-executable* (kw->command command) "--json")
                (map->sh-args opts))
         parsed-result (-> result
                           (update :out safe-json-read))]
     (if (zero? (:exit result))
       (:out parsed-result)
       (throw (ex-info (str "SFDX Error: " (get-in parsed-result [:err :message])) parsed-result))))))



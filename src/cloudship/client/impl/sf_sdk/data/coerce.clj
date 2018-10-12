(ns ^:no-doc cloudship.client.impl.sf-sdk.data.coerce
  (:require [taoensso.timbre :as t]
            [cloudship.client.impl.sf-sdk.data.type-conversion :as sdk-convert]
            [cloudship.client.data.conversion :as convert])
  (:import (com.sforce.ws.bind XmlObject XmlObjectWrapper)
           (com.sforce.soap.partner.sobject ISObject SObject)
           (javax.xml.namespace QName)))

(defn- extract-inner-query [result]
  (filter #(= "records" (.getLocalPart (.getName %)))
          (iterator-seq (.getChildren result))))

(defn sobj->map [data-describe-client ^XmlObject obj]
  (let [fields (iterator-seq (.getChildren obj))
        obj-name (cond (instance? XmlObjectWrapper obj) nil
                       (instance? ISObject obj) (.getType ^ISObject obj)
                       :else (t/warn "Unknown Case " (type obj)))]
    (reduce (fn [m field]
              (let [inner-query? (and (.getXmlType field) (= "QueryResult" (.getLocalPart (.getXmlType field))))
                    nested-childs? (not (empty? (iterator-seq (.getChildren field))))
                    fieldname (.getLocalPart (.getName field))]
                (cond inner-query? (assoc m (keyword fieldname)
                                            (mapv (partial sobj->map data-describe-client)
                                                  (extract-inner-query field)))
                      nested-childs? (assoc m (keyword fieldname) (sobj->map data-describe-client field))
                      :else
                      (assoc m (keyword fieldname)
                               (if (or (nil? obj-name) (= "type" fieldname))
                                 (.getValue field)
                                 (convert/string->cloudship data-describe-client obj-name fieldname (.getValue field)))))))
            {}
            fields)))

(defn- add-field-to-null [^ISObject obj field-name]
  (let [f (.getFieldsToNull obj)]
    (if (nil? f)
      (.setFieldsToNull obj (into-array String [field-name]))
      (.setFieldsToNull obj (into-array String (conj (into [] f) field-name))))))

(declare map->sobj)

(defn- set-field [data-describe-client ^XmlObject obj name val]
  (if (= :delete val)
    (add-field-to-null obj name)
    (cond
      (map? val) (.setField obj name (map->sobj data-describe-client val))
      (= name "type") (.setField obj name val)
      :else    (.setField obj name (sdk-convert/cloudship->sdk-client (convert/field-type data-describe-client (.getType ^ISObject obj) name) val)))))

(defn- type-first-sort-fn
  "Type call needs to come first"
  [entry]
  (cond (= (key entry) :type) 0
        :true 1))

(defn map->sobj [con-or-kw m]
  (let [obj (SObject.)]
    (run! #(set-field con-or-kw obj (name (key %)) (val %)) (sort-by type-first-sort-fn m))
    obj))

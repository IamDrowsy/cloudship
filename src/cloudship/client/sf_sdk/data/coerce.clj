(ns cloudship.client.sf-sdk.data.coerce
  (:require [taoensso.timbre :as t]
            [cloudship.client.sf-sdk.data.types :as types])
  (:import (com.sforce.ws.bind XmlObject XmlObjectWrapper)
           (com.sforce.soap.partner.sobject ISObject)
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
                                            (into []
                                                  (map (partial sobj->map data-describe-client)
                                                       (extract-inner-query field))))
                      nested-childs? (assoc m (keyword fieldname) (sobj->map data-describe-client field))
                      :else
                      (assoc m (keyword fieldname)
                               (if (or (nil? obj-name) (= "type" fieldname))
                                 (.getValue field)
                                 (types/sf->clj data-describe-client obj-name fieldname (.getValue field)))))))
            {}
            fields)))
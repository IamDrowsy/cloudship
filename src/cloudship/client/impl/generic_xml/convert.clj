(ns cloudship.client.impl.generic-xml.convert
  (:require [flatland.useful.seq :as us]))

(defn simple? [content]
  (and (= 1 (count content)) (string? (first content))))

(defn default-convert-fn [tag content]
  (case content
    "false" false
    "true" true
    content))

(defn extract-grouping [convert-fn result [tag content]]
  (cond (and (empty? result) (string? content))
        (convert-fn tag content)
        ; if the first was a string but there is more
        (string? result)
        (conj [result] content)
        :else
        (conj result content)))

(defn element->map [convert-fn {:keys [tag content] :as elem}]
  [(keyword (name tag))
   (if (simple? content)
     (first content)
     (us/groupings first (partial extract-grouping convert-fn)
                   [] (mapv (partial element->map convert-fn) content)))])

(defn xml->map
  ([xml]
   (xml->map xml default-convert-fn))
  ([{:keys [tag content] :as elem} convert-fn]
   (element->map convert-fn elem)))

(defn map->xml [namespace tag content]
  {:tag (keyword (name namespace) (name tag))
   :content (if (string? content)
              content)})

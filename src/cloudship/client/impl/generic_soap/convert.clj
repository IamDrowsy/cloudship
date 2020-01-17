(ns cloudship.client.impl.generic-soap.convert
  (:require [flatland.useful.seq :as us]))

(defn single-string? [content]
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
   (if (single-string? content)
     (first content)
     (us/groupings first (partial extract-grouping convert-fn)
                   [] (mapv (partial element->map convert-fn) content)))])

(defn xml->map
  ([xml]
   (xml->map xml default-convert-fn))
  ([{:keys [tag content] :as elem} convert-fn]
   (into {} [(element->map convert-fn elem)])))

(defn primitiv? [content]
  (or (string? content) (not (seqable? content))))

(defn tag+content->xml
  ([tag content]
   (tag+content->xml nil tag content))
  ([namespace tag content]
   (let [tag-in-ns (if namespace (str namespace (name tag))
                                 tag)]
     (cond (primitiv? content) {:tag tag-in-ns :content [(str content)]}
           (map? content) {:tag tag-in-ns :content (reduce (fn [result [tag content]]
                                                             (let [new-content (tag+content->xml namespace tag content)]
                                                               (if (vector? new-content)
                                                                 (into result new-content)
                                                                 (conj result new-content))))
                                                           []
                                                           content)}
           (empty? content) {:tag tag-in-ns :content []}
           (sequential? content) (mapv (partial tag+content->xml namespace tag) content)))))

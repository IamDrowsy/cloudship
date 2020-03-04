(ns cloudship.client.impl.generic-soap.convert
  (:require [flatland.useful.seq :as us]
            [clojure.data.xml :as xml]))

(defn single-string? [content]
  (and (= 1 (count content)) (string? (first content))))

(defn single-nil? [content]
  ; maybe we want to check for {:attrs #:xmlns.http%3A%2F%2Fwww.w3.org%2F2001%2FXMLSchema-instance{:nil "true"}} instead
  (and (= 1 (count content)) (nil? (:content (first content)))))

(defn default-convert-fn [tag content]
  (case content
    "false" false
    "true" true
    content))

(defn extract-grouping [convert-fn result [tag content]]
  ; TODO when we have aggregated fields like BillingAddress we return :BillingAddress [{:city ...}] instead of {:city ...}
  ; it's not trivial to see if we have on single entry of a list or an entry thats always only on (like the aggregated field)
  (cond
    ; first was nil but now there is content
    (nil? result)
    [nil content]
    (and (empty? result) (string? content))
    (convert-fn tag content)
    ; if everything is empty
    (and (empty? result) (nil? content))
    nil
    ; if the first was a string but there is more
    (string? result)
    [result content]
    :else
    (conj result content)))

(xml/alias-uri 'xsi "http://www.w3.org/2001/XMLSchema-instance")

(defn element->map [convert-fn {:keys [tag attrs content] :as elem}]
  (let [new-content (cond (single-string? content) (first content)
                          (::xsi/nil attrs) nil
                          :else (us/groupings first (partial extract-grouping convert-fn)
                                              [] (mapv (partial element->map convert-fn) content)))]
    [(keyword (name tag))
     new-content]))

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
           (nil? content) {:tag tag-in-ns :attrs {:nil "true"}}
           (empty? content) {:tag tag-in-ns :content []}
           (sequential? content) (mapv (partial tag+content->xml namespace tag) content)))))

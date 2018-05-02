(ns cloudship.client.impl.sf-sdk.meta.convert
  (:require [cloudship.client.impl.sf-sdk.util.reflect :as r]
            [clojure.string :as str]
            [ebenbild.core :refer [like]])
  (:import [clojure.lang Reflector]
           [com.sforce.ws.bind XMLizable]
           [com.sforce.soap.metadata Metadata WebLink CustomField]))

(defn find-fields [o]
  (if (nil? o)
    (do (println "got nil in find fields") #{})
    (let [fields
          (->> #_(:members (r/reflect o))
            (r/transitive-reflect-members o)
            (filter (like {:name            #(str/starts-with? (name %) "get")
                           :flags           #(and (% :public)
                                                  (not (% :abstract))
                                                  (not (% :static)))
                           :parameter-types []}))
            (map :name)
            (map name)
            (map (fn [s] (subs s 3))))]
      (sort (into #{} fields)))))

(defn str-invoke [instance method-str & args]
  (Reflector/invokeInstanceMethod
    instance
    method-str
    (to-array args)))

(defn get-field [obj fieldname]
  (str-invoke obj (str "get" fieldname)))

(defn array? [o]
  (str/starts-with? (str (type o)) "class ["))

(defn enum? [o]
  (and (type o)
       (.isEnum ^Class (type o))))

(defn api-fix
  "Fixes bugs in the metadata api by changeing the retrieved values
   * In Layouts, :EmptySpace false is retrieved but considered true on update, so nil is the way to go"
  [m]
  (if (= false (:EmptySpace m))
    (assoc m :EmptySpace nil)
    m))

(defn construct-empty [c]
  (Reflector/invokeConstructor c (into-array Object [])))

(def construct-empty-mem (memoize construct-empty))

(defn custom? [^Metadata o]
  (str/ends-with? (.getFullName o) "__c"))

(def always-include-meta-map
  {"CustomObject" (fn [o] #{"EnableFeeds" "EnableHistory"})
   "FieldSetItem" (fn [o] #{"IsFieldManaged" "IsRequired"})
   "PicklistValue" (fn [o] #{"Default"})
   "Picklist" (fn [o] #{"Sorted"})
   "RecordType" (fn [o] #{"Active"})
   "WebLink" (fn [o] (case (str (.getDisplayType ^WebLink o))
                       "button" #{"Protected"}
                       "link" #{"Protected" "HasMenubar" "HasToolbar" "ShowsLocation" "ShowsStatus"}
                       "massActionButton" #{}))
   "CustomField" (fn [o] (case (str (.getType ^CustomField o))
                           "TextArea" #{"Required" "ExternalId"}
                           "Checkbox" #{"ExternalId"}
                           "Number" #{"Required" "ExternalId" "Unique" "Scale"}
                           "Email" #{"Required" "ExternalId" "Unique"}
                           "Date" #{"Required" "ExternalId"}
                           "Picklist" (if (custom? o) #{"Required" "ExternalId"} #{})
                           "MultiselectPicklist" #{"Required" "ExternalId"}
                           "Text" #{"ExternalId" "Required" "Unique"}
                           "Url" #{"ExternalId" "Required"}
                           "Lookup" (if (custom? o) #{"ExternalId" "Required"} #{})
                           "Phone" #{"ExternalId" "Required"}
                           #{}))})

(def always-include-describe-data-map
  {"Field" (fn [o]
             (case (str (.getSoapType o))
               "xsd:int" #{"Digits"}
               "xsd:double" #{"Precision" "Scale"}
               #{}))})

(def always-include-val-map
  (merge always-include-meta-map always-include-describe-data-map))


(defn local-name [c]
  (last (str/split (str c) #"\.")))

(defn always-include? [o f]
  (let [fun (always-include-val-map (local-name (type o)))]
    (and fun ((fun o) f))))

(defn default-and-not-included? [o f x]
  (and (not (always-include? o f))
       (= (get-field (construct-empty-mem (r/class-of (type o))) f) x)))

(defn obj->map [o]
  (api-fix
    (let [fields (find-fields o)]
      (if (instance? XMLizable o)
        (reduce (fn [m f]
                  (let [x (get-field o f)]
                    (cond (default-and-not-included? o f x)
                          m
                          (instance? XMLizable x)
                          (assoc m (keyword f) (obj->map x))
                          (array? x)
                          (assoc m (keyword f)  (into [] (map obj->map x)))
                          (enum? x)
                          (assoc m (keyword f)
                                   {:enumType (type x)
                                    :value (str x)})
                          :else
                          (assoc m (keyword f) x))))
                {:internalType (type o)}
                fields)
        o))))

; ---- map -> obj -------
(defn setField [obj name val]
  #_(println obj ": " name " = " val)
  (str-invoke obj (str "set" name) val))


(defn array-type-fn [obj fieldname]
  (let [type-str (->> (r/transitive-reflect-members obj)
                      (filter #(and (= (name (:name %)) (str "set" fieldname))
                                    ((:flags %) :public)))
                      first
                      :parameter-types
                      first
                      name
                      ((fn [x]
                         (subs x 0 (- (count x) 2)))))]
    (if (r/primitive? type-str)
      (case type-str
        "byte" byte-array
        "int" int-array)
      (partial into-array (r/class-of type-str)))))

(defn field-type-cast [obj fieldname val]
  (if (vector? val)
    ((array-type-fn obj fieldname) val)
    val))

(defn construct [name & args]
  (Reflector/invokeConstructor (r/class-of name) (into-array Object args)))

(defn sanatize-enum-name [s]
  (str/replace s \- \_))

(defn construct-enum [name val]
  (Reflector/invokeStaticMethod (r/class-of name) "valueOf" #^"[Ljava.lang.Object;" (into-array Object [(sanatize-enum-name val)])))

(defn clean-up-map [m]
  (reduce (fn [m [k v]]
            (if (or (= v nil) #_(= v 0))
              (dissoc m k)
              m))
          m
          m))

(defn map->obj [m]
  (cond (and (map? m) (:internalType m))
        (let [obj (construct (:internalType m))]
          (dorun
            (for [k (keys (clean-up-map (dissoc m :internalType)))]
              (setField obj (name k) (field-type-cast obj (name k)
                                                      (map->obj (k m))))))
          obj)
        (and (map? m) (:enumType m))
        (construct-enum (:enumType m) (:value m))
        (vector? m)
        (into [] (map map->obj m))
        :else
        m))
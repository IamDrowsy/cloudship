(ns
  ^:no-doc
  cloudship.client.impl.sf-sdk.util.reflect
  (:require [clojure.reflect :as r]
            [clojure.string :as str])
  (:import (clojure.lang Named)))

(defn primitive? [^String name]
  (not (.contains name ".")))

(defn primitive-class [name]
  (Class/forName (str "java.lang." (str/upper-case (subs name 0 1)) (subs name 1))))

(defn ^Class class-of [class-or-name]
  (cond (instance? Class class-or-name)
        class-or-name
        (and (instance? String class-or-name) (primitive? class-or-name))
        (primitive-class class-or-name)

        (instance? String class-or-name)
        (Class/forName class-or-name)

        (and (instance? Named class-or-name)
             (not (namespace class-or-name))
             (primitive? (name class-or-name)))
        (primitive-class (name class-or-name))

        (instance? Named class-or-name)
        (Class/forName (str (if (namespace class-or-name)
                              (str (namespace class-or-name) "."))
                            (name class-or-name)))

        :else
        (throw (IllegalArgumentException.
                 (str "unkown type " (type class-or-name) " to get class for " class-or-name)))))


(defn transitive-reflect-members [o]
  {:pre [(not (nil? o))]}
  (let [r-data (r/reflect o)
        bases (map #(class-of (name %)) (:bases r-data))]
    (if (= o Object)
      []
      (concat (:members r-data)
              (mapcat transitive-reflect-members bases)))))

(defn public? [d]
  ((:flags d) :public))

(defn public-methods [o]
  (->> (transitive-reflect-members o)
       (filter public?)))


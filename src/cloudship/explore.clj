(ns ^{:doc "Namespace with helper functions for exploring an org.

 You should not rely on the exact results of those functions as they might heuristics."}
  cloudship.explore
  (:require [cloudship.util.suggest :as suggest]
            [cloudship.data :as data]))

(defn find-object
  "Returns a list of sobjects for the given cloudship that are similar (using levenshtein distance) to the given name."
  [cloudship object]
  (let [t (suggest/build-trie (map :name (data/describe cloudship)))]
    (suggest/best-suggestions t object)))

(defn find-field
  "Returns a list of fields for the given cloudship and object that are similar (using levenshtein distance) to the given name"
  [cloudship object field]
  (let [t (suggest/build-trie (map :name (:fields (data/describe-object cloudship object))))]
    (suggest/best-suggestions t field)))

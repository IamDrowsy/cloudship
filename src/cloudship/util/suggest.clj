(ns cloudship.util.suggest
  (:require [clemence.core :as c]
            [clojure.string :as str]))

(defn build-trie [texts]
  (c/build-trie texts))

(defn max-dist [text-length]
  (case text-length
    4 1
    5 2
    6 3
    11 4
    20 5
    (long (* 0.25 text-length))))

(defn best-suggestions [trie text]
  (mapv first (take 5 (sort-by second (c/levenshtein trie text (max-dist (count text)))))))

(defn valid?-or-throw-with-alternatives [throw-name trie text]
  (if (empty? (c/levenshtein trie text 0)) ;not in trie
    (let [suggestions (best-suggestions trie text)]
      (throw (ex-info (str "Got invalid " throw-name " '" text "'. "
                           (if (empty? suggestions)
                             ""
                             (str "Did you mean " suggestions)))
                      {:suggestions suggestions
                       :input text
                       :trie true})))
    text))
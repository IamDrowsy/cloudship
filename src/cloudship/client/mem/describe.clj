(ns cloudship.client.mem.describe
  (:require [cloudship.client.protocols :as p :refer [DataDescribeClient]]
            [taoensso.nippy :as nippy]
            [clojure.set :as set]))

(defrecord InMemDescribeClient [global objects]
  DataDescribeClient
  (describe-global [client] (:global client))
  (describe-objects [client object-names] (map #(get (:objects client) %) object-names)))

(defn from-client [other-describe-client]
  (let [global (p/describe-global other-describe-client)
        all-objects-names (map :name global)
        all-data (zipmap all-objects-names (p/describe-objects other-describe-client all-objects-names))]
    (->InMemDescribeClient global all-data)))

(defn to-nippy [client file]
  (nippy/freeze-to-file file client))

(defn from-nippy [file]
  (nippy/thaw-from-file file))


; Memoize describe client
(defn- memoize-global-fn [underlying-describe-client atom]
  (fn []
    (let [{:keys [global]} @atom]
      (if global
        global
        (:global (swap! atom assoc :global (p/describe-global underlying-describe-client)))))))

(defn- fill-atom-with-keys [underlying-describe-client atom object-names]
  (if (empty? object-names)
    @atom
    (swap! atom update :objects
           #(merge % (zipmap object-names (p/describe-objects underlying-describe-client object-names))))))

(defn- memoize-sobjects-fn [underlying-describe-client atom]
  (fn [object-names]
    (let [known-data (:objects @atom)
          known-object-names (into #{} (keys known-data))
          unknown-object-names (set/difference (into #{} object-names) known-object-names)
          filled-data (:objects (fill-atom-with-keys underlying-describe-client atom unknown-object-names))]
      (map #(get filled-data %) object-names))))

(defn memoize-describe-client
  "Takes a DataDescribeClient and returns a new one that uses an atom to cache every "
  [underlying-describe-client]
  (let [a (atom {})
        global-fn (memoize-global-fn underlying-describe-client a)
        objects-fn (memoize-sobjects-fn underlying-describe-client a)]
    (reify
      DataDescribeClient
      (describe-global [_] (global-fn))
      (describe-objects [_ object-names] (objects-fn object-names)))))

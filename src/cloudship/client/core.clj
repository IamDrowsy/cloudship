(ns ^:no-doc cloudship.client.core
  (:require [cloudship.auth.core :as auth]
            [cloudship.client.data.protocol :as p :refer [DataDescribeClient DataClient BaseClient]]
            [cloudship.client.meta.protocol :as mp :refer [MetadataClient MetadataDescribeClient]]
            [com.rpl.specter :refer :all]
            [cloudship.connection.props.core :as props]
            [cloudship.client.impl.mem.describe :as md]
            [cloudship.client.impl.mem.meta-describe :as mmd]
            [cloudship.client.impl.sf-sdk.data.init :as data]
            [cloudship.client.impl.sf-sdk.meta.core :as meta]
            [cloudship.client.impl.generic-soap.init :as generic]
            [clojure.core.cache :as cache :refer [has? miss hit lookup evict]]
            [taoensso.timbre :as t]
            [clojure.pprint :as pp])
  (:import (clojure.lang Keyword)
           (java.util Map)))

(extend-protocol BaseClient
  nil
  (info [this] nil))

(defn transform-first-arg [base-fn first-arg-transform]
  (fn [first-arg & rest-args]
    (apply (partial base-fn (first-arg-transform first-arg)) rest-args)))

(defn function-map [protocol transform-fn]
  (transform [ALL] (fn [[k v]] [(keyword (:name (meta k))) (transform-first-arg k transform-fn)])
             (:method-builders protocol)))

(defn extend-type-with-transform-fn [type protocol transform-fn]
  (extend type protocol
    (function-map protocol transform-fn)))

(defrecord CloudshipClient [data-describe-client data-client metadata-describe-client metadata-client]
  BaseClient
  (info [this] (transform [MAP-VALS] p/info (into {} this))))

(extend-type-with-transform-fn CloudshipClient DataDescribeClient :data-describe-client)
(extend-type-with-transform-fn CloudshipClient DataClient :data-client)
(extend-type-with-transform-fn CloudshipClient MetadataDescribeClient :metadata-describe-client)
(extend-type-with-transform-fn CloudshipClient MetadataClient :metadata-client)

(defn init-cloudship-client [config]
  (if (:cache-name config)
    (t/infof "Initializing new connection for %s" (:cache-name config))
    (t/info "Initializing new connection without :cache-name"))
  (t/info (str "Connection data is: \n" (with-out-str (pp/pprint (select-keys config [:proxy :username :url :api-version])))))
  (let [authed-config (auth/auth config)
        generic-client (generic/->generic-client authed-config)
        data-con (if (= :generic (:client config))
                   generic-client
                   (data/->partner-connection authed-config))
        meta-con (if (= :generic (:client config))
                   generic-client
                   (meta/->metadata-connection data-con))]
    (->CloudshipClient (md/memoize-describe-client generic-client)
                       data-con
                       (mmd/memoize-describe-client meta-con)
                       meta-con)))

(def cache (atom (cache/ttl-cache-factory {} :ttl 3.6e+6))) ;one hour

(defn resolve-cloudship-client [resolveable]
  (cond (instance? CloudshipClient resolveable)
        resolveable
        (or (keyword? resolveable) (map? resolveable))
        (let [cache-name (if (map? resolveable) (:cache-name resolveable) resolveable)]
          (if (and cache-name (has? @cache cache-name))
            (lookup (swap! cache hit cache-name) cache-name)
            (lookup (swap! cache miss cache-name (init-cloudship-client (props/->props resolveable))) cache-name)))
        :else (throw (ex-info (str resolveable " is not resolveable to a cloudship client")
                              {:resolvable resolveable}))))

(defn evict-cloudship-client [resolveable]
  (if-let [full-name (cond (map? resolveable) (:cache-name resolveable)
                           (keyword? resolveable) resolveable
                           :else nil)]
    (swap! cache evict full-name)
    (t/error (str "Cannot evict " resolveable " as it has no known full-name (:full)."))))

(defn extend-cloudship-client [type ->cloudship-client]
  (run! #(extend-type-with-transform-fn type % ->cloudship-client)
        [BaseClient DataDescribeClient DataClient MetadataDescribeClient MetadataClient]))

(extend-cloudship-client Keyword resolve-cloudship-client)
(extend-cloudship-client Map resolve-cloudship-client)
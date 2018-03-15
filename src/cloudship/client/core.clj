(ns cloudship.client.core
  (:require [cloudship.client.protocols :as p :refer [DataDescribeClient DataClient BaseClient]]
            [cloudship.connection.props.core :as props]
            [cloudship.client.mem.describe :as md]
            [cloudship.client.sf-sdk.data.init :as init]
            [clojure.core.cache :as cache :refer [has? miss hit lookup evict]]
            [taoensso.timbre :as t]
            [clojure.pprint :as pp])
  (:import (clojure.lang Keyword)
           (java.util Map)))

(extend-protocol BaseClient
  nil
  (info [this] nil))

(defrecord CloudshipClient [data-describe-client data-client metadata-client]
  BaseClient
  (info [this] {:data-client (p/info (:data-client this))
                :data-describe-client (p/info (:data-describe-client this))
                :metadata-client (p/info (:metadata-client this))})
  DataDescribeClient
  (describe-global [this] (p/describe-global (:data-describe-client this)))
  (describe-objects [this object-names] (p/describe-objects (:data-describe-client this) object-names))
  DataClient
  (query [this describe-client query options]
    (p/query (:data-client this) describe-client query options))
  (insert [this describe-client records options]
    (p/insert (:data-client this) describe-client records options))
  (update [this describe-client records options]
    (p/update (:data-client this) describe-client records options))
  (upsert [this describe-client records options]
    (p/upsert (:data-client this) describe-client records options))
  (delete [this describe-client records options]
    (p/delete (:data-client this) describe-client records options)))

(defn init-cloudship-client [props]
  (t/infof "Initializing new connection for %s" (:full props))
  (t/info (str "Connection data is: \n" (with-out-str (pp/pprint (select-keys props [:proxy :username :url])))))
  (let [partner-con (init/->partner-connection props)]
    (->CloudshipClient (md/memoize-describe-client partner-con)  partner-con partner-con)))

(def cache (atom (cache/ttl-cache-factory {} :ttl 3.6e+6))) ;one hour

(defn resolve-cloudship-client [resolveable]
  (cond (instance? CloudshipClient resolveable)
        resolveable
        (or (keyword? resolveable) (map? resolveable))
        (let [full-name (if (map? resolveable) (:full resolveable) resolveable)]
          (if (has? @cache full-name)
            (lookup (swap! cache hit full-name) full-name)
            (lookup (swap! cache miss full-name (init-cloudship-client (props/->props resolveable))) full-name)))
        :else (throw (ex-info (str resolveable " is not resolveable to a cloudship client")
                              {:resolvable resolveable}))))

(defn evict-cloudship-client [resolveable]
  (if-let [full-name (cond (map? resolveable) (:full resolveable)
                           (keyword? resolveable) resolveable
                           :else nil)]
    (swap! cache evict full-name)
    (t/error (str "Cannot evict " resolveable " as it has no known full-name (:full)."))))

(defn info [resolvable]
  (p/info (resolve-cloudship-client resolvable)))

(extend-type Keyword
  BaseClient
  (info [keyword] (info keyword))
  DataDescribeClient
  (describe-global [keyword] (p/describe-global (resolve-cloudship-client keyword)))
  (describe-objects [keyword object-names] (p/describe-objects (resolve-cloudship-client keyword) object-names))
  DataClient
  (query [keyword describe-client query-string options]
    (p/query (resolve-cloudship-client keyword) describe-client query-string options))
  (insert [keyword describe-client records options]
    (p/insert (resolve-cloudship-client keyword) describe-client records options))
  (update [keyword describe-client records options]
    (p/update (resolve-cloudship-client keyword) describe-client records options))
  (upsert [keyword describe-client records options]
    (p/upsert (resolve-cloudship-client keyword) describe-client records options))
  (delete [keyword describe-client records options]
    (p/delete (resolve-cloudship-client keyword) describe-client records options)))

(extend-type Map 
  BaseClient
  (info [props] (info props))
  DataDescribeClient
  (describe-global [props] (p/describe-global (resolve-cloudship-client props)))
  (describe-objects [props object-names] (p/describe-objects (resolve-cloudship-client props) object-names))
  DataClient
  (query [props describe-client query-string options]
    (p/query (resolve-cloudship-client props) describe-client query-string options))
  (insert [props describe-client records options]
    (p/insert (resolve-cloudship-client props) describe-client records options))
  (update [props describe-client records options]
    (p/update (resolve-cloudship-client props) describe-client records options))
  (upsert [props describe-client records options]
    (p/upsert (resolve-cloudship-client props) describe-client records options))
  (delete [props describe-client records options]
    (p/delete (resolve-cloudship-client props) describe-client records options)))
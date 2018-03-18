(ns cloudship.client.meta.protocol
  (:refer-clojure :exclude [update read]))

(defprotocol MetadataClient
  (read [this meta-describe-client type names])
  (create [this meta-describe-client metadata])
  (update [this meta-describe-client metadata])
  (upsert [this meta-describe-client metadata])
  (delete [this meta-describe-client metadata]))

(defprotocol MetadataDescribeClient
  (describe [this])
  (describe-type [this type]))
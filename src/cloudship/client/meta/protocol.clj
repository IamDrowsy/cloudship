(ns ^:no-doc cloudship.client.meta.protocol
  (:refer-clojure :exclude [update read list]))

(defprotocol MetadataClient
  (list [this meta-describe-client type])
  (read [this meta-describe-client type names])
  (create [this meta-describe-client metadata])
  (update [this meta-describe-client metadata])
  (upsert [this meta-describe-client metadata])
  (delete [this meta-describe-client metadata])
  (rename [this meta-describe-client type old-name new-name]))

(defprotocol MetadataDescribeClient
  (describe [this])
  (describe-type [this type]))
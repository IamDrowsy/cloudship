(ns ^{:doc "Transforms a keyword to a basic org property map"}
  cloudship.connection.props.keyword
  (:require [instaparse.core :as insta]
            [clojure.spec.alpha :as s]
            [cloudship.connection.props.load :as l]
            [cloudship.connection.props.spec :as prop-spec]))

(def kw-grammar
  "kw = org sandbox-part? flags
   <ident> = #'[A-Za-z0-9_]+'
   org = ident
   <sandbox-part> = <'-'> sandbox
   sandbox = ident
   flags = flag*
   flag = <'.'> ident flag-opt?
   flag-opt = <':'> ident")

(def parser
  (insta/parser kw-grammar))

(defn parses? [kw]
  (not (insta/failure? (insta/parse parser (name kw)))))


(s/def ::flag-name string?)
(s/def ::flag-opt string?)
(s/def ::flag-map (s/keys :req-un [::flag-name] :opt-un [::flag-opt]))
(s/def ::flag (s/or :map ::flag-name
                    :string ::flag-map))
(s/def ::flags (s/* ::flag))

(s/def ::parsed-kw (s/keys :req-un [::prop-spec/org ::prop-spec/full]
                           :opt-un [::prop-spec/sandbox ::flags]))

(s/def ::prop-kw (s/and keyword? parses?))

(defn parse-keyword
  "Takes a keyword and parses it into a map"
  [kw]
  (insta/transform
    {:flag (fn [flag & opt?]
             (if opt? {:flag-name flag :opt (second (first opt?))}
                      flag))
     :flags (fn [& flags]
              [:flags (vec flags)])
     :kw (fn [& parts]
           (into {} parts))}
    (merge (insta/parse parser (name kw))
           {:full kw})))

(s/fdef parse-keyword
        :args (s/cat :prop-kw ::prop-kw)
        :ret ::parsed-kw)

(defn- find-and-merge-props [{:keys [org sandbox] :as kw-props}]
  (let [all-props (l/slurp-and-merge-props)
        base-props (dissoc all-props :orgs)
        org-props (dissoc (get-in all-props [:orgs (keyword org)] {}) :sandboxes)
        sandbox-props (get-in all-props [:orgs (keyword org) :sandboxes (keyword sandbox)] {})]
    (l/deep-merge base-props org-props sandbox-props kw-props)))

(defn kw->props
  "Takes a keyword, parses it into a map and merges the configured props into it"
  [kw]
  (find-and-merge-props (parse-keyword kw)))


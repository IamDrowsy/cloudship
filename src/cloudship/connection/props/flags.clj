(ns cloudship.connection.props.flags
  (:require [cloudship.connection.props.flags.ciphercloud :as cc]
            [cloudship.connection.props.flags.version :as v]
            [cloudship.connection.props.flags.keepass :as kp]))

(defmulti resolve-flag (fn [flag]
                         (if (map? flag)
                           (:flag-name flag)
                           flag)))

(defn- pop-if-current
  "Returns a list of flags without the first element if it matches the given flag"
  [flag flags]
  (if (= flag (first flags))
    (rest flags)
    flags))

(defn expand-props [{:keys [flags] :as con-props}]
  (loop [[current-flag & rest-flags] flags
         props con-props]
    (if current-flag
      (let [f (resolve-flag current-flag)
            new-props (f props)]
        (recur rest-flags (-> new-props
                              (update :flags #(pop-if-current current-flag %))
                              (update :resolved-flags (fnil conj []) current-flag))))
      (dissoc props :flags))))

(defmethod resolve-flag
  "v"
  [this] (v/resolve-version-flag this))

(defmethod resolve-flag
  "cc"
  [this] (cc/resolve-cc-flag))

(defmethod resolve-flag
  "kp"
  [this] (kp/resolve-kp-flag))

(defmethod resolve-flag
  "sfdx"
  [this] (resolve-flag {:flag-name "auth-method" :opt "sfdx"}))

(defmethod resolve-flag
  "auth"
  [this] (resolve-flag {:flag-name "auth-method" :opt (:opt this)}))

(defmethod resolve-flag
  :default
  [this] (fn [config]
           (let [flag (keyword (:flag-name this))]
             (if (map? this)
               (assoc config flag (:opt this))
               (assoc config flag true)))))

(ns cloudship.util.keepass
  (:require [cloudship.util.user-interact :as uio]
            [taoensso.timbre :refer [debug debugf log-and-rethrow-errors warnf]])
  (:import [de.slackspace.openkeepass KeePassDatabase]
           [de.slackspace.openkeepass.domain Group KeePassFile Entry]))

(def open-databases
  (atom {}))

(defn reset-dbs! []
  (reset! open-databases {}))

(defn- open-database!
  [^String database ^String password]
  (if password (debug "Using given password"))
  (log-and-rethrow-errors
    (let [db (.openDatabase (KeePassDatabase/getInstance database)
                            ^String (or password (uio/prompt-password database)))]
      (swap! open-databases assoc database db)
      db)))

(defn- resolve-database [database password]
  (if-let [db (get @open-databases database)]
    (do (debugf "found open database %s in cache" database)
        db)
    (do (debugf "opening database %s" database)
        (open-database! database password))))

(defprotocol HasSubgroups
  (subgroup [this name]))

(extend-protocol HasSubgroups
  Group
  (subgroup [this name]
    (let [groups (filter #(= (.getName ^Group %) name) (.getGroups this))]
      (if (empty? groups)
        (throw (ex-info (format "Cannot find subgroup %s" name)
                        {:name name, :parent this}))
        (first groups))))
  KeePassFile
  (subgroup [this name]
    (let [group (.getGroupByName this name)]
      (or group
          (throw (ex-info (format "Cannot find group %s" name)
                          {:name name :file this}))))))

(defn- ^Entry entry* [database path-to-entry password]
  (let [db (resolve-database database password)]
    (loop [g db
           [f & r] path-to-entry]
      (if (empty? r)
        (.getEntryByTitle g f)
        (recur (subgroup g f) r)))))

(defn entry
  "Returns a map of the given keepass entry."
  ([database path-to-entry]
   (entry database path-to-entry nil))
  ([database path-to-entry password]
   (let [e (entry* database path-to-entry password)]
     (if e
       (select-keys (bean e) [:username :password :url :notes :name :uuid])
       (throw (ex-info (format "Cannot find entry %s in database %s" path-to-entry database)
                       {:database database :path-to-entry path-to-entry}))))))

(defn entry-history
  "Returns the list of entries in ascending order (starting with earliest)"
  ([database path-to-entry]
   (entry-history database path-to-entry nil))
  ([database path-to-entry password]
   (let [e (entry* database path-to-entry password)
         history (if e (.getHistoricEntries (.getHistory e)) [])]
     (map #(select-keys (bean %) [:username :password :url :notes :name :uuid])
          history))))
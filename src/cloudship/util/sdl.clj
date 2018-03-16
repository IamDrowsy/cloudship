(ns cloudship.util.sdl
  (:require [clojure.java.io :as io]
            [com.rpl.specter :refer :all]
            [clojure.string :as str])
  (:import (java.io Reader)
           (java.util Properties)))

(defn- load-props
  [file-name]
  (with-open [^Reader reader (io/reader file-name)]
    (let [props (Properties.)]
      (.load props reader)
      (into {} (for [[k v] props] [(keyword k) (keyword (read-string v))])))))

(defn load-sdl
  [file-name]
  (->> (load-props file-name)
       (setval [MAP-VALS NAME (regex-nav #":")] ".")))

(defn apply-sdl [mapping-or-file maps]
  (let [mapping (if (string? mapping-or-file)
                  (load-sdl mapping-or-file)
                  mapping-or-file)
        mapping-fn (fn [key] (mapping key NONE))]
    (transform [ALL MAP-KEYS] mapping-fn maps)))

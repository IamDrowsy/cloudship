(ns cloudship.connection.props.flags.windp
  (:require [com.rpl.specter :refer [transform submap MAP-VALS]]
            [cloudship.connection.props.flags :as cf])
  (:import (com.github.windpapi4j WinDPAPI WinDPAPI$CryptProtectFlag)
           (java.security SecureRandom)
           (java.util Base64)))

(def ^:dynamic *salt-size* 16)

(defn- dpapi []
  (WinDPAPI/newInstance (into-array WinDPAPI$CryptProtectFlag [(WinDPAPI$CryptProtectFlag/CRYPTPROTECT_UI_FORBIDDEN)])))

(defn- salt
  "Returns a random salt of *salt-size* bytes"
  []
  (let [sr (SecureRandom/getInstanceStrong)
        ba (byte-array *salt-size*)]
    (.nextBytes sr ba)
    ba))

(defn- byte-array->base64 [^bytes ba]
  (String. (.encode (Base64/getEncoder) ba)))

(defn- base64->byte-array [^String s]
  (.decode (Base64/getDecoder) s))

(defn encrypt
  "Encrypts a String using the windows dpapi and a random salt.

  Returns a Base64 encoded string of the salt and the encrypted value"
  [s]
  (let [salt-bytes (salt)
        api (dpapi)
        encrypted (if (zero? *salt-size*)
                    (.protectData api (.getBytes s))
                    (.protectData api (.getBytes s) salt-bytes))]
    (if (zero? *salt-size*)
      (byte-array->base64 (byte-array encrypted))
      (byte-array->base64 (byte-array (concat salt-bytes encrypted))))))

(defn decrypt
  "Decrypts a Base64 encoded string (with an attached salt) using the windows dpapi"
  [^String base64]
  (let [api (dpapi)
        ba (base64->byte-array base64)
        salt (byte-array (take *salt-size* ba))
        value (byte-array (drop *salt-size* ba))]
    (if (zero? *salt-size*)
      (String. (.unprotectData api value))
      (String. (.unprotectData api value salt)))))

(defn reader
  [arg]
  (cond (string? arg) (decrypt arg)
        :else arg))

(defn resolve-windp-flag [{:keys [fields]}]
  (fn [props]
    (transform [(submap fields) MAP-VALS] decrypt props)))

(defmethod cf/resolve-flag
  "windp"
  [this] (resolve-windp-flag this))
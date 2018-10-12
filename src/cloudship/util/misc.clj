(ns ^:no-doc cloudship.util.misc
  (:require [taoensso.timbre :as t]))

(defn normalize-simple-var-args
  "Normalizes varargs by flattening sequential inputs
  so [[1 2 3] 1 2 [1 2]] becomes [1 2 3 1 2 1 2].
  Only works for one level."
  [var-args]
  (mapcat #(if (sequential? %) % [%]) var-args))

(defn safe-list->map
  "Creates a map from a list by using key-fn as key and val-fn (default: identity) as value.
  Throws error if keys are not unique and entries would by overwritten."
  ([list key-fn]
   (safe-list->map list key-fn identity))
  ([list key-fn val-fn]
   (reduce (fn [m e]
             (if (m (key-fn e))
               (throw (ex-info (str "Value for " (key-fn e) " not unique. Overwriting: "
                                    (m (key-fn e)) " with " (val-fn e) ".")
                               {:key       (key-fn e)
                                :old-entry (m (key-fn e))
                                :new-entry (val-fn e)}))
               (assoc m (key-fn e) (val-fn e))))
           {}
           list)))
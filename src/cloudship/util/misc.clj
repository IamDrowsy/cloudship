(ns cloudship.util.misc)

(defn normalize-simple-var-args
  "Normalizes varargs by flattening sequential inputs
  so [[1 2 3] 1 2 [1 2]] becomes [1 2 3 1 2 1 2].
  Only works for one level."
  [var-args]
  (mapcat #(if (sequential? %) % [%]) var-args))
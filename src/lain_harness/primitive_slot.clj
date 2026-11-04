(ns lain-harness.primitive-slot
  "Read a field from a supported plain record, without connected slot topology."
  (:require [propagators.cells.value :as value]
            [propagators.compiler-2.session.operators :as ops]))

(defn- field-value [key record]
  (when-not (map? record)
    (throw (ex-info "prim-slot expects a plain record" {})))
  (get record key value/nothing))

(defn primitive-bindings []
  [['prim-slot (ops/prim-op (fn [make] (make field-value)))]])

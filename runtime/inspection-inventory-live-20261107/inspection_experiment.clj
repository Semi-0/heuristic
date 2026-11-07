(ns lain-harness.inspection-experiment
  "Finite inspection adapters over existing lexical observations and cell errors."
  (:require [lain-harness.connected-data :as cd]
            [propagators.cells.error-layer :as errors]
            [propagators.compiler-2.session :as session]
            [propagators.experimental.amb.observation :as observation]
            [propagators.network :as net]))

(defn cell-errors [network reference]
  (let [raw (net/network-cell-strongest network reference)]
    (into (errors/errors-of raw) (errors/errors-of (observation/base raw)))))

(defn facilities []
  (session/session-extension
   {:id ::facilities
    :primitives
    {'inspect:errors
     (cd/observation-operator
      'inspect:errors (fn [network [reference]] (vec (cell-errors network reference)))
      identity (fn [_ _ [reference]] [reference]) true)}}))

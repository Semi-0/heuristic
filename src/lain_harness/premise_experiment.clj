(ns lain-harness.premise-experiment
  "Experimental adapters over existing TTMS operators; no new support algebra."
  (:require [lain-harness.connected-data :as cd]
            [propagators.cells.value :as value]
            [propagators.datastructures.layered-value :as datum]
            [propagators.stdlib.premise-state :as state]
            [propagators.experimental.amb.observation :as observation]
            [propagators.experimental.ttms-application :as application]
            [propagators.experimental.ttms-primitives :as primitives]
            [propagators.compiler-2.runtime.session.extension :as extension]
            [propagators.compiler-2.session :as session]
            [propagators.network :as net]))

(defn inspect-value [network reference]
  (let [v (net/network-cell-strongest network reference)
        base (observation/base v)]
    {:value (cond (value/nothing? base) :unknown
                  (value/contradiction? base) :contradictory
                  :else base)
     :support (datum/support-of v)
     :premise-states (state/states-of v)}))

(defn facilities []
  (session/session-extension
   {:id ::facilities
    :primitives
    (into {'inspect:premises
           (cd/observation-operator 'inspect:premises
                                    (fn [network [reference]] (inspect-value network reference))
                                    identity (fn [_ _ [reference]] [reference]) true)}
          (map (fn [[name implementation]] [(symbol (str "ttms:" (if (= name '/) "divide" name))) implementation]))
          (concat (extension/extension-bindings primitives/session-extension)
                  (extension/extension-bindings application/session-extension)))}))

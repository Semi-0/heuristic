(ns lain-harness.lexical-observation
  "Connected lexical metadata, using the existing relationship observer lifetime.
  Selection across lexical parents and presentation belong to Lain."
  (:require [lain-harness.environment :as environment]
            [lain-harness.connected-data :as cd]
            [propagators.datastructures.layered-value :as datum]
            [propagators.cells.value :as value]
            [propagators.compiler-2.model.env :as env]

            [propagators.compiler-2.session :as session]
            [propagators.compiler-2.session.connected :as connected]

            [propagators.experimental.visualization.data :as data]

            [propagators.network :as net]))

(defn local-facts
  ([network e] (local-facts network e (environment/local-bindings network (:id e))))
  ([network e live]
   (let [target (:id e)
         declared (into {} (map (fn [[name binding]] [name (env/binding-id binding)]))
                        (get-in e [:frames target :bindings]))
         facts (mapv (fn [[name id]]
                       (let [v (net/network-cell-strongest network id)]
                         {:name (str name) :cell id :frame target
                          :state (cond (value/contradiction? v) :contradictory
                                       (value/nothing? v) :unknown :else :known)
                          :available (not (value/unusable? v))
                          :value (if (value/unusable? v) :unavailable (data/payload v)) :premises (datum/support-of v)}))
                     (sort-by (comp str key) (merge declared live)))]
     facts)))

(defn fact-list [facts]
  (reduce (fn [tail fact] {:car fact :cdr tail}) connected/empty-list (reverse facts)))

(defn bindings-operator []
  (cd/observation-operator
   'lexical:bindings (fn [network [e]] (local-facts network e)) fact-list
   (fn [network _ [e]]
     (when-not (value/unusable? e) (map :cell (local-facts network e)))) true))

(defn primitive-bindings []
  [['lexical:bindings (bindings-operator)]])

(defn extension []
  (session/session-extension
   {:id ::bindings
    :imports {["lain_harness/lexical_observation.clj" ::primitive-bindings 1] [::bindings 1]}
    :primitives (primitive-bindings)}))

(defn parent-bindings []
  [['lexical:parent
    (connected/primitive 'lexical:parent (fn [e]
                                           (if-let [parent (get-in e [:frames (:id e) :parent])]
                                             (assoc e :id parent) ::root)))]])

(defn parent-extension []
  (session/session-extension
   {:id ::parent
    :imports {["lain_harness/lexical_observation.clj" ::parent-bindings 1] [::parent 1]}
    :primitives (parent-bindings)}))

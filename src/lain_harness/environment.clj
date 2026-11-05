(ns lain-harness.environment
  "Native lexical frame declarations and change observations. No fixed domain schema."
  (:require [propagators.compiler-2.compiler.basis :as basis]
            [propagators.compiler-2.model.env :as env]
            [propagators.compiler-2.model.env.index :as index]

            [propagators.compiler-2.session.connected :as connected]
            [propagators.compiler-2.model.operator-value :as operator]

            [propagators.network :as net]
            [propagators.ids :as ids]))

(defrecord Environment [id frames])
(defrecord Contribution [name cell])
(defn reference [id] (->Environment id {}))
(defn child [parent id bindings]
  (when-not (instance? Environment parent)
    (throw (ex-info "Expected a native lexical environment reference" {})))
  (->Environment id (assoc (:frames parent) id {:parent (:id parent) :bindings bindings})))
(defn bindings [root]
  {'empty-env (connected/primitive 'empty-env (fn [] (reference root)))
   'lexical-child
   (operator/propagator-operator
    {:name 'lexical-child
     :activate (fn [network inputs outputs context]
                 ((operator/operator-activate
                   (connected/primitive 'lexical-child
                                        (fn [parent] (child parent (basis/stable-node-id :harness-child inputs (first outputs)) []))))
                  network context inputs (first outputs)))})
   'env:reference-slot
   (connected/primitive 'env:reference-slot
                        (fn [name reference]
                          (when-not (ids/node-id? reference)
                            (throw (ex-info "env:reference-slot expects a cell reference" {})))
                          (->Contribution (symbol name) reference)))
   'env:slot
   (operator/propagator-operator
    {:name 'env:slot
     :activate (fn [network arguments [output] context]
                 (when-not (= 2 (count arguments))
                   (throw (ex-info "env:slot expects name and content" {})))
                 ((operator/operator-activate
                   (connected/primitive 'env:slot
                                        (fn [name] (->Contribution (symbol name) (second arguments)))))
                  network context [(first arguments)] output))})
   'extend-env
   (operator/propagator-operator
    {:name 'extend-env
     :activate (fn [network inputs outputs context]
                 ((operator/operator-activate
                   (connected/primitive
                    'extend-env
                    (fn [parent contribution]
                      (when-not (instance? Contribution contribution)
                        (throw (ex-info "Expected binding contribution" {})))
                      (child parent (basis/stable-node-id :harness-extension inputs (first outputs))
                             [[(:name contribution) (env/cell-binding (:cell contribution))]]))))
                  network context inputs (first outputs)))})})

(defn local-bindings [network target]
  (let [static (:frames (net/network-dict-entry network env/lexical-topology-key))
        runtime (index/runtime-topology network)
        names (into (set (keys (get-in static [target :bindings])))
                    (keep (fn [[k _]] (when (and (= :binding (first k)) (= target (second k))) (nth k 2))) runtime))]
    (into {} (keep (fn [name] (when-let [id (env/local-binding-id network target name)] [name id]))) names)))

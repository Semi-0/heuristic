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
(defn local-bindings-in
  "Resolve several frames with one pass over the runtime binding index."
  [network targets]
  (if (empty? targets) {}
      (let [static (:frames (net/network-dict-entry network env/lexical-topology-key))
            runtime (index/runtime-topology network)
            grouped (reduce (fn [by-frame [key id]]
                              (if (and (= :binding (first key)) (contains? targets (second key)))
                                (update-in by-frame [(second key) (nth key 2)] (fnil conj #{}) id)
                                by-frame)) {} runtime)]
        (into {} (for [target targets
                       :let [frame (get static target)
                             live (get grouped target)
                             names (into (set (keys (:bindings frame))) (keys live))]]
                   [target
                    (into {} (keep (fn [name]
                                     (let [candidates (into (set (get-in frame [:bindings name])) (get live name))
                                           id (or (get-in frame [:current-bindings name])
                                                  (get runtime [:current-binding target name])
                                                  (when (= 1 (count candidates)) (first candidates)))]
                                       (when id [name id]))) names))])))))
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
  (get (local-bindings-in network #{target}) target {}))

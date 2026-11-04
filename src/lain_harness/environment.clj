(ns lain-harness.environment
  "Native lexical frame declarations and change observations. No fixed domain schema."
  (:require [propagators.compiler-2.compiler.basis :as basis]
            [propagators.compiler-2.model.env :as env]
            [propagators.compiler-2.model.env.index :as index]
            [propagators.compiler-2.session.operators :as ops]
            [propagators.compiler-2.model.operator-value :as operator]
            [propagators.experimental.visualization.data :as data]
            [propagators.cells.value :as value]
            [propagators.network :as net]
            [propagators.message :as message]))

(defrecord Environment [id frames])
(defrecord Contribution [name cell])
(defn reference [id] (->Environment id {}))
(defn child [parent id bindings]
  (when-not (instance? Environment parent)
    (throw (ex-info "Expected a native lexical environment reference" {})))
  (->Environment id (assoc (:frames parent) id {:parent (:id parent) :bindings bindings})))
(defn bindings [root]
  {'empty-env (ops/prim-op (fn [p] (p (fn [] (reference root)))))
   'lexical-child (ops/prim-op
                   (fn [p] (fn [cells]
                             ((p (fn [parent] (child parent (basis/stable-node-id :harness-child cells) []))) cells))))
   'env:slot (operator/operator-closure
              {:name 'env:slot
               :compiler-activate
               (fn [_ network _ arguments output]
                 (when-not (= 2 (count arguments)) (throw (ex-info "env:slot expects name and content" {})))
                 (let [name (data/payload (net/network-cell-strongest network (first arguments)))]
                   (if (value/unusable? name) {:messages []}
                       {:messages [(message/message output (->Contribution (symbol name) (second arguments)))]})))})
   'extend-env (ops/prim-op
                (fn [p] (fn [cells]
                          ((p (fn [parent contribution]
                                (when-not (instance? Contribution contribution)
                                  (throw (ex-info "Expected binding contribution" {})))
                                (child parent (basis/stable-node-id :harness-extension cells)
                                       [[(:name contribution) (env/cell-binding (:cell contribution))]]))) cells))))})

(defn local-bindings [network target]
  (let [static (:frames (net/network-dict-entry network env/lexical-topology-key))
        runtime (index/runtime-topology network)
        names (into (set (keys (get-in static [target :bindings])))
                    (keep (fn [[k _]] (when (and (= :binding (first k)) (= target (second k))) (nth k 2))) runtime))]
    (into {} (keep (fn [name] (when-let [id (env/local-binding-id network target name)] [name id]))) names)))

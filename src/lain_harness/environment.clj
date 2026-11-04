(ns lain-harness.environment
  "Native lexical frame declarations and change observations. No fixed domain schema."
  (:require [propagators.compiler-2.compiler.basis :as basis]
            [propagators.compiler-2.model.env :as env]
            [propagators.compiler-2.model.env.index :as index]
            [propagators.compiler-2.session :as session]
            [propagators.compiler-2.session.operators :as ops]
            [propagators.compiler-2.model.operator-value :as operator]
            [propagators.compiler-2.runtime.application :as application]
            [propagators.datastructures.compound-object :as obj]
            [propagators.datastructures.support :as support]
            [propagators.experimental.visualization.data :as data]
            [propagators.datastructures.layered-value :as datum]
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

(defn visible-bindings [network target]
  (let [static (:frames (net/network-dict-entry network env/lexical-topology-key))
        runtime (index/runtime-topology network)]
    (loop [frame target seen #{} result {}]
      (if (or (nil? frame) (seen frame)) result
          (let [names (into (set (keys (get-in static [frame :bindings])))
                            (keep (fn [[k _]] (when (and (= :binding (first k)) (= frame (second k))) (nth k 2))) runtime))
                local (into {} (keep (fn [name] (when-let [id (env/local-binding-id network frame name)] [name id]))) names)
                parent (or (get-in static [frame :parent-id]) (get runtime [:frame frame :parent-id]))]
            (recur parent (conj seen frame) (merge local result)))))))

(defn describe-value
  "Bounded serialization view; live values and accessors remain in the network."
  [network v depth]
  (let [base (data/payload v) premises (datum/support-of v)]
    (cond
      (value/unusable? v) {:view :unavailable :support #{}}
      (application/compiler-callable? base) {:view :callable :support premises}
      (and (net/net? base) (pos? depth))
      (let [keys (take 64 (sort-by pr-str (obj/accessor-slot-keys base)))
            fields (into {} (map (fn [key] [key (describe-value network (data/slot-value network base key) (dec depth))])) keys)]
        {:view (into {} (map (fn [[key result]] [key (:view result)])) fields)
         :support (apply support/combine premises (map :support (vals fields)))})
      (net/net? base) {:view :compound-depth-limit :support premises}
      :else {:view base :support premises})))

(defn snapshot [s target]
  (let [network (session/network s)
        ledger (:environment/source-ledger (session/compiler-state s))]
    (into (sorted-map)
          (map (fn [[name id]]
                 (let [v (net/network-cell-strongest network id) description (describe-value network v 4)]
                   [name {:binding name :cell id :available? (not (value/unusable? v))
                          :value v :view (:view description) :support (:support description)
                          :origin (last (filter (fn [{:keys [target form]}]
                                                  (and target (seq? form) (= 'define (first form))
                                                       (= name (second form))
                                                       (= id (env/local-binding-id network target name)))) ledger))}])))
          (visible-bindings network target))))

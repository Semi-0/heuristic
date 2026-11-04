(ns lain-harness.lexical-observation
  "Connected lexical metadata, using the existing relationship observer lifetime.
  Selection across lexical parents and presentation belong to Lain."
  (:require [lain-harness.environment :as environment]
            [propagators.cells.value :as value]
            [propagators.compiler-2.compiler.basis :as basis]
            [propagators.compiler-2.model.env :as env]
            [propagators.compiler-2.model.operator-value :as operator]
            [propagators.compiler-2.runtime.declaration-effects :as declarations]
            [propagators.compiler-2.session :as session]
            [propagators.compiler-2.session.operators :as ops]
            [propagators.datastructures.compound-object :as object]
            [propagators.datastructures.support-collection :as collection]
            [propagators.message :as message]
            [propagators.experimental.ttms-publication :as publication]
            [propagators.experimental.visualization.data :as data]
            [propagators.gur.flat :as gur]
            [propagators.network :as net]
            [propagators.propagator :as prop]
            [propagators.relationship-observer :as observer]))

(deftype Snapshot [facts]
  Object
  (equals [_ other] (and (instance? Snapshot other) (= facts (.-facts ^Snapshot other))))
  (hashCode [_] (hash facts)))

(defn local-facts [network e]
  (let [target (:id e)
        declared (into {} (map (fn [[name binding]] [name (env/binding-id binding)]))
                       (get-in e [:frames target :bindings]))
        live (into {} (keep (fn [[name _]]
                              (when-let [id (env/local-binding-id network target name)] [name id])))
                   (environment/visible-bindings network target))
        facts (mapv (fn [[name id]]
                      (let [v (net/network-cell-strongest network id)
                            projection (environment/describe-value network v 4)]
                        {:name (str name) :cell id :frame target
                         :available (not (value/unusable? v))
                         :value (:view projection) :support (:support projection)}))
                    (sort-by (comp str key) (merge declared live)))]
    (Snapshot. facts)))

(defn fact-list [snapshot]
  (reduce (fn [tail fact]
            (object/as-accessor-network
             {:car (object/as-accessor-network fact) :cdr tail}))
          basis/list-empty-marker (reverse (.-facts ^Snapshot snapshot))))

(defn bindings-operator []
  (operator/operator-closure
   {:name 'lexical:bindings
    :application-installer
    (fn [network [input] output _context]
      (let [source (basis/stable-node-id :lexical-facts-source output)
            prepared (reduce declarations/ensure-cell network [source output])
            [_initial installed] ((observer/p:observe-roots [input] source output) prepared)
            sample (fn [current]
                     (let [e (data/payload (net/network-cell-strongest current input))]
                       (if (value/unusable? e) (Snapshot. [])
                           (local-facts current e))))
            stamped installed
            activate (fn [_ _ current]
                       [(message/message source
                                         (collection/content
                                          (publication/next-source-datum source
                                                                         (net/network-cell-strongest current source)
                                                                         (sample current) :active)))])
            watchers (for [[id entry] (net/net-env stamped)
                           :when (and (prop/prop? entry)
                                      (= observer/observer-name (prop/prop-name entry))
                                      (contains? (:outputs (get (net/net-graph stamped) id)) source))] id)
            stamped (reduce (fn [n id]
                              (net/assoc-net-prop n id
                                                  (assoc (net/network-env-lookup n id) :activate activate))) stamped watchers)
            [_ registered] (declarations/eval-activation-result
                            [(gur/bind-name [:relationship/root-observers]
                                            [[input] source output] source)] stamped)
            [forward installed]
            ((prop/construct-propagator ::facts
                                        (fn [_ _ current]
                                          (let [snapshot (data/payload (net/network-cell-strongest current source))]
                                            (if (value/unusable? snapshot) []
                                                (basis/primitive-reply-messages current [source] output (fact-list snapshot)))))
                                        [source] [output]) registered)
            watchers (conj (vec watchers) forward)]
        [(declarations/register-props installed watchers) watchers output]))}))

(defn primitive-bindings []
  [['lexical:bindings (bindings-operator)]])

(defn extension []
  (session/session-extension
   {:id ::bindings
    :imports {["lain_harness/lexical_observation.clj" ::primitive-bindings 1] [::bindings 1]}
    :primitives (primitive-bindings)}))

(defn parent-bindings []
  [['lexical:parent
    (ops/prim-op (fn [p] (p (fn [e]
                              (when-let [parent (get-in e [:frames (:id e) :parent])]
                                (assoc e :id parent))))))]])

(defn parent-extension []
  (session/session-extension
   {:id ::parent
    :imports {["lain_harness/lexical_observation.clj" ::parent-bindings 1] [::parent 1]}
    :primitives (parent-bindings)}))

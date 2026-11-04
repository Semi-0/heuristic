(ns lain-harness.lexical-observation
  "Connected lexical metadata, using the existing relationship observer lifetime.
  Selection across lexical parents and presentation belong to Lain."
  (:require [lain-harness.environment :as environment]
            [propagators.datastructures.layered-value :as datum]
            [propagators.cells.value :as value]
            [propagators.compiler-2.compiler.basis :as basis]
            [propagators.compiler-2.model.env :as env]
            [propagators.compiler-2.model.operator-value :as operator]
            [propagators.compiler-2.runtime.declaration-effects :as declarations]
            [propagators.compiler-2.session :as session]
            [propagators.compiler-2.session.connected :as connected]
            [propagators.compiler-2.session.operators :as ops]
            [propagators.datastructures.support-collection :as collection]
            [propagators.message :as message]
            [propagators.experimental.ttms-publication :as publication]
            [propagators.experimental.visualization.data :as data]
            [propagators.gur.flat :as gur]
            [propagators.network :as net]
            [propagators.propagator :as prop]))

(deftype Snapshot [facts]
  Object
  (equals [_ other] (and (instance? Snapshot other) (= facts (.-facts ^Snapshot other))))
  (hashCode [_] (hash facts)))

(defn local-facts [network e]
  (let [target (:id e)
        declared (into {} (map (fn [[name binding]] [name (env/binding-id binding)]))
                       (get-in e [:frames target :bindings]))
        live (environment/local-bindings network target)
        facts (mapv (fn [[name id]]
                      (let [v (net/network-cell-strongest network id)]
                        {:name (str name) :cell id :frame target
                         :available (not (value/unusable? v))
                         :value (if (value/unusable? v) :unavailable (data/payload v)) :support (datum/support-of v)}))
                    (sort-by (comp str key) (merge declared live)))]
    (Snapshot. facts)))

(defn fact-list [snapshot]
  (let [facts (.-facts ^Snapshot snapshot)]
    (if (empty? facts) (connected/frozen basis/list-empty-marker)
        (connected/frozen (reduce (fn [tail fact] {:car fact :cdr tail}) basis/list-empty-marker (reverse facts))))))

(defn bindings-operator []
  (operator/operator-closure
   {:name 'lexical:bindings
    :application-installer
    (fn [network [input] output _context]
      (let [source (basis/stable-node-id :lexical-facts-source output)
            prepared (reduce declarations/ensure-cell network [source output])
            activate (fn activate [_ _ current]
                       (let [e (data/payload (net/network-cell-strongest current input))
                             snapshot (when-not (value/unusable? e) (local-facts current e))]
                         (if-not snapshot {:messages []}
                                 {:messages [(message/message source (collection/content
                                                                      (publication/next-source-datum source
                                                                                                     (net/network-cell-strongest current source) snapshot :active)))]
                                  :effects (mapv (fn [fact]
                                                   (gur/declare-prop (basis/stable-node-id :lexical-watch output (:cell fact))
                                                                     :lexical-watch [(:cell fact)] [source] activate))
                                                 (.-facts ^Snapshot snapshot))})))
            [watch prepared] ((prop/construct-propagator :lexical-sample activate [input] [source]) prepared)
            [_ registered] (declarations/eval-activation-result
                            [(gur/bind-name [:session :samples] output watch)] prepared)
            [forward installed] ((prop/construct-propagator ::facts
                                                            (fn [_ _ current]
                                                              (let [snapshot (data/payload (net/network-cell-strongest current source))]
                                                                (if (value/unusable? snapshot) []
                                                                    (basis/primitive-reply-messages current [source] output (fact-list snapshot)))))
                                                            [source] [output]) registered)]
        [(declarations/register-props installed [watch forward]) [watch forward] output]))}))

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
                              (if-let [parent (get-in e [:frames (:id e) :parent])]
                                (assoc e :id parent) ::root)))))]])

(defn parent-extension []
  (session/session-extension
   {:id ::parent
    :imports {["lain_harness/lexical_observation.clj" ::parent-bindings 1] [::parent 1]}
    :primitives (parent-bindings)}))

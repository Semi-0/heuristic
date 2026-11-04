(ns lain-harness.connected-data
  "Finite connected data snapshots and cell identities; no scheduling policy."
  (:require [propagators.cells.value :as value]
            [propagators.datastructures.compound-object :as object]
            [propagators.compiler-2.compiler.basis :as basis]
            [propagators.compiler-2.model.operator-value :as operator]
            [propagators.compiler-2.runtime.declaration-effects :as declarations]
            [propagators.compiler-2.session.connected :as connected]
            [propagators.datastructures.support-collection :as collection]
            [propagators.experimental.ttms-publication :as publication]
            [propagators.experimental.visualization.data :as data]
            [propagators.gur.flat :as gur]
            [propagators.message :as message]
            [propagators.network :as net]
            [propagators.propagator :as prop]))

(def unpack connected/payload)
(defn packed [v] (if (or (= v basis/list-empty-marker) (and (coll? v) (not (record? v)))) (connected/frozen v) v))
(defn record-value [& fields] (connected/frozen (into {} (map (fn [[k v]] [k (unpack v)])) (partition 2 fields))))
(defn list-value [& xs]
  (packed (reduce (fn [tail x] {:car (unpack x) :cdr tail}) basis/list-empty-marker (reverse xs))))
(defn field-value [key v] (packed (get (unpack v) key)))
(defn merge-values [a b] (connected/frozen (merge (unpack a) (unpack b))))

(defn value-cells [network input]
  (loop [pending [input] seen #{}]
    (if-let [id (first pending)]
      (if (contains? seen id) (recur (rest pending) seen)
          (let [v (data/payload (net/network-cell-strongest network id))
                children (when (object/accessor-network? v)
                           (mapcat #(object/accessor-parent-ids v %) (object/accessor-slot-keys v)))]
            (recur (concat (rest pending) children) (conj seen id)))) seen)))

(defn observation-operator [name sample project watch-cells refresh?]
  (operator/operator-closure
   {:name name
    :application-installer
    (fn [network inputs output _context]
      (let [source (basis/stable-node-id name output)
            prepared (reduce declarations/ensure-cell network [source output])
            activate (fn activate [_ _ current]
                       (let [values (mapv #(data/payload (net/network-cell-strongest current %)) inputs)
                             sampled (if (some value/unusable? values) connected/pending (sample current values))
                             snapshot (connected/frozen sampled)]
                         {:messages (if (= connected/pending sampled) []
                                        [(message/message source (collection/content
                                                                  (publication/next-source-datum source
                                                                                                 (net/network-cell-strongest current source) snapshot :active)))])
                          :effects (mapv (fn [id]
                                           (gur/declare-prop (basis/stable-node-id :sample-watch output id)
                                                             :sample-watch [id] [source] activate))
                                         (watch-cells current inputs values))}))
            [watch prepared] ((prop/construct-propagator name activate inputs [source]) prepared)
            [_ prepared] (if refresh?
                           (declarations/eval-activation-result [(gur/bind-name [:session :samples] output watch)] prepared)
                           [nil prepared])
            [forward installed] ((prop/construct-propagator :snapshot-value
                                                            (fn [_ _ current]
                                                              (let [snapshot (data/payload (net/network-cell-strongest current source))]
                                                                (if (value/unusable? snapshot) []
                                                                    (basis/primitive-reply-messages current [source] output (project snapshot))))) [source] [output]) prepared)]
        [(declarations/register-props installed [watch forward]) [watch forward] output]))}))

(defn snapshot-operator []
  (observation-operator 'data:snapshot
                        (fn [network [v]] (connected/freeze network v)) identity
                        (fn [network inputs _] (mapcat #(value-cells network %) inputs)) false))

(defn reference-operator []
  (operator/operator-closure
   {:name 'cell:reference
    :compiler-activate (fn [_ _ _ [input] output]
                         {:messages [(message/message output input)]})}))

(defn value-operator []
  (operator/operator-closure
   {:name 'cell:value
    :compiler-activate
    (fn [_ network _ [reference] output]
      (let [source (data/payload (net/network-cell-strongest network reference))]
        (if (value/unusable? source) {:messages []}
            {:effects [(gur/declare-prop (basis/stable-node-id :cell-value output source)
                                         :cell-value [source] [output]
                                         (fn [_ _ current] (basis/mono-sync-messages current source output)))]})))}))

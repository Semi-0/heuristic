(ns lain-harness.connected-data
  "Finite connected data snapshots and cell identities; no scheduling policy."
  (:require [propagators.cells.value :as value]
            [propagators.datastructures.compound-object :as object]
            [propagators.compiler-2.compiler.basis :as basis]
            [propagators.compiler-2.model.operator-value :as operator]
            [propagators.compiler-2.session.connected :as connected]
            [propagators.datastructures.support-collection :as collection]
            [propagators.experimental.ttms-publication :as publication]
            [propagators.experimental.visualization.data :as data]
            [propagators.gur.flat :as gur]
            [propagators.message :as message]
            [propagators.network :as net]))

(def unpack connected/payload)
(def packed identity)
(defn record-value [& fields] (into {} (map (fn [[k v]] [k (unpack v)])) (partition 2 fields)))
(defn list-value [& xs]
  (packed (reduce (fn [tail x] {:car (unpack x) :cdr tail}) connected/empty-list (reverse xs))))
(defn field-value [key v] (packed (get (unpack v) key)))
(defn merge-values [a b] (merge (unpack a) (unpack b)))

(defn value-cells [network input]
  (loop [pending [input] seen #{}]
    (if-let [id (first pending)]
      (if (contains? seen id) (recur (rest pending) seen)
          (let [v (data/payload (net/network-cell-strongest network id))
                children (when (object/accessor-network? v)
                           (mapcat #(object/accessor-parent-ids v %) (object/accessor-slot-keys v)))]
            (recur (concat (rest pending) children) (conj seen id)))) seen)))

(defn observation-operator [name sample project watch-cells refresh?]
  (operator/propagator-operator
   {:name name
    :activate
    (fn [network inputs [output] _context]
      (letfn [(activate [_ _ current]
                (let [values (mapv #(data/payload (net/network-cell-strongest current %)) inputs)
                      sampled (if (some value/unusable? values) connected/pending (sample current values))
                      watched (distinct (concat inputs (watch-cells current inputs values)))
                      watch (basis/stable-node-id :sample-watch output)]
                  {:messages (if (= connected/pending sampled) []
                                 [(message/message output
                                                   (collection/content
                                                    (publication/next-source-datum
                                                     output (net/network-cell-strongest current output)
                                                     (project sampled) :active)))])
                   :effects (cond-> [(gur/declare-prop watch name watched [output] activate)]
                              refresh? (conj (gur/bind-name [:session :samples] output watch)))}))]
        (activate inputs [output] network)))}))

(defn snapshot-operator []
  (observation-operator 'data:snapshot
                        (fn [network [v]] (connected/freeze network v)) identity
                        (fn [network inputs _] (mapcat #(value-cells network %) inputs)) false))

(defn reference-operator []
  (operator/propagator-operator
   {:name 'cell:reference
    :activate (fn [_ [input] [output] _context]
                {:messages [(message/message output input)]})}))

(defn value-operator []
  (operator/propagator-operator
   {:name 'cell:value
    :activate
    (fn [network [reference] [output] _context]
      (let [source (data/payload (net/network-cell-strongest network reference))]
        (if (value/unusable? source) {:messages []}
            {:effects [(gur/declare-prop (basis/stable-node-id :cell-value output source)
                                         :cell-value [source] [output]
                                         (fn [_ _ current] (basis/mono-sync-messages current source output)))]})))}))

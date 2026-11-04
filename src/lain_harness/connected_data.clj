(ns lain-harness.connected-data
  "Finite connected data snapshots and cell identities; no scheduling policy."
  (:require [propagators.cells.value :as value]
            [propagators.datastructures.compound-object :as object]
            [propagators.compiler-2.compiler.basis :as basis]
            [propagators.compiler-2.model.operator-value :as operator]
            [propagators.compiler-2.session.connected :as connected]
            [propagators.datastructures.support-collection :as collection]
            [propagators.datastructures.membership-collection :as membership]
            [propagators.datastructures.support :as support]
            [propagators.experimental.ttms-publication :as publication]
            [propagators.experimental.visualization.data :as data]
            [propagators.gur.flat :as gur]
            [propagators.message :as message]
            [propagators.network :as net]
            [propagators.stdlib.premise-state :as premise-state]))

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

(defn input-operator []
  (operator/propagator-operator
   {:name 'cell:input
    :activate
    (fn [network [reference] [output] _context]
      (let [target (data/payload (net/network-cell-strongest network reference))]
        (if (value/unusable? target) []
            {:effects [(gur/declare-prop (basis/stable-node-id :cell-input output target)
                                         :cell-input [output] [target]
                                         (fn [_ _ current] (basis/mono-sync-messages current output target)))]})))}))

(defn initialize-operator []
  (operator/propagator-operator
   {:name 'source:initialize
    ;; The target is an output declared in Lain, never an input readiness gate.
    :input-selector (fn [arguments _output _context]
                      (when-not (= 2 (count arguments))
                        (throw (ex-info "source:initialize expects a target and initial value" {})))
                      [(second arguments)])
    :output-selector (fn [[target _initial] _output] target)
    :activate
    (fn [network [initial] [target] _context]
      (let [previous (net/network-cell-strongest network target)
            content (net/network-cell-strongest network initial)]
        (if (and (value/nothing? previous) (not (value/unusable? content)))
          [(message/message target
                            (collection/content
                             (publication/next-source-datum target previous
                                                            (data/payload content) :active)))]
          [])))}))

(defn boolean-operator
  "A deciding input suffices; other inputs remain watched without becoming premises.
  Otherwise the ordinary primitive waits for all operands and combines support."
  [name deciding-value combine]
  (let [activate (operator/operator-activate (connected/primitive name combine))]
    (operator/propagator-operator
     {:name name
      :activate
      (fn [network inputs [output] context]
        (let [previous (net/network-cell-content network output)
              evidence? #(or (collection/content? %) (membership/content? %))
              states (apply support/join
                            (map #(premise-state/states-of (net/network-cell-strongest network %)) inputs))
              state-update (membership/content {:premise-state states})
              bases (basis/primitive-input-values network inputs)
              deciding (first
                        (keep-indexed
                         (fn [i id]
                           (when (and (not (value/unusable? (net/network-cell-strongest network id)))
                                      (= deciding-value (boolean (nth bases i)))) id)) inputs))]
          ;; An unconditional Boolean is permanent. Supported decisions retain
          ;; every watched premise's state, independently of their value support.
          (if (boolean? previous) []
              (mapv (fn [patch]
                      (update patch :value
                              (fn [content]
                                (cond
                                  (evidence? content) (membership/merge-content content state-update)
                                  (evidence? previous)
                                  (membership/merge-content
                                   (membership/content {:base content :support #{} :membership #{}})
                                   state-update)
                                  :else content))))
                    (activate network context (if deciding [deciding] inputs) output)))))})))

(defn and-operator []
  (boolean-operator 'and false (fn [& values] (every? identity values))))

(defn or-operator []
  (boolean-operator 'or true (fn [& values] (boolean (some identity values)))))

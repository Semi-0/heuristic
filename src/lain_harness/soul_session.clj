(ns lain-harness.soul-session
  "Local assembly for environment-bootstrapped source construction. No history policy."
  (:require [clojure.string :as str]
            [lain-harness.admission :as admission]
            [lain-harness.connected-data :as cd]
            [lain-harness.environment :as environment]
            [lain-harness.lexical-observation :as lexical]
            [lain-harness.model-io-session :as model]
            [lain-harness.runtime :as runtime]
            [lain-harness.source-reader :as reader]
            [lain-harness.value-metadata :as metadata]
            [propagators.cells.value :as value]
            [propagators.compiler-2.compiler.basis :as basis]
            [propagators.stdlib.source-publication :as publication]
            [propagators.datastructures.reducer-cell :as reducer]
            [propagators.datastructures.layered-value :as datum]
            [propagators.datastructures.support :as support]
            [propagators.compiler-2.session :as session]
            [propagators.compiler-2.model.operator-value :as operator]
            [propagators.datastructures.support-collection :as collection]
            [propagators.message :refer [message]]
            [propagators.compiler-2.session.connected :as connected]
            [propagators.compiler-2.session.host :as host]
            [propagators.compiler-2.session.operators :as ops]
            [propagators.experimental.temporal-behavior :as temporal]
            [propagators.network :as net]))

(defn visible-facts [network e]
  ;; Environment frames are the advertised interface. The compiler's ambient
  ;; implementation root is not an additional model capability catalog.
  (loop [e e seen #{} facts []]
    (if-not (contains? (:frames e) (:id e)) facts
            (let [local (remove #(contains? seen (:name %)) (lexical/local-facts network e))
                  facts (into facts local)]
              (if-let [parent (get-in e [:frames (:id e) :parent])]
                (recur (assoc e :id parent) (into seen (map :name local)) facts)
                facts)))))

(defn headline [v]
  (cond
    (and (string? v) (str/starts-with? v "[{:title ")) "environment description; inspect its reference"
    (string? v) (subs v 0 (min 160 (count v)))
    (= :callable (metadata/kind v)) "callable; inspect the binding and language-introduction-cell"
    (instance? lain_harness.environment.Environment v) (str "lexical environment " (:id v))
    (= :compound (metadata/kind v)) "compound; inspect its immediate slots"
    (map? v) (str "record with keys " (pr-str (vec (sort-by str (keys v)))))
    (vector? v) (str "vector with " (count v) " elements")
    (or (keyword? v) (number? v) (boolean? v) (nil? v)) (pr-str v)
    :else (str "value of type " (.getName (class v)))))

(defn overview [network [e selected]]
  (let [selected (set selected)]
    (mapv (fn [{:keys [name cell value available state]}]
            (cond-> {:title name :reference cell :state state
                     :headline (cond (= state :contradictory) "contradictory"
                                     (not available) "unknown"
                                     :else (headline value))}
              (and available (contains? selected name))
              (assoc :content (if (or (instance? lain_harness.environment.Environment value)
                                      (#{:callable :compound} (metadata/kind value)))
                                :reference-only value))))
          (visible-facts network e))))

(defn overview-operator []
  (cd/observation-operator
   'environment:overview overview pr-str
   (fn [current _ [e]] (map :cell (visible-facts current e))) true))

(defn occurrence-operator []
  (operator/propagator-operator
   {:name 'event:point
    :activate
    (fn [network inputs [output] _]
      (when-not (= 3 (count inputs))
        (throw (ex-info "event:point expects identity, time and value" {})))
      (let [raw (mapv #(net/network-cell-strongest network %) inputs)
            values (mapv cd/unpack raw)]
        (if (or (some value/unusable? values)
                (not (apply support/compatible? (map datum/support-of raw)))) []
            (let [[id time v] values
                  source (basis/stable-node-id ::occurrence output id)]
              [(message output (publication/active source 1 (temporal/occurrence id time v)))]))))}))

(defn edge-view [network reference]
  (let [connections (get (net/net-graph network) reference)
        strongest (cd/unpack (net/network-cell-strongest network reference))
        inspected (cond (value/contradiction? strongest) value/contradiction
                        (reducer/reduced-value? strongest) (reducer/reduced-result strongest)
                        (#{:callable :compound} (metadata/kind strongest)) (metadata/kind strongest)
                        :else strongest)]
    {:edge reference :value inspected
     :inputs (:inputs connections) :outputs (:outputs connections)}))

(defn inspect-operator []
  (cd/observation-operator
   'inspect:cell (fn [network [reference]] (edge-view network reference))
   identity (fn [_ _ [reference]] [reference]) true))

(defn first-operator []
  (operator/propagator-operator
   {:name 'cell:first
    :activate (fn [network [input] [output] _]
                (let [prior (net/network-cell-strongest network output)
                      v (net/network-cell-strongest network input)]
                  (if (and (value/nothing? prior) (not (value/unusable? v)))
                    [(message output (collection/content {:base (cd/unpack v) :support #{}}))]
                    [])))}))

(defn latest-operator []
  (cd/observation-operator
   'behavior:latest-value
   (fn [_ values]
     (when-not (<= 1 (count values) 2)
       (throw (ex-info "behavior:latest-value expects a behavior and optional fallback" {})))
     (let [[behavior initial] values
           result (if (reducer/reduced-value? behavior) (reducer/reduced-result behavior) behavior)]
       (if-let [record (peek (:records result))] (:value record)
               (if (= 1 (count values)) connected/pending initial))))
   identity (fn [_ _ _] []) false))

(defn admission-interpreter [journal]
  (reify ops/EffectInterpreter
    (interpret-effect! [_ owner context {:keys [reply target support]} emit!]
      (let [cancelled (atom false)
            finished (atom false)
            task (future
                   (try
                     (when-not (and (= :reply (:kind reply)) (= "stop" (:finish-reason reply))
                                    (false? (:truncated? reply)))
                       (throw (ex-info "Failed or incomplete response cannot be admitted" {})))
                     (loop [text (:text reply) index 0 receipts []]
                       (when (or @cancelled ((:cancelled? context)))
                         (throw (InterruptedException. "Admission cancelled")))
                       (let [{:keys [status source remaining message]} (reader/read-next text true)]
                         (case status
                           :form
                           (let [id [(:request-id reply) index]
                                 completion (basis/stable-node-id ::completion (:request-id reply))
                                 declaration (admission/declaration id source target nil)
                                 extension (session/session-extension
                                            (cond-> declaration
                                              (zero? index)
                                              (assoc :cells {completion (publication/active completion 1
                                                                                            {:response (:request-id reply)
                                                                                             :source-reference support})})))
                                 receipt @(host/submit-extension! owner extension)
                                 entry {:response (:request-id reply) :form index :source source :completion-reference completion :receipt receipt}]
                             (swap! journal conj entry)
                             (if (= :admitted (:status receipt))
                               (recur remaining (inc index) (conj receipts receipt))
                               (emit! {:status :rejected :response (:request-id reply) :form index :receipts (conj receipts receipt)})))
                           :complete (emit! {:status :admitted :response (:request-id reply) :forms index :receipts receipts})
                           (throw (ex-info "Source reader rejected response" {:status status :message message})))))
                     (catch Exception error
                       (let [diagnostic {:status :rejected :response (:request-id reply) :message (ex-message error)
                                         :details (select-keys (ex-data error) [:status :message])}]
                         (swap! journal conj {:response (:request-id reply) :diagnostic diagnostic})
                         (emit! diagnostic)))
                     (finally (reset! finished true) ((:complete! context)))))]
        (fn [] (reset! cancelled true) (when-not @finished (future-cancel task)) {:status :closed})))))

(defn extension [owner journal]
  (let [admitter (admission-interpreter journal)
        prim (fn [name f] [name (connected/primitive name f)])]
    (session/session-extension
     {:id ::facilities
      :primitives
      (into (environment/bindings (session/environment (host/current-session owner)))
            [(prim 'data:record cd/record-value)
             (prim 'data:get cd/field-value)
             (prim 'data:merge cd/merge-values)
             (prim 'list:vector runtime/list-values)
             (prim 'seq:count count)
             ['source:initialize (cd/initialize-operator)]
             ['data:snapshot (cd/snapshot-operator)]
             ['session:export (connected/export-operator)]
             (prim 'seq:empty (fn [] []))
             (prim 'seq:append (fn [xs x] (conj xs x)))
             (prim 'seq:concat (fn [& xs] (vec (apply concat xs))))
             (prim 'seq:last peek)
             (prim 'seq:project (fn [key xs] (mapv #(get % key) xs)))
             (prim 'data:equal? =)
             ['event:point (occurrence-operator)]
             (prim 'text:format (fn [template & values] (apply format template values)))
             ['cell:reference (cd/reference-operator)]
             ['cell:value (cd/value-operator)]
             ['cell:input (cd/input-operator)]
             ['inspect:cell (inspect-operator)]
             ['cell:first (first-operator)]
             ['environment:overview (overview-operator)]
             ['behavior:latest-value (latest-operator)]
             ['source:admit
              (ops/async-op admitter
                            (fn [make] (make (fn [reply target support]
                                               (ops/effect-output {:reply (cd/unpack reply)
                                                                   :target (cd/unpack target)
                                                                   :support (cd/unpack support)})))))]])})))

(defn open! [root options]
  (let [owner (host/open! (model/compiler)) journal (atom []) deliveries (atom [])]
    (try
      (host/extend! owner (extension owner journal))
      (host/extend! owner (runtime/message-extension {:owner owner}))
      (host/extend! owner (model/declaration root "soul-refined.lain" options))
      (host/observe! owner ::deliveries
                     (fn [current]
                       (let [network (session/network current)
                             events (connected/freeze network (session/binding-value current 'delivered-events))]
                         (when-not (= connected/pending events)
                           (reset! deliveries (mapv #(connected/freeze network (:payload %))
                                                    (runtime/list-values events)))))))
      {:owner owner :journal journal :deliveries deliveries}
      (catch Throwable error (host/close! owner) (throw error)))))

(defn advance-context! [owner]
  (host/publish-input!
   owner (fn [current]
           (mapv (fn [[target source]]
                   (session/publication-input
                    (session/binding-cell current target)
                    (connected/payload (session/binding-value current source))))
                 '[[context-cell conversation-context-cell]
                   [context-selection-cell construction-selection]])))
  (host/step! owner)
  (host/publish-input! owner
                       (fn [current]
                         [(session/publication-input
                           (session/binding-cell current 'construction-trigger) true)]))
  (host/step! owner))

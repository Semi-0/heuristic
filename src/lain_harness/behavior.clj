(ns lain-harness.behavior
  "Opt-in Compiler-2 temporal operators. All state is immutable reducer content."
  (:require [propagators.cells.value :as value]
            [propagators.compiler-2.compiler.basis :as basis]
            [propagators.compiler-2.model.operator-value :as operator]
            [propagators.compiler-2.runtime.application :as application]
            [propagators.compiler-2.runtime.declaration-effects :as declarations]
            [propagators.compiler-2.session :as session]
            [propagators.compiler-2.session.files :as files]
            [propagators.compiler-2.session.connected :as connected]
            [propagators.datastructures.reducer-cell :as reducer]
            [propagators.datastructures.layered-value :as datum]
            [propagators.datastructures.support-collection :as collection]
            [propagators.datastructures.support :as support]
            [propagators.stdlib.premise-state :as premise-state]
            [propagators.experimental.temporal-behavior :as temporal]
            [propagators.experimental.temporal-runner :as temporal-runner]
            [propagators.message :refer [message]]
            [propagators.network :as net]
            [propagators.network-builder :as nb]
            [propagators.propagator :as prop]
            [propagators.runner :as runner]))

(defn callable-template
  "Prepare a temporary invocation through Compiler-2 for primitives and closures."
  [network callable context-id seed arity]
  (let [operator-id (basis/stable-node-id seed :callable)
        out (basis/stable-node-id seed :result)
        args (mapv #(basis/stable-node-id seed :argument %) (range arity))
        initial (reduce nb/ensure-cell (declarations/discard network) (conj args out operator-id))
        [constant-id initial] ((prop/construct-propagator
                                :behavior/callable
                                (fn [_ _ _] [(message operator-id callable)])
                                [] [operator-id]) initial)
        initial (runner/completed-network (runner/run-network [constant-id] initial))]
    {:network initial :operator operator-id :context context-id :seed seed :arguments args :output out}))

(defn invoke
  "Evaluate immutable inputs and discard the temporary invocation."
  [{:keys [network operator context seed arguments output]} & values]
  (let [[network producers]
        (reduce (fn [[current producers] [id v]]
                  (let [[producer updated]
                        ((prop/construct-propagator
                          :behavior/argument
                          (fn [_ _ _] [(message id (collection/content {:base v :support #{}}))])
                          [] [id]) current)]
                    [updated (conj producers producer)]))
                [network []] (map vector arguments values))
        ready (runner/completed-network (runner/run-network producers network))
        [state _] (application/install-application
                   {:net ready :context-id context :application/app-id seed}
                   operator arguments output)
        after (runner/completed-network (runner/run-network (:props state) (:net state)))]
    (connected/payload (net/network-cell-strongest after output))))

(defn- callable-argument [operation]
  (case operation :lift 2 (:scan :map :event-filter :history-map) 1 nil))

(defn- arities [operation]
  (case operation :scan #{2 3} :lift #{3} (:map :event-filter :history-map :sample) #{2}
        :history #{1 2} (:hold :last) #{1}))

(defn- configuration [operation network arguments context-id output]
  (let [read-value #(connected/payload (net/network-cell-strongest network (nth arguments %)))
        position (callable-argument operation)
        callable (when position (read-value position))
        parameter (case operation :scan (when (= 3 (count arguments)) (read-value 2))
                        (:sample :history) (when (= 2 (count arguments)) (read-value 1)) nil)]
    (when (and (or (nil? position) (application/compiler-callable? callable))
               (not= connected/pending parameter) (not (value/unusable? parameter)))
      (cond-> {:ready? true}
        position (assoc :callable (callable-template network callable context-id
                                                     [:behavior output :invocation]
                                                     (if (#{:scan :lift} operation) 2 1)))
        (and (= :scan operation) (= 3 (count arguments))) (assoc :initial parameter)
        (= :sample operation) (assoc :time parameter)
        (= :history operation) (assoc :order (or parameter :fifo))))))

(defn behavior-operator [operation]
  (operator/operator-closure
   {:name (symbol (str "behavior:" (name operation)))
    :application-installer
    (fn [network arguments output context-id]
      (when-not ((arities operation) (count arguments))
        (throw (ex-info "Invalid behavior arity" {:operation operation :arity (count arguments)})))
      (let [state-id (basis/stable-node-id :behavior-state output)
            networks (temporal/networks
                      (fn [content update]
                        (let [config (merge (:config content) (:config update))]
                          (temporal/merge-behavior operation (partial invoke (:callable config)) content update))) false)
            input-ids (if (= :lift operation) (subvec (vec arguments) 0 2) [(first arguments)])
            configure (prop/construct-propagator
                       [:behavior operation :configure]
                       (fn [_ _ current]
                         (let [prior (net/network-cell-content current state-id)]
                           (if (:ready? (:config (reducer/reducer-slots prior))) []
                               (if-let [config (configuration operation current arguments context-id output)]
                                 [(message state-id (temporal/update-value state-id networks {:config config}))]
                                 []))))
                       (vec (remove (set input-ids) arguments)) [state-id])
            slots (map-indexed
                   (fn [i input-id]
                     (reducer/p:reducer-slot
                      state-id (:merge-net networks) (:strongest-net networks)
                      :inputs input-id state-id
                      (fn [current id] {i (net/network-cell-content current id)}))) input-ids)
            forward (prop/construct-propagator
                     :behavior/result
                     (fn [_ _ current]
                       (let [content (net/network-cell-content current state-id)]
                         (if (value/nothing? content) [] [(message output content)])))
                     [state-id] [output])
            initial (reduce declarations/ensure-cell network [state-id output])
            [p n1] (configure initial)
            [slot-props n2] (reduce (fn [[props n] install]
                                      (let [[id next-net] (install n)] [(conj props id) next-net]))
                                    [[] n1] slots)
            [q n3] (forward n2)]
        [n3 (into [p q] slot-props) output]))}))

(defn source-operator []
  (operator/operator-closure
   {:name 'behavior:source
    :application-installer
    (fn [network arguments output _context-id]
      (when-not (<= (count arguments) 1)
        (throw (ex-info "behavior:source accepts an optional retained window" {})))
      (let [networks (temporal/source-networks)
            initialize (prop/construct-propagator
                        :behavior/source
                        (fn [_ _ current]
                          (let [window (when (seq arguments)
                                         (connected/payload (net/network-cell-strongest current (first arguments))))]
                            (if (or (= connected/pending window) (value/unusable? window)) []
                                [(message output (temporal/update-value output networks
                                                                        (cond-> {} window (assoc :window window))))])))
                        arguments [output])
            [p installed] (initialize (declarations/ensure-cell network output))]
        [installed [p] output]))}))

(defn- event-list [xs]
  (loop [xs xs events []]
    (cond
      (= connected/empty-list xs) events
      (and (map? xs) (contains? xs :car) (contains? xs :cdr))
      (let [event (:car xs)]
        (recur (:cdr xs)
               (conj events (temporal/occurrence (:id event) (:time event)
                                                 (get event :revision 0) (:value event)))))
      :else (throw (ex-info "Expected an immutable list of explicit temporal events" {})))))

(defn- merge-snapshot [content update]
  (let [snapshot (collection/merge-content (:snapshot content value/nothing)
                                           (:snapshot update value/nothing))]
    (if (= snapshot (:snapshot content)) content
        (let [projected (collection/strongest-value snapshot temporal/refine)
              payload (connected/payload projected)
              proof (datum/support-of projected)
              ready? (not (value/unusable? payload))
              evidence {:support/observations
                        (if ready? (into #{} (map #(hash-map :base % :support proof))
                                         (event-list payload)) #{})
                        :support/states (premise-state/states-of projected)}]
          {:snapshot snapshot :evidence evidence
           :result {:records (temporal/source-records evidence)
                    :domain-ready? ready? :domain-support proof
                    :domain-states (:support/states evidence)}}))))

(defn snapshot-operator
  "Adapt an authoritative supported snapshot of explicit events. No clock, IDs,
  or model policy are invented; an empty snapshot still has domain support."
  []
  (operator/operator-closure
   {:name 'behavior:from-events
    :application-installer
    (fn [network arguments output _]
      (when-not (= 1 (count arguments))
        (throw (ex-info "behavior:from-events expects one event-list snapshot" {})))
      (let [networks (temporal/networks merge-snapshot false)
            install (reducer/p:reducer-slot
                     output (:merge-net networks) (:strongest-net networks)
                     :snapshot (first arguments) output
                     (fn [current id]
                       (let [v (net/network-cell-strongest current id)]
                         (if (value/nothing? v) value/nothing
                             (collection/content
                              {:base (connected/payload v) :support (datum/support-of v)
                               :premise-state (premise-state/states-of v)})))))
            [p installed] (install (declarations/ensure-cell network output))]
        [installed [p] output]))}))

(defn current-operator
  "Project a snapshot-backed behavior's last value, or its explicit initial value.
  The ordinary TTMS output retains the domain and selected record's premises."
  []
  (operator/propagator-operator
   {:name 'behavior:current
    :activate
    (fn [network [input initial] [output] _]
      (let [content (net/network-cell-content network input)
            result (:result (reducer/reducer-slots content))
            fallback (net/network-cell-strongest network initial)
            record (peek (:records result))]
        (if (and (:domain-ready? result) (not (value/unusable? fallback)))
          [(message output
                    {:support/observations
                     (into #{} (map #(update % :support support/combine (:domain-support result)))
                           (if record (:candidates record)
                               #{{:base (connected/payload fallback)
                                  :support (datum/support-of fallback)}}))
                     :support/states (support/join (:domain-states result)
                                                   (premise-state/states-of fallback))})]
          (if (seq (:domain-states result))
            [(message output (collection/content {:premise-state (:domain-states result)}))]
            []))))}))

(defn primitive-bindings
  "Source-imported operators and the local evaluator needed by their evidence."
  []
  {:bindings (into [['behavior:source (source-operator)]
                    ['behavior:from-events (snapshot-operator)]
                    ['behavior:current (current-operator)]]
                   (map (fn [operation] [(symbol (str "behavior:" (name operation)))
                                         (behavior-operator operation)]))
                   [:scan :map :lift :hold :sample :event-filter :history :last :history-map])
   :evaluation temporal-runner/evaluation-options})

(defn extension [root fixture]
  (files/prepare-extension! {:id [:behavior fixture] :root root :read-files [fixture]}))

(defn observed [s binding]
  (let [v (session/binding-value s binding)]
    (if (reducer/reduced-value? v) (reducer/reduced-result v) v)))

(defn create-session []
  (session/create-session {:id (random-uuid) :run-id (random-uuid)}))

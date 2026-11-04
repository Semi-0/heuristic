(ns lain-harness.behavior
  "Opt-in Compiler-2 temporal operators. All state is immutable reducer content."
  (:require [propagators.cells.cell :as cell]
            [propagators.cells.value :as value]
            [propagators.compiler-2.compiler.basis :as basis]
            [propagators.compiler-2.model.operator-value :as operator]
            [propagators.compiler-2.runtime.application :as application]
            [propagators.compiler-2.runtime.declaration-effects :as declarations]
            [propagators.compiler-2.session :as session]
            [propagators.compiler-2.session.connected :as connected]
            [propagators.datastructures.reducer-cell :as reducer]
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
        initial (-> (reduce nb/ensure-cell (declarations/discard network) (conj args out operator-id))
                    (nb/seed-cell operator-id callable))
        [state _] (application/install-application
                   {:net initial :context-id context-id :application/app-id seed}
                   operator-id args out)]
    {:network (:net state) :tasks (:props state) :arguments args :output out}))

(defn invoke
  "Evaluate immutable inputs and discard the temporary invocation."
  [{:keys [network tasks arguments output]} & values]
  (let [seeded (reduce (fn [n [id v]] (net/assoc-net-cell n id (cell/cell v v)))
                       network (map vector arguments values))
        after (runner/completed-network (runner/run-network tasks seeded))]
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

(defn extension [root fixture]
  (session/session-extension
   {:id [:behavior fixture]
    :primitives (into {'behavior:source (source-operator)}
                      (map (fn [operation] [(symbol (str "behavior:" (name operation)))
                                            (behavior-operator operation)]))
                      [:scan :map :lift :hold :sample :event-filter :history :last :history-map])
    :files {"lib/behavior.lain" (slurp (str root "/lib/behavior.lain"))
            fixture (slurp (str root "/" fixture))}}))

(defn observed [s binding]
  (let [v (session/binding-value s binding)]
    (if (reducer/reduced-value? v) (reducer/reduced-result v) v)))

(defn create-session []
  (session/create-session {:id (random-uuid) :run-id (random-uuid)
                           :runtime-options {:evaluation temporal-runner/evaluation-options}}))

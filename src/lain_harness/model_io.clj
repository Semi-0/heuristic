(ns lain-harness.model-io
  "Effect-triggered, session-owned model workers. Only immutable handles enter Lain."
  (:require [clojure.core.async :as async]
            [clojure.string :as str]
            [lain-harness.blazux :as blazux]
            [meander.epsilon :as m]
            [propagators.compiler-2.compiler.basis :as basis]
            [propagators.compiler-2.model.operator-value :as operator]
            [propagators.compiler-2.session :as session]
            [propagators.compiler-2.session.host :as host]
            [propagators.compiler-2.session.operators :as ops]
            [propagators.experimental.visualization.data :as data]
            [propagators.gur :as gur]
            [propagators.message :refer [message]]
            [propagators.stdlib.source-publication :as publication]))

(defrecord WorkerHandle [session-id run-id call-id])

(defn- await-closed! [done]
  (when (= ::timeout (deref done 2000 ::timeout))
    (throw (ex-info "Model worker did not stop" {})))
  {:status :closed})

(defn- put-unless-stopped! [stop channel item]
  (let [[accepted selected] (async/alts!! [stop [channel item]] :priority true)]
    (and (= selected channel) accepted)))

(defn request-value [value]
  (let [v (data/payload value)
        request (if (string? v) {:text v} v)
        messages (:messages request)]
    (when-not (and (map? request)
                   (if (some? messages)
                     (and (vector? messages) (seq messages)
                          (every? #(and (#{"system" "user" "assistant"} (:role %))
                                        (string? (:content %))) messages))
                     (and (string? (:text request)) (not (str/blank? (:text request))))))
      (throw (ex-info "Model input requires text or explicit role-tagged messages" {})))
    (select-keys request [:id :text :messages])))

(defn- run-worker! [transport {:keys [stop stopping requests results done]}]
  (async/thread
    (try
      (loop [revision 0]
        (let [[job selected] (async/alts!! [stop requests] :priority true)]
          (when (and (= selected requests) job)
            (let [{:keys [request context acknowledge!]} job
                  cancelled? #(or (realized? stopping) ((:cancelled? context)))]
              (if (cancelled?)
                (recur revision)
                (let [reply (try
                              (transport (assoc request :cancelled? cancelled?))
                              (catch InterruptedException error (throw error))
                              (catch Exception error
                                (cond-> {:kind :error :text "" :thinking ""
                                         :error "Model request failed"}
                                  (:status (ex-data error))
                                  (assoc :http-status (:status (ex-data error))))))
                      result (cond-> (assoc reply :response-sequence (inc revision)) (:id request) (assoc :request-id (:id request)))
                      accepted? (and (not (cancelled?))
                                     (put-unless-stopped! stop results [(inc revision) result]))]
                  (when accepted? (acknowledge! :completed))
                  ((:complete! context))
                  (when-not (realized? stopping)
                    (recur (if accepted? (inc revision) revision)))))))))
      (catch InterruptedException _ nil)
      (finally
        (async/close! results)
        (deliver done true)))))

(defn- open-worker! [transport capacity]
  (let [resource {:stop (async/chan) :stopping (promise) :requests (async/chan capacity)
                  :results (async/chan capacity) :done (promise)
                  :subscribed? (atom false)}]
    (run-worker! transport resource)
    resource))

(defn- stop-worker! [{:keys [stop stopping requests results done]}]
  (deliver stopping true)
  (async/close! stop)
  (async/close! requests)
  (async/close! results)
  (await-closed! done))

(defn- subscribe! [handle {:keys [results subscribed?]} context emit!]
  (when-not (compare-and-set! subscribed? false true)
    (throw (ex-info "A model worker supports one output subscription" {})))
  (let [stop (async/chan) done (promise)]
    (async/thread
      (try
        (loop []
          (let [[item selected] (async/alts!! [stop results] :priority true)]
            (when (and (= selected results) item (not ((:cancelled? context))))
              (let [[revision reply] item
                    update (publication/active [::output handle] revision reply)
                    accepted? (loop []
                                (case (emit! update)
                                  :accepted true
                                  :closed false
                                  :backpressured
                                  (let [[_ selected] (async/alts!! [stop (async/timeout 10)] :priority true)]
                                    (when-not (= selected stop) (recur)))))]
                (when accepted? (recur))))))
        (finally (deliver done true))))
    (fn []
      (async/close! stop)
      (await-closed! done)
      (reset! subscribed? false)
      {:status :closed})))

(defn interpreter
  "One private resource catalog per installed bridge, scoped by session and run.
  The catalog owns IO only; it is not consulted by propagation or strongest reads."
  [transport {:keys [capacity] :or {capacity 8}}]
  (when-not (and (fn? transport) (pos-int? capacity))
    (throw (ex-info "A transport and positive queue capacity are required" {})))
  (let [workers (atom {})]
    (reify ops/EffectInterpreter
      (interpret-effect! [_ owner context effect emit!]
        (let [{:keys [id run-id]} (session/session-identity (host/current-session owner))
              resource! (fn [handle]
                          (let [resource (get @workers handle)]
                            (when-not (and (instance? WorkerHandle handle)
                                           (= id (:session-id handle))
                                           (= run-id (:run-id handle)) resource)
                              (throw (ex-info "Worker belongs to a closed or different session run" {})))
                            resource))]
          (m/match effect
            {:op :open}
            (let [handle (->WorkerHandle id run-id (:id context))
                  resource (open-worker! transport capacity)]
              (swap! workers assoc handle resource)
              (when-not (= :accepted (emit! handle))
                (stop-worker! resource)
                (swap! workers dissoc handle)
                (throw (ex-info "Worker handle publication was not accepted" {})))
              (fn []
                (let [closed (stop-worker! resource)]
                  (swap! workers dissoc handle)
                  closed)))
            {:op :enqueue :worker ?worker :request ?request}
            (let [resource (resource! (data/payload ?worker))]
              (when-not (async/offer! (:requests resource)
                                      {:request ?request :context context :acknowledge! emit!})
                (emit! :backpressured)
                ((:complete! context)))
              nil)

            {:op :subscribe :worker ?worker}
            (let [handle (data/payload ?worker)]
              (subscribe! handle (resource! handle) context emit!))

            _ (throw (ex-info "Unknown model IO effect" {:op (:op effect)}))))))))

(defn- output-binding [receive]
  (reify ops/Operator
    (as-propagator-operator [_ name hooks]
      (let [callable (ops/compiler-binding 'io-model:receive receive hooks)]
        (operator/propagator-operator
         {:name name
          :input-selector (fn [arguments _output _context]
                            (when-not (= 2 (count arguments))
                              (throw (ex-info "io-model:output expects worker and target" {})))
                            [(first arguments)])
          :output-selector (fn [arguments _output] (second arguments))
          :activate (fn [_ [worker] [target] context]
                      (let [id (basis/stable-node-id ::receive target)]
                        {:messages [(message id callable)]
                         :effects [(gur/declare-cell id)
                                   (gur/apply-closure-effect id [context worker] target)]}))})))))

(defn primitive-bindings
  ([] (primitive-bindings {}))
  ([{:keys [transport] :as options}]
   (let [transport (or transport (blazux/completion-transport options))
         interpreter (interpreter transport options)
         receive (ops/async-op interpreter
                               (fn [make]
                                 (make (fn [worker]
                                         (ops/effect-output {:op :subscribe :worker worker})))))]
     [['io-model:worker (ops/async-op interpreter
                                      (fn [make] (make (fn [] (ops/effect-output {:op :open})))))]
      ['io-model:input (ops/async-op interpreter
                                     (fn [make]
                                       (make (fn [worker input]
                                               (ops/effect-output {:op :enqueue :worker worker
                                                                   :request (request-value input)})))))]
      ['io-model:receive receive]
      ['io-model:output (output-binding receive)]
      ['generated:thoughts? (ops/prim-op (fn [make] (make #(not (str/blank? (:thinking (data/payload %)))))))]
      ;; Code is never inferred from prose or executed by this bridge.
      ['generated:action? (ops/prim-op (fn [make] (make #(= :code (:kind (data/payload %))))))]])))

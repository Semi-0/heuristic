(ns lain-harness.runtime
  "Open-session dependencies for environment streams, compilation and model IO."
  (:require [clojure.string :as str]
            [lain-harness.resource-queue :as q]
            [lain-harness.source-reader :as source-reader]
            [lain-harness.admission :as admission]
            [lain-harness.lexical-observation :as lexical-observation] [lain-harness.resource-stream :as stream]
            [lain-harness.resource-environment :as environment] [lain-harness.premises :as premises]
            [propagators.compiler-2.session :as session]
            [propagators.compiler-2.session.host :as host]
            [propagators.compiler-2.session.files :as files]
            [propagators.compiler-2.session.dependencies :as dependency]
            [propagators.compiler-2.session.operators :as ops]
            [propagators.compiler-2.compiler.basis :as basis]
            [propagators.compiler-2.model.env :as env]
            [propagators.compiler-2.model.operator-value :as operator]
            [propagators.gur :as gur]
            [propagators.datastructures.compound-object :as obj]
            [propagators.experimental.visualization.data :as data]
            [propagators.network :as net]
            [propagators.cells.value :as value])
  (:import [java.util UUID]))

(defrecord Stream [id])
(defrecord Model [id])
(defn services [owner]
  {:owner owner :streams (atom {}) :models (atom {}) :errors (atom [])})
(defn- signal! [state] (locking state (.notifyAll ^Object state)))
(defn- worker [name state body]
  (let [thread (doto (Thread. ^Runnable
                      (fn [] (try (body)
                                  (catch InterruptedException _)
                                  (catch Throwable error
                                    (swap! state assoc :closed? true :failure (ex-message error))))) name) (.setDaemon true) .start)]
    (fn []
      (swap! state assoc :closed? true)
      (signal! state)
      (.interrupt thread)
      (.join thread 3000)
      (if (.isAlive thread) {:status :failed} {:status :closed}))))
(defn- await-work [state ready?]
  (locking state
    (loop []
      (cond (:closed? @state) false
            (ready?) true
            :else (do (.wait ^Object state 1000) (recur))))))
(defn- diagnostic! [services model error]
  (let [id (UUID/randomUUID)
        event {:id [:diagnostic id] :kind :diagnostic :message (ex-message error) :support #{}}
        source (basis/stable-node-id :diagnostic id)
        target (:target @(:state model))]
    (swap! (:state model) update :diagnostics (fnil conj []) event)
    (if target
      (premises/admit! (:owner services)
                       {:id [:diagnostic id] :target target :support-cell source
                        :cells {source (premises/content source (:id event) 1 :active)}
                        :files {(str "diagnostic/" id ".lain")
                                (pr-str (list 'define (symbol (str "diagnostic-" id)) (:message event)))}})
      (q/append! (:inbound model) event))
    (signal! (:state model))))

(defn- description-dependency [services]
  (dependency/callback-dependency
   (fn [context emit!]
     (let [e (first (:arguments context))
           id [:description (:id e)]
           queue (q/queue 256)
           output (->Stream id)
           listeners (atom {})
           entry {:queue queue :listeners listeners :environment e}
           _ (swap! (:streams services) assoc id entry)
           stop (environment/observe! (:owner services) e
                                      (fn [event]
                                        (q/append! queue event)
                                        (doseq [[_ receive] @listeners] (receive event))
                                        (q/acknowledge! queue [(:id event)])))]
       (when-not (= :accepted (emit! output))
         (stop) (throw (ex-info "Description output rejected" {})))
       (fn [] (stop) (q/close! queue) (swap! (:streams services) dissoc id) {:status :closed})))))

(defn- compile-event! [services model target event]
  (let [owner (:owner services)
        id (:id event)
        support-id (basis/stable-node-id :emission-support id)
        file (str "model/" (UUID/nameUUIDFromBytes (.getBytes (pr-str id) "UTF-8")) ".lain")
        declaration {:id [:emission id] :target target :support-cell support-id
                     :derivations {support-id {:base {:emission id :source (:source event)} :sources (:sources event) :expected (:basis event)}}
                     :files {file (:source event)}}]
    (when-not (premises/available? owner (:sources event))
      (throw (ex-info "Emission support withdrawn" {:emission id})))
    (let [receipt (host/admit-source! owner declaration [(:id model) (:round event)])]
      (when-not (= :admitted (:status receipt))
        (throw (ex-info (or (:message receipt) "Admission rejected") receipt))))
    (swap! (:state model) update :admissions (fnil conj [])
           {:id id :file file :target target :support-cell support-id :source (:source event)})
    {:status :admitted :file file :support-cell support-id}))

(defn- compiler-dependency [services]
  (dependency/callback-dependency
   (fn [context emit!]
     (let [[actions e] (:arguments context)
           model (get @(:models services) (:id actions))
           _ (when-not model (throw (ex-info "Unknown model action stream" {})))
           target (environment/ensure! (:owner services) e)
           state (:state model)]
       (when (and (:target @state) (not= target (:target @state)))
         (throw (ex-info "Model action stream already has a lexical target" {})))
       (swap! state assoc :target target)
       (signal! state)
       (emit! {:compiler-target target})
       (fn [] {:status :closed})))))

(defn- current-context [services model]
  (let [owner (:owner services)
        snapshot (host/current-session owner)
        target (:target @(:state model))
        bindings (when target (vals (environment/snapshot snapshot target)))]
    (vec (filter :available? bindings))))
(defn- render-event [event]
  (if (= :diagnostic (:kind event)) (:message event)
      (str (:binding event) " = "
           (let [v (:view event)]
             (if (= :callable v)
               (if-let [form (get-in event [:origin :form])] (pr-str form)
                       "<connected compound or callable>")
               (pr-str v))))))
(defn- run-inference! [services model events]
  (let [state (:state model) owner (:owner services)
        round (inc (:round @state))
        round-id (basis/stable-node-id :model-round (:id model) round)
        context (current-context services model)
        sources (vec (conj (premises/support-sources context) (:premise model) round-id))
        network (session/network (host/current-session owner))
        basis (into {} (map (fn [id] [id (data/payload (net/network-cell-strongest network id))]))
                    (premises/support-sources context))
        cancelled? #(or (:closed? @state) (not (premises/available? owner sources)))
        parser (stream/framer)
        emit-form!
        (fn [{:keys [index source]}]
          (when (cancelled?) (throw (ex-info "Inference support withdrawn" {})))
          (let [id [(:id model) round index]
                event {:id id :source source :round round :sources sources :basis basis}]
            (q/append! (:outbound model) event)
            (try
              (compile-event! services model (:target @state) event)
              (catch Exception error
                (throw (ex-info (ex-message error) (assoc (ex-data error) :phase :admission) error)))
              (finally (q/acknowledge! (:outbound model) [id])))))]
    (premises/source! owner round-id {:model (:id model) :round round})
    (swap! state assoc :round round :round-premise round-id :busy? true)
    (try
      (try
        ((:transport model)
         {:messages [{:role "system" :content "Emit only complete Lain source forms, without markdown. Extend the existing environment monotonically. Reuse existing definitions; use a new name for corrections. Emit no code when no work is needed."}
                     {:role "user" :content (str "Changed bindings: " (pr-str (vec (keep :binding events)))
                                                 "\nCurrent environment:\n" (str/join "\n" (map render-event context)))}]
          :round round :context context :events events :cancelled? cancelled?}
         #(try (stream/feed! parser % false emit-form!)
               (catch Exception error (throw (ex-info (ex-message error)
                                                      (merge {:phase :framing} (ex-data error)) error)))))
        (catch Exception error
          (when-not (#{:admission :framing} (:phase (ex-data error)))
            (swap! state assoc :paused? true :pause-reason :transport-failure))
          (throw error)))
      (stream/feed! parser "" true emit-form!)
      (finally (swap! state assoc :busy? false)))))

(defn- model-dependency [services model]
  (dependency/callback-dependency
   (fn [_context _emit!]
     (let [owner (:owner services) state (:state model)
           stop-observe
           (host/observe! owner [:model-input (:id model)]
                          (fn [s]
                            (let [stream-handle (data/payload (net/network-cell-strongest (session/network s) (:inlet model)))
                                  description (get @(:streams services) (:id stream-handle))]
                              (when (and description (not= (:id stream-handle) (:input-stream @state)))
                                (when (:input-stream @state) (throw (ex-info "Model input stream is fixed" {})))
                                (let [receive (fn [event]
                                                (try
                                                  (q/append! (:inbound model) event)
                                                  (signal! state)
                                                  (catch Exception error
                                                    (swap! state assoc :paused? true :pause-reason :input-queue-failure
                                                           :failure (ex-message error))
                                                    (throw error))))]
                                  (swap! state assoc :input-stream (:id stream-handle))
                                  (swap! (:listeners description) assoc (:id model) receive)
                                  (doseq [event (q/history (:queue description))] (receive event)))))))
           stop-worker
           (worker "lain-model-session" state
                   (fn []
                     (try
                       (while (await-work state #(and (:target @state) (seq (q/pending (:inbound model)))
                                                      (not (:paused? @state))
                                                      (premises/available? owner [(:premise model)])
                                                      (< (:round @state) (:max-inferences model))))
                         (let [events (q/pending (:inbound model))]
                           (q/acknowledge! (:inbound model) (mapv :id events))
                           (try (run-inference! services model events)
                                (catch InterruptedException e (throw e))
                                (catch Exception error
                                  (when-not (= :closed (host/lifecycle owner))
                                    (diagnostic! services model error))))))
                       (catch InterruptedException _))))]
       (fn []
         (stop-observe)
         (doseq [[_ entry] @(:streams services)] (swap! (:listeners entry) dissoc (:id model)))
         (q/close! (:inbound model)) (q/close! (:outbound model))
         (stop-worker))))))

(defn facility-extensions [services]
  (let [owner (:owner services) interpreter (host/effect-interpreter owner)
        effect (fn [id] (ops/async-op interpreter
                                      (fn [a] (a (fn [& arguments]
                                                   (ops/effect-output (ops/dependency-input id arguments)))))))]
    (let [bindings (merge
                    (environment/bindings (session/environment (host/current-session owner)))
                    {'describe-environment (effect ::describe)
                     'incremental-compile (effect ::compile)
                     'inputs-of (operator/operator-closure
                                 {:name 'inputs-of
                                  :compiler-activate
                                  (fn [_ network _context arguments output]
                                    (let [model (net/network-cell-strongest network (first arguments))
                                          inlet (data/field model :inlet)]
                                      (if (value/unusable? inlet) {:messages []}
                                          {:effects [(gur/declare-prop (basis/stable-node-id :model-input-link output inlet)
                                                                       [:model-input-link output inlet] [output] [inlet]
                                                                       (fn [_ _ current] (basis/mono-sync-messages current output inlet)))]})))})
                     'actions-of (basis/slot-accessor :actions)})
          dependencies {'describe-environment {::describe (description-dependency services)}
                        'incremental-compile {::compile (compiler-dependency services)}}]
      (into [(source-reader/extension) (admission/extension)
             (lexical-observation/extension) (lexical-observation/parent-extension)]
            (map (fn [[name primitive]]
                   (let [entry (keyword "lain-harness.runtime" (str name))]
                     (session/session-extension
                      {:id entry
                       :imports {["lain_harness/runtime.clj" entry 1] [entry 1]}
                       :primitives {name primitive}
                       :dependencies (get dependencies name {})}))))
            (sort-by (comp str key) bindings)))))

(defn install-facilities! [services]
  (doseq [extension (facility-extensions services)]
    (host/extend! (:owner services) extension)))

(defn model-extension [services {:keys [id transport max-inferences timeout-ms capacity]
                                 :or {max-inferences 8 timeout-ms 90000 capacity 256}}]
  (when-not (and id (fn? transport)) (throw (ex-info "Model identity and transport required" {})))
  (let [model {:id id :transport transport :max-inferences max-inferences :timeout-ms timeout-ms
               :premise (basis/stable-node-id :model-premise id)
               :cell (basis/stable-node-id :model-cell id)
               :inlet (basis/stable-node-id :model-inlet id)
               :inbound (q/queue capacity) :outbound (q/queue capacity)
               :state (atom {:round 0 :closed? false})}
        value (obj/compound-object {:actions (->Stream id) :inlet (:inlet model)})]
    (when (contains? @(:models services) id) (throw (ex-info "Duplicate model identity" {:id id})))
    (swap! (:models services) assoc id model)
    (session/session-extension
     {:id [:model id]
      :cells {(:premise model) (premises/content (:premise model) {:model id} 1 :active)
              (:cell model) value (:inlet model) value/nothing}
      :primitives {'model-session (env/cell-binding (:cell model))}
      :dependencies {::model (model-dependency services model)}})))

(defn start-model! [services]
  (host/subscribe! (:owner services) ::model-stream ::model))

(defn user-extension [id]
  (let [cell (basis/stable-node-id :user-input id)]
    (session/session-extension {:id [:user id] :cells {cell value/nothing}
                                :primitives {'user-input (env/cell-binding cell)}})))
(defn publish-user! [services id revision text]
  (let [cell (basis/stable-node-id :user-input id)]
    (when-not (= :accepted (host/submit! (:owner services)
                                         (session/cell-input cell (premises/content cell text revision :active))))
      (throw (ex-info "User ingress backpressured" {})))))

(defn specification-extension
  "Read the actual source. Facilities must already be supplied through open-session API."
  [services path]
  (let [library (files/prepare-extension!
                 {:id ::compositional-library
                  :read-files ["propagators/compiler_2/runtime/lain/combinator.lain"
                               "propagators/compiler_2/runtime/lain/list.lain"]})
        parts (session/extension-parts library)
        owner (:owner services)
        root (.getParent (java.io.File. path))
        files (merge (:files parts)
                     {"soul-refined.lain" (slurp path)}
                     (into {} (map (fn [file] [file (slurp (str root "/" file))]))
                           ["lib/queue.lain" "lib/rounds.lain" "lib/observations.lain" "lib/observation-example.lain"]))]
    (session/session-extension
     (assoc parts :id ::specification :files files
            :target (session/environment (host/current-session owner))))))

(defn resume-model! [services id]
  (let [model (get @(:models services) id)]
    (when-not model (throw (ex-info "Unknown model" {:id id})))
    (swap! (:state model) dissoc :paused? :pause-reason)
    (signal! (:state model))))

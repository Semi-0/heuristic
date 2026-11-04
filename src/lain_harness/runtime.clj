(ns lain-harness.runtime
  "Trusted open-session assembly and external resource lifecycles. Policy is Lain."
  (:require [lain-harness.environment :as environment]
            [lain-harness.source-reader :as source-reader]
            [lain-harness.admission :as admission]
            [lain-harness.lexical-observation :as lexical]
            [lain-harness.connected-data :as cd]
            [lain-harness.value-metadata :as value-metadata]
            [lain-harness.premises :as premises]
            [propagators.compiler-2.compiler.basis :as basis]
            [propagators.compiler-2.model.env :as env]
            [propagators.compiler-2.model.operator-value :as operator]
            [propagators.experimental.visualization.data :as view]
            [propagators.gur :as gur]
            [propagators.compiler-2.session :as session]
            [propagators.compiler-2.session.connected :as connected]
            [propagators.compiler-2.session.host :as host]
            [propagators.compiler-2.session.files :as files]
            [propagators.compiler-2.session.dependencies :as dependency]
            [propagators.compiler-2.session.operators :as ops]
            [propagators.network :as net]
            [propagators.cells.value :as value]))

(defn services [owner] {:owner owner})
(defn model-cell [id part] (basis/stable-node-id :model id part))
(defn read-cell [s id] (connected/freeze (session/network s) (net/network-cell-strongest (session/network s) id)))
(defn linked-list [xs]
  (reduce (fn [tail x] {:car x :cdr tail}) basis/list-empty-marker (reverse xs)))
(defn list-values [xs]
  (if (= xs basis/list-empty-marker) [] (into [(:car xs)] (list-values (:cdr xs)))))

(defn publish-event!
  "External ingress has stable identity and ordering. Lain owns consumption."
  [owner cell event]
  (host/publish-input! owner
                       (fn [s]
                         (let [events (list-values (read-cell s cell))
                               prior (first (filter #(= (:id %) (:id event)) events))]
                           (cond
                             (= prior event) []
                             prior (throw (ex-info "Conflicting event identity" {:id (:id event)}))
                             :else [(session/publication-input cell (connected/frozen (linked-list (conj events event))))])))))

(defn- transport-dependency [owner id transport]
  (dependency/callback-dependency
   (fn [context emit!]
     (let [request (cd/unpack (:input context))
           cancelled (atom false)
           cancelled? #(or @cancelled ((:cancelled? context)))
           publish (fn [index kind payload]
                     (when (cancelled?) (throw (ex-info "Transport cancelled" {})))
                     (publish-event! owner (model-cell id :events)
                                     {:id [(:id request) index] :kind kind :payload payload}))
           thread (Thread.
                   (fn []
                     (let [index (atom 0)]
                       (try
                         (transport (assoc request :messages (list-values (:messages request)) :cancelled? cancelled?)
                                    #(publish (swap! index inc) :chunk %))
                         (publish (swap! index inc) :complete "")
                         (emit! :complete)
                         (catch Exception error
                           (when-not (cancelled?)
                             (publish (swap! index inc) :failure (ex-message error))))
                         (finally ((:complete! context)))))) "lain-transport")]
       (.setDaemon thread true)
       (.start thread)
       (fn [] (reset! cancelled true) (.interrupt thread) {:status :closed})))))

(defn facility-extensions [services]
  (let [owner (:owner services)
        primitives (merge (environment/bindings (session/environment (host/current-session owner)))
                          {'inputs-of (operator/operator-closure
                                       {:name 'inputs-of
                                        :compiler-activate
                                        (fn [_ network _ [model] output]
                                          (let [v (view/payload (net/network-cell-strongest network model))
                                                inlet (:inlet (cd/unpack v))]
                                            (if (or (value/unusable? v) (nil? inlet) (value/unusable? inlet)) {:messages []}
                                                {:effects [(gur/declare-prop (basis/stable-node-id :model-inlet output inlet)
                                                                             :model-inlet [output] [inlet]
                                                                             (fn [_ _ current] (basis/mono-sync-messages current output inlet)))]})))})
                           'session:initialize (connected/connector :initialize)
                           'session:feedback (connected/connector :feedback)
                           'session:admit (connected/connector :admission)
                           'cell:reference (cd/reference-operator)
                           'cell:value (cd/value-operator)
                           'data:snapshot (cd/snapshot-operator)
                           'data:unpack (ops/prim-op (fn [p] (p #(connected/thaw (cd/unpack %)))))
                           'data:merge (ops/prim-op (fn [p] (p cd/merge-values)))
                           'data:record (ops/prim-op (fn [p] (p cd/record-value)))
                           'data:list (ops/prim-op (fn [p] (p cd/list-value)))
                           'data:integer? (ops/prim-op (fn [p] (p integer?)))
                           'data:empty? (ops/prim-op (fn [p] (p #(= basis/list-empty-marker (cd/unpack %)))))
                           'data:get (ops/prim-op (fn [p] (p cd/field-value)))
                           'transport:request (ops/async-op (host/effect-interpreter owner)
                                                            (fn [a] (a (fn [id request] (if (= :idle (cd/unpack request)) (ops/cell-output :idle) (ops/effect-output (ops/dependency-input id request)))))))})]
    (into (into [(source-reader/extension) (admission/extension) (lexical/extension) (lexical/parent-extension)] (value-metadata/extensions))
          (map (fn [[name primitive]]
                 (let [id (keyword "lain-harness.runtime" (str name))]
                   (session/session-extension {:id id :primitives {name primitive}
                                               :imports {["lain_harness/runtime.clj" id 1] [id 1]}}))))
          (sort-by (comp str key) primitives))))
(defn install-facilities! [services]
  (doseq [extension (facility-extensions services)] (host/extend! (:owner services) extension)))

(defn model-extension [services {:keys [id transport name capacity max-inferences]
                                 :or {name 'model-session capacity 256 max-inferences 8}}]
  (when-not (and id (fn? transport) (pos-int? capacity) (pos-int? max-inferences))
    (throw (ex-info "Model identity, transport and positive limits required" {})))
  (let [dependency-id (keyword "lain-harness.transport" (str id))
        prefix (str "model-" (subs (str (:uuid (basis/stable-node-id id))) 0 12))
        state-name (symbol (str prefix "-state"))
        inlet-name (symbol (str prefix "-inlet"))
        events-name (symbol (str prefix "-events"))
        delivered-name (symbol (str prefix "-delivered"))
        premise (model-cell id :premise)]
    (session/session-extension
     {:id [:model id]
      :interfaces {[:model id] {:kind :model :cell (model-cell id :state)}
                   [:messages id] {:kind :messages :cell (model-cell id :delivered)}
                   [:source id] {:kind :source :cell (model-cell id :events)}}
      :cells {(model-cell id :state) value/nothing
              (model-cell id :inlet) value/nothing
              (model-cell id :events) (premises/content (model-cell id :events) (connected/frozen basis/list-empty-marker) 1 :active)
              (model-cell id :delivered) (premises/content (model-cell id :delivered) (connected/frozen basis/list-empty-marker) 1 :active)
              premise (premises/content premise {:model id} 1 :active)
              (model-cell id :instance) (connected/frozen
                                         {:state-cell (model-cell id :state) :inlet (model-cell id :inlet)
                                          :events-cell (model-cell id :events) :delivered-cell (model-cell id :delivered)
                                          :transport dependency-id :premise premise})}
      :primitives {name (env/cell-binding (model-cell id :instance))
                   state-name (env/cell-binding (model-cell id :state))
                   inlet-name (env/cell-binding (model-cell id :inlet))
                   events-name (env/cell-binding (model-cell id :events))
                   delivered-name (env/cell-binding (model-cell id :delivered))
                   (symbol (str prefix "-premise")) (env/cell-binding premise)}
      :dependencies {dependency-id (transport-dependency (:owner services) id transport)}
      :files {(str prefix ".lain")
              (str "(define " prefix "-initialize (session:initialize (cell:reference " state-name ")"
                   " (data:with (model:initial " capacity " " max-inferences ") (data:record :model-id " (pr-str (str id)) "))))\n"
                   "(model:connect " name " " inlet-name " (cell:reference user-input))")}

      :scoped-primitives {}})))

(defn user-extension [id]
  (let [cell (basis/stable-node-id :user-input id)]
    (session/session-extension {:id [:user id]
                                :interfaces {[:user id] {:kind :user-message :cell cell}}
                                :cells {cell (premises/content cell (connected/frozen basis/list-empty-marker) 1 :active)}
                                :primitives {'user-input (env/cell-binding cell)}})))
(defn publish-user! [services id identity text]
  (host/publish-user-message! (:owner services)
                              {:interface [:user id]
                               :run-id (:run-id (session/session-identity (host/current-session (:owner services))))
                               :id identity :text text}))
(defn model-state [services id] (read-cell (host/current-session (:owner services)) (model-cell id :state)))
(defn resume-model! [services id]
  (host/publish-input! (:owner services)
                       (fn [s]
                         (let [cell (model-cell id :state) state (read-cell s cell)]
                           [(session/publication-input cell (connected/frozen (-> state (update :version inc) (assoc-in [:round :paused] false))))]))))

(defn message-extension [services model-id]
  (let [owner (:owner services) dep (keyword "lain-harness.delivery" (str model-id))]
    (session/session-extension
     {:id [:message-to-user model-id]
      :primitives {'message-to-user (ops/async-op (host/effect-interpreter owner)
                                                  (fn [a] (a (fn [content] (ops/effect-output (ops/dependency-input dep content))))))}
      :dependencies {dep (dependency/callback-dependency
                          (fn [context emit!]
                            (publish-event! owner (model-cell model-id :delivered)
                                            {:id (nth (:id context) 3) :payload (:input context)})
                            (emit! (connected/thaw {:status :delivered}))
                            ((:complete! context)) nil))}})))

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
                           ["lib/data.lain" "lib/queue.lain" "lib/rounds.lain" "lib/observations.lain" "lib/observation-example.lain" "lib/compiler.lain" "lib/model.lain"]))]
    (session/session-extension
     (assoc parts :id ::specification :files files
            :target (session/environment (host/current-session owner))))))

(defn prepare!
  "Trusted preparation for an ordinary open session. No message is submitted.
  An optional existing editor owner catalog makes the same owner discoverable."
  [{:keys [source-path transport model-id user-id owners propagation-options]
    :or {model-id "model" user-id "user"
         propagation-options {:max-steps 300000 :timeout-ms 180000}}}]
  (let [owner (host/open! (session/create-session
                           {:id (random-uuid) :run-id (random-uuid)
                            :propagation-options propagation-options}))
        services (assoc (services owner) :model-id model-id :user-id user-id)]
    (try
      (install-facilities! services)
      (host/extend! owner (specification-extension services source-path))
      (host/extend! owner (user-extension user-id))
      (host/extend! owner (model-extension services {:id model-id :transport transport}))
      (host/extend! owner (message-extension services model-id))
      (host/extend-source! owner
                           "(define conversation (conversation-experiment model-session user-input message-to-user))
         (define conversation-child (car conversation))")
      (when owners
        ((requiring-resolve 'graph.lain-editor.runtime.session/register-compiler-session!)
         owners owner (slurp source-path)))
      services
      (catch Throwable error (host/close! owner) (throw error)))))

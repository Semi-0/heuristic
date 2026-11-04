(ns lain-harness.runtime
  "Trusted open-session assembly and external resource lifecycles. Policy is Lain."
  (:require [lain-harness.environment :as environment]
            [lain-harness.source-reader :as source-reader]
            [lain-harness.admission :as admission]
            [lain-harness.lexical-observation :as lexical]
            [lain-harness.connected-data :as cd]
            [lain-harness.value-metadata :as value-metadata]
            [propagators.compiler-2.session :as session]
            [propagators.compiler-2.session.connected :as connected]
            [propagators.compiler-2.session.host :as host]
            [propagators.compiler-2.session.files :as files]
            [propagators.compiler-2.session.dependencies :as dependency]
            [propagators.compiler-2.session.operators :as ops]
            [propagators.network :as net]))

(defn services [owner] {:owner owner})
(defn model-cell [s id part]
  (session/interface-cell s [(case part :state :model :events :source :delivered :messages :commands :control) id]))
(defn read-cell [s id] (connected/freeze (session/network s) (net/network-cell-strongest (session/network s) id)))
(defn linked-list [xs]
  (reduce (fn [tail x] {:car x :cdr tail}) connected/empty-list (reverse xs)))
(defn list-values [xs]
  (loop [xs xs result []]
    (cond
      (= xs connected/empty-list) result
      (and (map? xs) (contains? xs :car) (contains? xs :cdr))
      (recur (:cdr xs) (conj result (:car xs)))
      :else (throw (ex-info "Event source is unresolved or malformed" {:value xs})))))

(defn- event-inputs [s cell event]
  (let [events (list-values (read-cell s cell))
        prior (first (filter #(= (:id %) (:id event)) events))]
    (cond
      (= prior event) []
      prior (throw (ex-info "Conflicting event identity" {:id (:id event)}))
      :else [(session/publication-input cell (linked-list (conj events event)))])))

(defn publish-event!
  "External ingress has stable identity and ordering. Lain owns consumption."
  [owner interface event]
  (host/publish-input! owner
                       (fn [s] (event-inputs s (session/interface-cell s interface) event))))

(defn- publish-cell-event! [owner cell event]
  (host/publish-input! owner (fn [s] (event-inputs s cell event))))

(defn- transport-dependency [owner transport]
  (dependency/callback-dependency
   (fn [context emit!]
     (let [{:keys [request events]} (cd/unpack (:input context))
           cancelled (atom false)
           finished (atom false)
           cancelled? #(or @cancelled ((:cancelled? context)))
           publish (fn [index kind payload]
                     (when (cancelled?) (throw (ex-info "Transport cancelled" {})))
                     (publish-cell-event! owner events
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
                         (finally
                           (reset! finished true)
                           (Thread/interrupted)
                           ((:complete! context)))))) "lain-transport")]
       (.setDaemon thread true)
       (.start thread)
       (fn []
         (reset! cancelled true)
         ;; Normal completion must not interrupt the thread awaiting its receipt.
         (when-not @finished (.interrupt thread))
         {:status :closed})))))

(defn facility-extensions [services]
  (let [owner (:owner services)
        primitives (merge (environment/bindings (session/environment (host/current-session owner)))
                          {'cell:input (cd/input-operator)
                           'session:export (connected/export-operator)
                           'source:initialize (cd/initialize-operator)
                           'session:feedback (connected/connector :feedback)
                           'session:admit (connected/connector :admission)
                           'cell:reference (cd/reference-operator)
                           'cell:value (cd/value-operator)
                           'data:snapshot (cd/snapshot-operator)
                           'data:unpack (connected/primitive 'data:unpack #(connected/thaw (cd/unpack %)))
                           'data:merge (connected/primitive 'data:merge cd/merge-values)
                           'data:record (connected/primitive 'data:record cd/record-value)
                           'data:list (connected/primitive 'data:list cd/list-value)
                           'data:integer? (connected/primitive 'data:integer? integer?)
                           'data:equal? (connected/primitive 'data:equal? =)
                           'and (cd/and-operator)
                           'or (cd/or-operator)
                           'data:empty? (connected/primitive 'data:empty? #(= connected/empty-list (cd/unpack %)))
                           'data:get (connected/primitive 'data:get cd/field-value)})]
    (into (into [(source-reader/extension) (admission/extension) (lexical/extension) (lexical/parent-extension)] (value-metadata/extensions))
          (map (fn [[name primitive]]
                 (let [id (keyword "lain-harness.runtime" (str name))]
                   (session/session-extension {:id id :primitives {name primitive}
                                               :imports {["lain_harness/runtime.clj" id 1] [id 1]}}))))
          (sort-by (comp str key) primitives))))
(defn install-facilities! [services]
  (doseq [extension (facility-extensions services)] (host/extend! (:owner services) extension)))

(defn transport-extension
  "Supply only an effectful transport primitive and its external resource."
  [services transport]
  (when-not (fn? transport) (throw (ex-info "Transport function required" {})))
  (let [owner (:owner services) dep ::transport]
    (session/session-extension
     {:id dep
      :primitives {'transport:stream
                   (ops/async-op (host/effect-interpreter owner)
                                 (fn [a] (a (fn [request events]
                                              (if (= :idle (cd/unpack request)) (ops/cell-output :idle)
                                                  (ops/effect-output
                                                   (ops/dependency-input dep {:request (cd/unpack request) :events events})))))))}
      :dependencies {dep (transport-dependency owner transport)}})))

(defn publish-user! [services id identity text]
  (host/publish-user-message! (:owner services)
                              {:interface [:user id]
                               :run-id (:run-id (session/session-identity (host/current-session (:owner services))))
                               :id identity :text text}))
(defn model-state [services id]
  (let [s (host/current-session (:owner services))] (read-cell s (model-cell s id :state))))
(defn resume-model!
  "Publish an explicit command identity. Lain owns the state transition and replay."
  ([services id] (resume-model! services id (random-uuid)))
  ([services id identity]
   (publish-event! (:owner services) [:control id]
                   {:id identity :kind :resume :payload :resume})))

(defn message-extension [services]
  (let [owner (:owner services) dep ::delivery]
    (session/session-extension
     {:id dep
      :primitives {'message:deliver (ops/async-op (host/effect-interpreter owner)
                                                  (fn [a] (a (fn [content delivered] (ops/effect-output (ops/dependency-input dep {:content content :delivered delivered}))))))}
      :dependencies {dep (dependency/callback-dependency
                          (fn [context emit!]
                            (let [{:keys [content delivered]} (:input context)]
                              (publish-cell-event! owner delivered
                                                   {:id (nth (:id context) 3) :payload content}))
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
                           ["integration.lain" "lib/data.lain" "lib/ports.lain" "lib/queue.lain" "lib/rounds.lain" "lib/observations.lain" "lib/observation-example.lain" "lib/compiler.lain" "lib/model.lain"]))]
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
      (host/extend! owner (transport-extension services transport))
      (host/extend! owner (message-extension services))
      (host/extend-source! owner
                           (str "(define conversation (integration:launch " (pr-str model-id) " " (pr-str user-id)
                                " (struct :request transport:stream :deliver message:deliver)))"))
      (host/step! owner)
      (when owners
        ((requiring-resolve 'graph.lain-editor.runtime.session/register-compiler-session!)
         owners owner (slurp source-path)))
      services
      (catch Throwable error (host/close! owner) (throw error)))))

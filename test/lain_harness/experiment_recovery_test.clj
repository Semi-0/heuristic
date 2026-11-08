(ns lain-harness.experiment-recovery-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [lain-harness.experiment-observation :as observation]
            [lain-harness.model-io-session :as model]
            [lain-harness.soul-session :as soul]
            [propagators.compiler-2.session :as session]
            [propagators.compiler-2.session.connected :as connected]
            [propagators.compiler-2.session.host :as host]
            [propagators.network :as net]))

(def root (System/getenv "LAIN_HARNESS_ROOT"))
(defn experiment-compiler []
  (let [base (model/compiler)]
    (session/create-session
     (assoc (session/session-identity base)
            :native-extensions (:bootstrap (session/session-declarations base))
            :runtime-options (:options (session/session-declarations base))
            :propagation-options {:max-steps 300000 :timeout-ms 180000}))))
(def plan {:outputs "result" :facts "controlled inputs" :unknowns "result" :units "numbers"
           :relations "addition" :operators "+" :operator "none" :status "ready" :steps "connect computed result"
           :tests "independent verification" :decision :compose :problem-class "addition"
           :parameters "numbers" :captured "none" :interface "connected scalar result"})

(defn trial! [sources invalid-analysis?]
  (let [owner (host/open! (experiment-compiler)) journal (atom []) requests (atom []) failure (atom nil)
        read-value #(connected/payload (session/binding-value (host/current-session owner) %))
        publish! (fn []
                   (host/publish-input! owner (fn [current] [(session/publication-input (session/binding-cell current 'ask-event)
                                                                                        {:round 1 :content "Compute the connected result."})]))
                   (host/step! owner))]
    (try
      (host/extend! owner (soul/extension owner journal))
      (host/extend! owner (observation/facilities))
      (host/extend! owner (model/declaration root "test/fixtures/experiment-recovery.lain"
                                             {:transport (fn [request]
                                                           (swap! requests conj request)
                                                           (Thread/sleep 20)
                                                           {:kind :reply :thinking "separate thinking" :finish-reason "stop" :truncated? false
                                                            :text (if (str/starts-with? (:id request) "round-")
                                                                    (get sources (:id request))
                                                                    (if invalid-analysis? "not EDN" (pr-str plan)))})}))
      (let [identity (session/session-identity (host/current-session owner))
            target (:id (read-value 'generated-environment))
            _ (publish!)
            deadline (+ (System/currentTimeMillis) (Long/parseLong (or (System/getenv "LAIN_RECOVERY_TEST_TIMEOUT_MS") "90000")))
            outcome (loop []
                      (when-let [error (first (host/failures owner))] (throw error))
                      (let [value (read-value 'round-outcome)]
                        (cond (#{:done :failed} (:status value)) value
                              (> (System/currentTimeMillis) deadline) (throw (ex-info "Lain experiment timed out"
                                                                                      {:requests (mapv :id @requests) :result value :computed-result (read-value 'result)
                                                                                       :admission (read-value 'admission-result)
                                                                                       :raw-reply (select-keys (read-value 'raw-model-result) [:request-id :kind :finish-reason :truncated?])
                                                                                       :journal @journal
                                                                                       :bindings (observation/binding-views
                                                                                                  (session/network (host/current-session owner))
                                                                                                  '#{snapshot context values matching packet-ready packet a a-ok g g-ok r0 r1 r2 t0 t1 t2 ok0 ok1 ok2 ready o receipt reply observation required worker environment})}))
                              :else (do (Thread/sleep 20) (recur)))))
            count-before (count @requests)]
        ;; The host only supplies/replays external input and observes the outcome.
        (publish!)
        {:outcome outcome :requests @requests :journal @journal :identity identity :target target
         :system-message (read-value 'system-message)
         :no-replayed-call (= count-before (count @requests)) :worker-unchanged (let [used (mapv #(get-in % [:observation :worker]) (:attempts outcome))]
                                                                                  (or (empty? used) (every? #(= (into {} (read-value 'worker)) (into {} %)) used)))
         :result (read-value 'result)
         :retained (try (connected/payload (net/network-cell-strongest (session/network (host/current-session owner))
                                                                       (session/scoped-binding-cell (host/current-session owner) target 'retained)))
                        (catch Exception _ :absent))})
      (catch Throwable error (reset! failure error) (throw error))
      (finally
        (try (host/close! owner)
             (catch Throwable cleanup
               (if-let [error @failure] (.addSuppressed error cleanup) (throw cleanup))))))))

(deftest lain-controls-phases-and-repairs-the-partial-network
  (let [source "(define retained 7)(define pending (+ missing 4))(-> pending result))"
        result (trial! {"round-1" source "round-1-repair-1" "(define extra 1)" "round-1-repair-2" "(-> retained missing)"} false)
        attempts (get-in result [:outcome :attempts])]
    (is (= ["analysis-1" "generalization-1" "round-1" "round-1-repair-1" "round-1-repair-2"] (mapv :id (:requests result))))
    (is (= :done (get-in result [:outcome :status])))
    (is (= [:rejected :admitted :admitted] (mapv #(get-in % [:admission :status]) attempts)))
    (is (= [false false true] (mapv :ready? attempts)))
    (is (= 3 (count (:accepted-forms (first attempts)))))
    (is (str/includes? (get-in result [:requests 3 :text]) "Invalid Lain form"))
    (is (str/includes? (get-in result [:requests 3 :text]) "(define retained 7)"))
    (is (str/includes? (get-in result [:requests 4 :text]) ":unresolved-outputs"))
    (is (= 7 (:retained result)))
    (is (= 11 (:result result)))
    (is (:worker-unchanged result))
    (is (:no-replayed-call result))
    (doseq [request (:requests result)]
      (is (= ["system" "user"] (mapv :role (:messages request))))
      (is (= (:text request) (get-in request [:messages 0 :content])))
      (is (= "Compute the connected result." (get-in request [:messages 1 :content]))))
    (let [receipt (get-in attempts [0 :admission])
          repair (get-in result [:requests 3 :messages 0 :content])]
      (is (pos-int? (:timestamp-ms receipt)))
      (is (= (:timestamp-ms receipt) (.toEpochMilli (java.time.Instant/parse (:timestamp-utc receipt)))))
      (is (str/starts-with? repair "# Live environment\n\n## system-message\n"))
      (is (str/includes? repair (:timestamp-utc receipt)))
      (is (str/includes? repair "round-1"))
      (is (str/includes? repair "Retained forms are already installed"))
      (is (not (str/includes? repair "separate thinking"))))
    (is (= "round-1-repair-2" (get-in result [:system-message :response])))
    (is (every? #(= (:identity result) (get-in % [:observation :identity])) attempts))
    (is (every? #(= (:target result) (get-in % [:observation :child])) attempts))))

(deftest logical-gates-decide-with-unfinished-inputs
  (let [owner (host/open! (experiment-compiler))
        read-value #(connected/payload (session/binding-value (host/current-session owner) %))]
    (try
      (host/extend! owner (observation/facilities))
      (host/extend-source! owner
                           "(define missing)
                            (define rejected (logic:all false missing))
                            (define accepted (logic:any true missing))
                            (define waiting (logic:all true missing))")
      (is (false? (read-value 'rejected)))
      (is (true? (read-value 'accepted)))
      (is (= :bool4/nothing (read-value 'waiting)))
      (host/extend-source! owner "(-> true missing)")
      (is (false? (read-value 'rejected)))
      (is (true? (read-value 'accepted)))
      (is (true? (read-value 'waiting)))
      (finally (host/close! owner)))))

(deftest readiness-checks-completed-observations
  (let [owner (host/open! (experiment-compiler))
        read-value #(connected/payload (session/binding-value (host/current-session owner) %))]
    (try
      (host/extend! owner (soul/extension owner (atom [])))
      (host/extend! owner (observation/facilities))
      (host/extend-source! owner (slurp (str root "/experiments/phase-orchestration.lain")))
      (host/extend-source! owner
                           "(define outputs (data:record :production 11 :rush 11 :discount 11 :subtotal 11
                                                         :delivery 11 :tax 11 :total 11 :reply 11))
                            (define missing (data:record :total (seq:of :question-premise)))
                            (define incomplete (experiment:ready? (data:record :outputs outputs :missing-supports missing)))
                            (define unknown-value (experiment:usable? :unknown))
                            (define contradictory-value (experiment:usable? :contradictory))
                            (define delayed)
                            (define ready (experiment:ready? delayed))")
      (is (false? (read-value 'incomplete)))
      (is (false? (read-value 'unknown-value)))
      (is (false? (read-value 'contradictory-value)))
      (is (= :bool4/nothing (read-value 'ready)))
      (host/extend-source! owner "(-> (data:record :outputs outputs :missing-supports (data:record)) delayed)")
      (is (true? (read-value 'ready)))
      (finally (host/close! owner)))))

(deftest lain-stops-after-two-corrections
  (let [result (trial! {"round-1" "(define a 1))" "round-1-repair-1" "(define b 2))" "round-1-repair-2" "(define c 3))"} false)]
    (is (= :failed (get-in result [:outcome :status])))
    (is (= 5 (count (:requests result))))
    (is (= 0 (get-in result [:outcome :feedback :corrections-remaining])))
    (is (= 3 (count (get-in result [:outcome :attempts]))))
    (is (:no-replayed-call result))))

(deftest lain-rejects-invalid-planning-before-another-stage
  (let [result (trial! {} true)]
    (is (= :invalid-analysis (get-in result [:outcome :reason])))
    (is (= ["analysis-1"] (mapv :id (:requests result))))
    (is (:no-replayed-call result))))

(deftest delayed-constant-record-fields-remain-readable-in-environment
  (let [owner (host/open! (experiment-compiler)) requests (atom [])]
    (try
      (host/extend! owner (soul/extension owner (atom [])))
      (host/extend! owner (observation/facilities))
      (host/extend! owner (model/declaration root "test/fixtures/environment-context-probe.lain"
                                             {:transport #(swap! requests conj %)}))
      (host/publish-input! owner
                           (fn [current]
                             [(session/publication-input (session/binding-cell current 'raw-input-text) "Analysis text")]))
      (host/step! owner)
      (let [snapshot (connected/payload (session/binding-value (host/current-session owner) 'observed))]
        (is (= {"phase" "generalization" "analysis-record" "Analysis text"
                "generalization-record" "No interface yet." "inspection-feedback" {:status :not-started}
                "ask" "Test question"} (:values snapshot)))
        (is (str/includes? (:text snapshot) "## phase\ngeneralization"))
        (is (not (str/includes? (:text snapshot) "contradictory")))
        (is (empty? @requests) "Importing model and policy definitions opens no worker request"))
      (finally (host/close! owner)))))

(deftest one-time-environment-snapshot-waits-and-does-not-refresh
  (let [owner (host/open! (experiment-compiler))
        read-value #(connected/payload (session/binding-value (host/current-session owner) %))
        publish (fn [value]
                  (host/publish-input! owner
                                       (fn [current]
                                         [(session/publication-input (session/binding-cell current 'mutable-source) value)]))
                  (host/step! owner))]
    (try
      (host/extend! owner (soul/extension owner (atom [])))
      (host/extend! owner (observation/facilities))
      (host/extend! owner (model/declaration root "test/fixtures/environment-context-probe.lain" {}))
      (is (= :bool4/nothing (read-value 'once-snapshot)))
      (publish "first")
      (is (= "first" (get-in (read-value 'once-snapshot) [:values "mutable-source"])))
      (publish "second")
      (is (= "second" (read-value 'mutable-source)))
      (is (= "first" (get-in (read-value 'once-snapshot) [:values "mutable-source"])))
      (finally (host/close! owner)))))

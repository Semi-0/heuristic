(load-file (str (System/getenv "LAIN_HARNESS_ROOT") "/tools/offline_uuid.clj"))
(require '[clojure.edn :as edn]
         '[clojure.string :as str]
         '[lain-harness.catalog :as catalog]
         '[lain-harness.context-store :as store]
         '[lain-harness.context-runtime :as context]
         '[lain-harness.context-transport :as transport]
         '[lain-harness.soul-session :as soul]
         '[lain-harness.experiment-observation :as observation]
         '[lain-harness.model-io-session :as model]
         '[propagators.compiler-2.session :as s]
         '[propagators.compiler-2.session.host :as h]
         '[propagators.compiler-2.session.connected :as connected]
         '[propagators.compiler-2.runtime.application :as application]
         '[propagators.network :as net])

(defn oracle [[q rush member cap]]
  (let [p (+ (* (min q 100) 2M) (* (min (max (- q 100) 0) 400) 1.60M)
             (* (max (- q 500) 0) 1.30M))
        r (if rush (* p 0.20M) 0M)
        discount (if member (min cap (* (+ p r) 0.10M)) 0M)
        subtotal (+ (- (+ p r) discount) 25M)
        delivery (if (>= subtotal 250) 0M 12M)]
    (.setScale (* (+ subtotal delivery) 1.08M) 2 java.math.RoundingMode/HALF_UP)))

(defn reachable? [graph from target]
  (loop [pending [from] seen #{}]
    (cond (empty? pending) false
          (> (count seen) 100000) :inspection-limit
          :else (let [v (peek pending) pending (pop pending)]
                  (cond (= v target) true
                        (seen v) (recur pending seen)
                        :else (recur (into pending (:outputs (get graph v))) (conj seen v)))))))

(defn applications [current catalog outcomes]
  (let [network (s/network current) graph (net/net-graph network)
        payload #(connected/payload (net/network-cell-strongest network %))]
    (into {} (for [[id {:keys [reference]}] @(:references catalog)
                   :let [callable (payload reference)
                         declaration (get callable application/declaration-id-key)
                         captured (get callable application/captured-environment-key)]]
               [id {:reference reference :declaration declaration :captured captured
                    :applications
                    (vec (for [a (application/application-topologies network)
                               :let [operator (payload (:operator-id a))]
                               :when (and declaration (= declaration (get operator application/declaration-id-key))
                                          (= captured (get operator application/captured-environment-key)))]
                           (assoc a :arguments (mapv payload (:argument-ids a))
                                  :result (payload (:result-id a))
                                  :delivers-to (vec (for [[qid outcome] outcomes
                                                         :let [target (get-in outcome [:work :attempt :diagnostic :observation :reference])]
                                                         :when (and target (true? (reachable? graph (:result-id a) target)))] qid)))))}]))))

(let [root (System/getenv "LAIN_HARNESS_ROOT") evidence (System/getenv "LAIN_EVIDENCE_DIR")
      mode (System/getenv "LAIN_CONTEXT_MODE") preparation (System/getenv "LAIN_CONTEXT_PREPARATION")
      _ (when-not (#{"prepare" "indexed" "full" "ablation"} mode) (throw (ex-info "Unknown condition" {:mode mode})))
      verification (edn/read-string (slurp (System/getenv "LAIN_TOKENIZER_VERIFICATION")))
      _ (assert (and (:verified? verification) (= transport/settings (:settings verification))) "Verify matching tokenizer first")
      cases (edn/read-string (slurp (str root "/tools/context_cases.edn")))
      questions (assoc (:questions cases) 0 (str (:rules cases) "\n" (first (:questions cases))))
      expected (zipmap questions (map #(reduce + (map oracle %)) (:orders cases)))
      last-task (Long/parseLong (or (System/getenv "LAIN_CONTEXT_LAST_TASK") "4"))
      _ (assert (<= 1 last-task 4) "Last task must be 1..4")
      selected (case mode "prepare" [0] "ablation" [(Long/parseLong (or (System/getenv "LAIN_CONTEXT_ABLATION") "1"))] (vec (range (inc last-task))))
      database (store/install! (catalog/open! (str evidence "/database")))
      base (model/compiler)
      owner (h/open! (s/create-session (assoc (s/session-identity base)
                                             :native-extensions (:bootstrap (s/session-declarations base))
                                             :propagation-options {:max-steps 500000 :timeout-ms 600000})))
      journal (atom []) calls (atom []) counts (atom {}) responses (atom [])
      measurements (atom []) http-events (atom {}) observed-tasks (atom #{})
      write! (fn [id kind v]
               (when (= kind :http) (swap! http-events assoc id v))
               (spit (str evidence "/" id "-" (name kind) ".edn") (pr-str v)))
      provider (transport/transport write!)
      record-count (fn [request]
                     (let [n (transport/token-count request)]
                       (swap! counts assoc (:id request) {:tokens n :messages (:messages request)})
                       (write! (:id request) :budget {:tokens n :limit 12000}) n))
      source (apply str
                    (for [[position i] (map-indexed vector selected)
                          :let [prior (when (pos? position) (nth selected (dec position)))
                                history (if (and prior (= mode "full"))
                                          (str "(str history-" prior " \"\\n\" (context:text (data:get :transcript outcome-" prior ")))" ) "\"\"")]]
                      (str "(define task-" i " " (pr-str (nth questions i)) ")\n"
                           "(define history-" i " " history ")\n"
                           "(define outcome-" i " (context:task (cell:reference pricing-context-seed) (cell:reference (model:input context-model))"
                           " (cell:reference context-response) (cell:reference context-requests)"
                           " \"q" i "\" " (* 100 i) " "
                           (if prior (str "(context:gate task-" i " (data:has? outcome-" prior " :status))") (str "task-" i))
                           " history-" i "))\n")))]
  (try
    (spit (str evidence "/task-orchestration.lain") source)
    (spit (str evidence "/settings.edn") (pr-str {:mode mode :selected selected :settings transport/settings :questions questions :expected expected}))
    (add-watch journal ::archive (fn [_ _ _ entries] (write! "all" :admissions entries)))
    (add-watch (:journal database) ::archive (fn [_ _ _ entries] (write! "all" :catalog entries)))
    (h/extend! owner (soul/extension owner journal))
    (h/extend! owner (observation/facilities))
    (h/extend! owner (store/facilities database))
    (h/extend! owner (context/facilities database record-count
                        (fn [task phase observed]
                          (let [result ((context/validator expected) task phase observed)]
                            (swap! measurements conj {:id (:id (last @calls)) :phase phase
                                                      :decision (get-in observed [:result :decision])
                                                      :validation (:status result)})
                            result))))
    ;; Experimental distractors carry explicit synthetic evidence, no solved task.
    (when-not (= mode "ablation")
      (store/archive! database :fixture/distractors :fixture "Unrelated temperature conversion; obsolete policy capped member discounts at $10. Synthetic distractors, not current rules.")
      (store/index! database "{:catalog/id :unrelated/temperature-v1 :catalog/description \"Temperature unit conversion\" :catalog/status :current :catalog/evidence [[:catalog/id :fixture/distractors]]}")
      (store/index! database "{:catalog/id :obsolete/member-policy-v0 :catalog/description \"Historical member discount cap $10; obsolete, do not use for current pricing\" :catalog/status :superseded :catalog/evidence [[:catalog/id :fixture/distractors]]}"))
    (h/extend! owner
      (model/declaration root "experiments/context.lain"
        {:transport
         (fn [request]
           (let [id (:id request)
                 replay? (and (#{"indexed" "full"} mode) (str/starts-with? id "q0-"))
                 measured (get @counts id)]
             (assert (= (:messages request) (:messages measured)) "Request differs from counted packet")
             (write! id :request (dissoc request :cancelled?))
             (swap! calls conj {:id id :replay? replay? :tokens (:tokens measured)})
             (let [reply (if replay? (edn/read-string (slurp (str preparation "/" id "-response.edn")))
                             (provider request))]
               ;; Preserve transport evidence before handing the response to Lain.
               (store/archive! database (keyword "transport" id) :response (pr-str reply))
               (write! id :response reply)
               (swap! responses conj {:id id :replay? replay? :reply reply})
               (when (and (not replay?) (not= (:tokens measured) (get-in reply [:usage :prompt_tokens])))
                 (throw (ex-info "Serving tokenizer disagrees with provider usage"
                                 {:id id :counted (:tokens measured) :usage (:usage reply)})))
               reply)))}))
    (h/extend! owner (s/session-extension {:id :tasks :files {"tasks.lain" source}}))
    (let [deadline (+ (System/currentTimeMillis) 111600000)]
      (loop []
        (when (seq (h/failures owner)) (throw (ex-info "Host failure" {:failures (h/failures owner)})))
        (let [outcomes (into {} (for [i selected] [i (connected/payload (s/binding-value (h/current-session owner) (symbol (str "outcome-" i))))]))
              finished? (every? #(and (map? %) (:status %)) (vals outcomes))]
          (doseq [[i outcome] outcomes
                  :when (and (map? outcome) (:status outcome) (not (@observed-tasks i)))]
            (write! (str "q" i) :topology
                    (applications (h/current-session owner) database {i outcome}))
            (swap! observed-tasks conj i))
          (write! "live" :status {:at (str (java.time.Instant/now)) :calls @calls
                                  :statuses (into {} (map (fn [[i v]] [i (when (map? v) (:status v))]) outcomes))})
          (cond
            finished?
            (let [reuse (applications (h/current-session owner) database outcomes)
                  result {:mode mode :outcomes outcomes :calls @calls :responses @responses
                          :correct (into {} (for [[i v] outcomes] [i (= :delivered (:status v))]))
                          :indexing (into {} (for [[i v] outcomes] [i (get-in v [:index :status])]))
                          :within-budget? (every? #(<= (:tokens %) 12000) @calls)
                          :provider-calls (count (remove :replay? @calls))
                          :measurements @measurements
                          :costs (vec (for [{:keys [id replay? reply]} @responses
                                            :let [m (first (filter #(= id (:id %)) @measurements))]]
                                        {:id id :replay? replay?
                                         :preparation? (str/starts-with? id "q0-")
                                         :stage (if (str/includes? id "-index") :indexing
                                                    (if (= :retrieve (:decision m)) :retrieval :execution))
                                         :repair? (str/includes? id "-correction")
                                         :usage (when-not replay? (:usage reply))
                                         :elapsed-ms (when-not replay? (get-in @http-events [id :elapsed-ms]))
                                         :input-tokens (get-in @counts [id :tokens])}))
                          :session (s/session-identity (h/current-session owner))}]
              (write! "final" :result result)
              (write! "final" :applications reuse)
              (write! "final" :catalog @(:journal database))
              (prn (dissoc result :outcomes :responses)))
            (> (System/currentTimeMillis) deadline) (throw (ex-info "Experiment deadline reached; no automatic retry" {}))
            :else (do (Thread/sleep 1000) (recur))))))
    (catch Throwable e
      (let [failure (Throwable->map e)]
        (write! "fatal" :error failure)
        (store/archive! database :run/fatal :validation (pr-str failure))
        (write! "final" :catalog @(:journal database))
        (write! "failed" :state {:calls @calls :responses @responses :measurements @measurements}))
      (throw e))
    (finally (write! "final" :cleanup (h/close! owner)) (shutdown-agents))))

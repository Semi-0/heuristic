(load-file (str (System/getenv "LAIN_HARNESS_ROOT") "/tools/offline_uuid.clj"))
(require '[clojure.edn :as edn]
         '[lain-harness.catalog :as catalog]
         '[lain-harness.source-restore :as restore]
         '[lain-harness.soul-session :as soul]
         '[lain-harness.experiment-observation :as observation]
         '[lain-harness.model-io-session :as model]
         '[lain-harness.blazux :as blazux]
         '[propagators.compiler-2.session :as session]
         '[propagators.compiler-2.session.host :as host]
         '[propagators.compiler-2.session.connected :as connected]
         '[propagators.compiler-2.runtime.application :as application]
         '[propagators.network :as net])

(defn applications [current store]
  (let [network (session/network current)
        payload #(connected/payload (net/network-cell-strongest network %))]
    (into {} (for [[id {:keys [reference]}] @(:references store)
                   :let [callable (payload reference)
                         declaration (get callable application/declaration-id-key)
                         captured (get callable application/captured-environment-key)]]
               [id {:reference reference :declaration declaration :captured captured
                    :applications (vec (for [a (application/application-topologies network)
                                             :let [operator (payload (:operator-id a))]
                                             :when (and declaration (= declaration (get operator application/declaration-id-key))
                                                        (= captured (get operator application/captured-environment-key)))]
                                         (assoc a :arguments (mapv payload (:argument-ids a)) :result (payload (:result-id a)))))}]))))

(let [mode (first *command-line-args*)
      _ (assert (#{"prepare" "reopen"} mode))
      root (System/getenv "LAIN_HARNESS_ROOT")
      evidence (System/getenv "LAIN_EVIDENCE_DIR")
      store (catalog/open! (str evidence "/database"))
      _ (assert (empty? @(:references store)))
      bundle (if (= mode "prepare")
               (restore/save-bundle! store
                                     (restore/build-bundle (array-map :archive/build (restore/read-archive store :archive/build)
                                                                      :archive/compose (restore/read-archive store :archive/compose)) :event-cost/v1))
               (restore/load-bundle store))
      before (when (= mode "reopen") (edn/read-string {:default tagged-literal} (slurp (str evidence "/prepare-result.edn"))))
      base (model/compiler)
      compiler (session/create-session (assoc (session/session-identity base)
                                              :native-extensions (:bootstrap (session/session-declarations base))
                                              :runtime-options (:options (session/session-declarations base))
                                              :propagation-options {:max-steps 300000 :timeout-ms 180000}))
      owner (host/open! compiler)
      diagnostic-time (atom 0)
      journal (atom []) requests (atom []) responses (atom []) result (atom nil)
      provider (blazux/completion-transport {:timeout-ms 600000 :max-tokens 8192 :enable-thinking false})
      read-value #(connected/payload (session/binding-value (host/current-session owner) %))
      publish (fn [entries]
                (host/publish-input! owner (fn [current] (mapv (fn [[n v]] (session/publication-input (session/binding-cell current n) v)) entries)))
                (host/step! owner))
      wait-for (fn [predicate]
                 (let [deadline (+ (System/currentTimeMillis) (Long/parseLong (or (System/getenv "LAIN_OUTCOME_TIMEOUT_MS") "900000")))]
                   (loop []
                     (when (> (- (System/currentTimeMillis) @diagnostic-time) 10000)
                       (reset! diagnostic-time (System/currentTimeMillis))
                       (spit (str evidence "/" mode "-live-state.edn")
                             (pr-str {:ports (into {} (for [n '[restore-library-receipt restore-closure-receipt restoration-ready restored-library-status restored-closure-status]] [n (read-value n)]))
                                      :registered (keys @(:references store))
                                      :edges (observation/binding-views (session/network (host/current-session owner)) '#{id source completed-response target})})))
                     (when-let [error (first (host/failures owner))] (throw error))
                     (doseq [n '[restore-library-receipt restore-closure-receipt durable-query-receipt durable-use-receipt]]
                       (when (= :rejected (:status (read-value n))) (throw (ex-info "Source admission failed" {:port n :receipt (read-value n)}))))
                     (cond (predicate) true
                           (> (System/currentTimeMillis) deadline) (throw (ex-info "Durable experiment timed out" {:receipts (mapv read-value '[restore-library-receipt restore-closure-receipt durable-query-receipt durable-use-receipt])}))
                           :else (do (Thread/sleep 50) (recur))))))]
  (try
    (spit (str evidence "/" mode "-bundle.edn") (pr-str bundle))
    (add-watch journal ::receipts (fn [_ _ _ entries] (spit (str evidence "/" mode "-admissions.edn") (pr-str entries))))
    (host/extend! owner (soul/extension owner journal))
    (host/extend! owner (catalog/facilities store))
    (host/extend! owner (restore/facilities store))
    (host/extend! owner (observation/facilities))
    (host/extend! owner (model/declaration root "experiments/catalog-durable.lain"
                                           {:transport (fn [request]
                                                         (assert (= mode "reopen") "Preparation must not call the model")
                                                         (println :request (:id request)) (flush)
                                                         (swap! requests conj (dissoc request :cancelled?))
                                                         (spit (str evidence "/" (:id request) "-context.txt") (:text request))
                                                         (let [reply (provider request)]
                                                           (swap! responses conj reply)
                                                           (spit (str evidence "/" (:id request) "-response.edn") (pr-str reply))
                                                           (spit (str evidence "/" (:id request) ".lain") (:text reply))
                                                           (println :response (:id request) (:finish-reason reply)) (flush)
                                                           reply))}))
    (wait-for #(true? (read-value 'restoration-ready)))
    (assert (= (set (:operators bundle)) (set (keys @(:references store)))))
    (assert (empty? @requests) "Restoration must not replay any model calls")
    (assert (= (apply str (map :source (:modules bundle))) (apply str (map :source @journal))) "Admitted restoration source must be byte-exact")
    (let [common {:pid (.pid (java.lang.ProcessHandle/current))
                  :identity (session/session-identity (host/current-session owner))
                  :source-hashes (mapv :sha256 (:modules bundle))
                  :bundle-hash (restore/sha256 (pr-str bundle))
                  :operators (:operators bundle)
                  :restoration-admitted-forms (count @journal)
                  :restoration-model-calls (count @requests)
                  :restoration-old-reply-absent (not (string? (read-value 'user-output)))}]
      (if (= mode "prepare")
        (do
          (host/extend! owner (session/session-extension
                               {:id :durable-canary :files {"durable-canary.lain"
                                                            "(define canary ((cell:value (catalog:resolve :event-cost/v1)) 120 3 8 15 0.08))"}}))
          (assert (< (abs (- (read-value 'canary) 518.4)) 0.00001))
          (reset! result (assoc common :canary (read-value 'canary) :provider-calls (count @requests)))
          (spit (str evidence "/prepare-applications.edn") (pr-str (applications (host/current-session owner) store))))
        (do
          (assert (:closed before))
          (assert (not (.isPresent (java.lang.ProcessHandle/of (:pid before)))) "Producer process must have exited")
          (assert (not= (:pid before) (:pid common)))
          (assert (not= (:identity before) (:identity common)))
          (assert (= (:bundle-hash before) (:bundle-hash common)))
          (publish {'quantity 75 'unit-price 6 'hours 4 'hourly-rate 30 'surcharge-rate 0.05 'start true})
          (wait-for #(and (= :admitted (:status (read-value 'durable-use-receipt))) (number? (read-value 'answer)) (string? (read-value 'user-output))))
          (let [initial (read-value 'answer) reply (read-value 'user-output)
                applied (applications (host/current-session owner) store)
                call-count (count @requests)
                _ (publish {'quantity 100})
                updated (read-value 'answer)
                checks {:initial-correct (< (abs (- initial 598.5)) 0.00001)
                        :updated-correct (< (abs (- updated 756.0)) 0.00001)
                        :reply-updated (not= reply (read-value 'user-output))
                        :two-new-model-calls (= 2 call-count (count @requests))
                        :old-process-exited true :fresh-session true :source-unchanged true
                        :no-old-model-replay (zero? (:restoration-model-calls common))
                        :no-old-reply (:restoration-old-reply-absent common)
                        :no-new-registrations (= 3 (count @(:references store)))
                        :restored-closure-invoked (= 1 (count (get-in applied [:event-cost/v1 :applications])))
                        :captured-dependencies-used (and (= 2 (count (get-in applied [:operator/linear-v1 :applications])))
                                                         (= 1 (count (get-in applied [:operator/percentage-v1 :applications]))))
                        :database-discovery (boolean (some #(and (= :query (:operation %))
                                                                 (some (fn [row] (some #{:event-cost/v1} row)) (:result %))) @(:journal store)))
                        :environment-only (every? #(= (:text %) (:content (first (:messages %)))) @requests)}]
            (spit (str evidence "/reopen-applications.edn") (pr-str applied))
            (reset! result (assoc common :checks checks :initial initial :updated updated :reply reply :reply-after (read-value 'user-output)
                                  :provider-calls call-count :selection-notes (read-value 'selection-notes)))
            (assert (every? true? (vals checks)) (str checks))))))
    (catch Throwable error
      (spit (str evidence "/" mode "-failure.edn") (pr-str {:message (ex-message error) :data (ex-data error)}))
      (throw error))
    (finally
      (let [closed (= :closed (:status (host/close! owner)))]
        (when @result
          (swap! result assoc :closed closed)
          (spit (str evidence "/" mode "-result.edn") (pr-str @result))
          (prn @result))
        (spit (str evidence "/" mode "-evidence.edn") (pr-str {:requests @requests :responses @responses :catalog @(:journal store) :admissions @journal :closed closed})))
      (shutdown-agents))))

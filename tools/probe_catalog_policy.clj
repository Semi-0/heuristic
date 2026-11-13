(ns tools.probe-catalog-policy)

(load-file (str (System/getenv "LAIN_HARNESS_ROOT") "/tools/offline_uuid.clj"))
(require '[clojure.edn :as edn]
         '[lain-harness.premise-experiment :as premise]
         '[lain-harness.session-publication :as publication]
         '[lain-harness.catalog :as catalog]
         '[lain-harness.catalog-selection :as selection]
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

(let [root (System/getenv "LAIN_HARNESS_ROOT")
      evidence (System/getenv "LAIN_EVIDENCE_DIR")
      policy-mode (or (System/getenv "LAIN_POLICY_MODE") "fresh")
      _ (assert (#{"control" "fresh"} policy-mode))
      controlled? (= "control" policy-mode)
      thinking? (= "1" (System/getenv "LAIN_POLICY_THINKING"))
      store (catalog/open! (str evidence "/database"))
      before (edn/read-string {:default tagged-literal} (slurp (str evidence "/accumulate-result.edn")))
      base (model/compiler)
      compiler (session/create-session (assoc (session/session-identity base)
                                              :native-extensions (:bootstrap (session/session-declarations base))
                                              :runtime-options (:options (session/session-declarations base))
                                              :propagation-options {:max-steps 300000 :timeout-ms 180000}))
      owner (host/open! compiler)
      journal (atom []) requests (atom []) responses (atom []) snapshots (atom []) checks (atom {})
      provider-count (atom 0)
      provider (blazux/completion-transport {:timeout-ms 600000 :max-tokens 8192 :enable-thinking thinking? :reasoning-effort "medium" :thinking-token-budget 4096})
      read-value #(connected/payload (session/binding-value (host/current-session owner) %))
      publish (fn [entries]
                (host/publish-input! owner (fn [current] (mapv (fn [[n v]] (session/publication-input (session/binding-cell current n) v)) entries)))
                (host/step! owner))
      revision (fn [name epoch status text]
                 (host/extend! owner (publication/declaration (host/current-session owner) name epoch status text)))
      check! (fn [name passed] (swap! checks assoc name (boolean passed)))
      premise-values {'old-policy "Delivery is added after surcharge; historical interpretation."
                      'new-policy "Delivery receives the same surcharge as pieces and labor."
                      'question-old "What was the historical quote under the old policy?"
                      'question-current "What is the revised quote under the new policy?"
                      'unrelated-note "Delivery vehicle is blue."}]
  (try
    (assert (:closed before))
    (assert (not (.isPresent (java.lang.ProcessHandle/of (:pid before)))))
    (assert (empty? @(:references store)))
    (add-watch journal ::receipts (fn [_ _ _ entries] (spit (str evidence "/admissions.edn") (pr-str entries))))
    (host/extend! owner (soul/extension owner journal))
    (host/extend! owner (catalog/facilities store))
    (host/extend! owner (selection/facilities store))
    (host/extend! owner (observation/facilities))
    (host/extend! owner (premise/facilities))
    (host/extend! owner
                  (model/declaration root "experiments/catalog-policy.lain"
                                     {:transport
                                      (fn [request]
                                        (let [id (:id request)]
                                          (when (#{"discover" "choose"} id)
                                            (assert (empty? @(:references store))))
                                          (swap! requests conj (dissoc request :cancelled?))
                                          (spit (str evidence "/" id "-context.txt") (:text request))
                                          (println :request id) (flush)
                                          (let [reply (if controlled?
                                                        {:kind :reply :finish-reason "stop" :truncated? false :thinking ""
                                                         :text (slurp (str root "/test/fixtures/catalog-policy/" id ".lain"))}
                                                        (do (swap! provider-count inc) (provider request)))]
                                            (swap! responses conj (assoc reply :id id))
                                            (spit (str evidence "/" id "-response.edn") (pr-str reply))
                                            (spit (str evidence "/" id ".lain") (:text reply))
                                            (println :response id (:finish-reason reply)) (flush)
                                            reply)))}))
    (doseq [[name text] premise-values] (revision name 1 :active text))
    (publish {'quantity 100 'unit-price 6 'hours 6 'hourly-rate 30 'surcharge-rate 0.05 'delivery-charge 40 'accumulate-mode false})
    (publish {'start true})
    (let [deadline (+ (System/currentTimeMillis) 900000)]
      (loop [last-report 0]
        (when-let [error (first (host/failures owner))] (throw error))
        (let [outcomes (mapv read-value '[discover-outcome choose-outcome execute-outcome])
              now (System/currentTimeMillis)]
          (when (> (- now last-report) 10000)
            (spit (str evidence "/state.edn") (pr-str {:outcomes outcomes :selected (read-value 'selected-id)})))
          (doseq [n '[restore-receipt-0 restore-receipt-1 restore-receipt-2]]
            (when (= :rejected (:status (read-value n))) (throw (ex-info "Restoration rejected" {:receipt (read-value n)}))))
          (cond (some #(= :failed (:status %)) outcomes) (throw (ex-info "Lain repair budget exhausted" {:outcomes outcomes}))
                (= :done (:status (last outcomes))) true
                (> now deadline) (throw (ex-info "Policy experiment timeout" {:outcomes outcomes}))
                :else (do (Thread/sleep 50) (recur (if (> (- now last-report) 10000) now last-report)))))))
    (let [current (host/current-session owner)
          source-names (into {} (for [name (concat (keys premise-values) '[quantity unit-price hours hourly-rate surcharge-rate delivery-charge])] [(session/binding-cell current name) name]))
          snapshot (fn [label expected]
                     (let [current (host/current-session owner)
                           values (into {} (for [name '[base-answer historical-answer answer user-output]
                                                 :let [view (premise/inspect-value (session/network current) (session/binding-cell current name))]]
                                             [name (assoc view :support-names (set (map #(get source-names (:source %) (:source %)) (:support view))))]))
                           actual (mapv #(get-in values [% :value]) '[base-answer historical-answer answer])]
                       (swap! snapshots conj {:label label :values values})
                       (check! (keyword (str (name label) "-values"))
                               (every? true? (map (fn [a b] (if (number? b) (and (number? a) (< (abs (- a b)) 0.00001)) (= a b))) actual expected)))
                       (check! (keyword (str (name label) "-reply"))
                               (if (= :unknown (last expected)) (= :unknown (get-in values ['user-output :value]))
                                   (string? (get-in values ['user-output :value]))))
                       values))
          calls (count @requests) admitted (count @journal)
          initial (snapshot :initial [819 859 861])
          applied (applications current store)
          bundle (read-value 'selected-bundle)]
      (check! :selected-compatible-component (= :event-cost/v1 (read-value 'selected-id)))
      (check! :incompatible-whole-not-restored (not (contains? @(:references store) :delivered-event-cost/v1)))
      (check! :only-required-operators (= #{:event-cost/v1 :operator/linear-v1 :operator/percentage-v1} (set (keys @(:references store)))))
      (check! :retained-callable-used-once (= 1 (count (get-in applied [:event-cost/v1 :applications]))))
      (check! :captured-dependencies-used (and (= 2 (count (get-in applied [:operator/linear-v1 :applications]))) (= 1 (count (get-in applied [:operator/percentage-v1 :applications])))))
      (check! :historical-support (= '#{quantity unit-price hours hourly-rate surcharge-rate delivery-charge old-policy question-old} (get-in initial ['historical-answer :support-names])))
      (check! :current-support (= '#{quantity unit-price hours hourly-rate surcharge-rate delivery-charge new-policy question-current} (get-in initial ['answer :support-names])))
      (check! :reply-support (= '#{quantity unit-price hours hourly-rate surcharge-rate delivery-charge new-policy question-current} (get-in initial ['user-output :support-names])))
      (check! :base-independent (= '#{quantity unit-price hours hourly-rate surcharge-rate} (get-in initial ['base-answer :support-names])))
      (check! :source-exact (= (apply str (map :source (:modules bundle)))
                               (apply str (map :source (filter #(.startsWith (or (:response %) "") "restore/") @journal)))))
      (check! :query-returned-incompatible-candidate (boolean (some #(and (= :query (:operation %)) (some (fn [row] (some #{:delivered-event-cost/v1} row)) (:result %))) @(:journal store))))
      (publish {'delivery-charge 60})
      (snapshot :delivery-change [819 879 882])
      (publish {'quantity 120})
      (snapshot :quantity-change [945 1005 1008])
      (publish {'surcharge-rate 0.10})
      (snapshot :rate-change [990 1050 1056])
      (revision 'unrelated-note 2 :retracted (premise-values 'unrelated-note))
      (snapshot :unrelated-withdrawn [990 1050 1056])
      (doseq [[name historical?] [['old-policy true] ['new-policy false] ['question-old true] ['question-current false]]]
        (revision name 2 :retracted (premise-values name))
        (snapshot (keyword (str name "-withdrawn")) (if historical? [990 :unknown 1056] [990 1050 :unknown]))
        (revision name 3 :active (premise-values name))
        (snapshot (keyword (str name "-restored")) [990 1050 1056]))
      (publish {'start true})
      (check! :no-extra-inference (= calls (count @requests)))
      (check! :no-source-readmission (= admitted (count @journal)))
      (check! :bounded-requests (<= 3 calls 9))
      (check! :environment-only (every? #(= (:text %) (:content (first (:messages %)))) @requests))
      (spit (str evidence "/applications.edn") (pr-str applied))
      (spit (str evidence "/selected-bundle.edn") (pr-str bundle))
      (spit (str evidence "/selection-notes.txt") (str (read-value 'selection-notes)))
      (assert (every? true? (vals @checks)) (str @checks)))
    (catch Throwable error
      (spit (str evidence "/failure.edn") (pr-str {:message (ex-message error) :data (ex-data error)}))
      (throw error))
    (finally
      (let [closed (= :closed (:status (host/close! owner)))]
        (spit (str evidence "/evidence.edn") (pr-str {:requests @requests :responses @responses :catalog @(:journal store) :admissions @journal :snapshots @snapshots}))
        (spit (str evidence "/result.edn") (pr-str {:closed closed :controlled? controlled? :thinking? thinking? :provider-calls @provider-count :request-count (count @requests) :checks @checks}))
        (prn {:closed closed :provider-calls @provider-count :checks @checks}))
      (shutdown-agents))))

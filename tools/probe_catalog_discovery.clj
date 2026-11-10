(load-file (str (System/getenv "LAIN_HARNESS_ROOT") "/tools/offline_uuid.clj"))
(require '[clojure.edn :as edn]
         '[lain-harness.catalog :as catalog]
         '[lain-harness.soul-session :as soul]
         '[lain-harness.experiment-observation :as observation]
         '[lain-harness.model-io-session :as model]
         '[lain-harness.blazux :as blazux]
         '[propagators.compiler-2.session :as session]
         '[propagators.compiler-2.session.host :as host]
         '[propagators.compiler-2.session.connected :as connected]
         '[propagators.compiler-2.runtime.application :as application]
         '[propagators.network :as net])

(defn application-evidence [current store]
  (let [network (session/network current)
        payload #(connected/payload (net/network-cell-strongest network %))]
    (into {} (for [[id {:keys [reference]}] @(:references store)
                   :let [callable (payload reference)
                         declaration (get callable application/declaration-id-key)
                         captured (get callable application/captured-environment-key)]]
               [id {:reference reference :declaration declaration :captured captured
                    :applications (vec (for [a (application/application-topologies network)
                                             :let [operator (payload (:operator-id a))]
                                             :when (and declaration
                                                        (= declaration (get operator application/declaration-id-key))
                                                        (= captured (get operator application/captured-environment-key)))]
                                         (assoc a :arguments (mapv payload (:argument-ids a))
                                                :result (payload (:result-id a)))))}]))))

(let [root (System/getenv "LAIN_HARNESS_ROOT")
      evidence (System/getenv "LAIN_EVIDENCE_DIR")
      thinking? (not= "false" (System/getenv "LAIN_DISCOVERY_THINKING"))
      cumulative? (= "cumulative" (System/getenv "LAIN_DISCOVERY_MODE"))
      baseline? (= "baseline" (System/getenv "LAIN_DISCOVERY_MODE"))
      library-response (System/getenv "LAIN_LIBRARY_RESPONSE")
      replay-dir (System/getenv "LAIN_REPLAY_DIR")
      _ (.mkdirs (java.io.File. evidence))
      store (catalog/open! (str evidence "/database"))
      _ (catalog/seed! store [{:catalog/id :cap/linear} {:catalog/id :cap/percentage} {:catalog/id :cap/maximum}
                              {:catalog/id :cap/batch-cost :catalog/depends-on [[:catalog/id :cap/linear]]}
                              {:catalog/id :cap/event-cost :catalog/depends-on [[:catalog/id :cap/batch-cost] [:catalog/id :cap/percentage]]}])
      base (model/compiler)
      compiler (session/create-session (assoc (session/session-identity base)
                                              :native-extensions (:bootstrap (session/session-declarations base))
                                              :runtime-options (:options (session/session-declarations base))
                                              :propagation-options {:max-steps 300000 :timeout-ms 180000}))
      owner (host/open! compiler)
      diagnostic-time (atom 0)
      admissions (atom []) requests (atom []) responses (atom [])
      provider (blazux/completion-transport {:timeout-ms 600000 :max-tokens 8192 :enable-thinking (not= "false" (System/getenv "LAIN_DISCOVERY_THINKING"))
                                             :reasoning-effort "medium" :thinking-token-budget 4096})
      read-value #(connected/payload (session/binding-value (host/current-session owner) %))
      publish (fn [entries] (host/publish-input! owner (fn [current]
                                                         (mapv (fn [[n v]] (session/publication-input (session/binding-cell current n) v)) entries)))
                (host/step! owner))]
  (try
    (add-watch admissions ::journal (fn [_ _ _ entries] (spit (str evidence "/admissions-live.edn") (pr-str entries))))
    (host/extend! owner (soul/extension owner admissions))
    (host/extend! owner (observation/facilities))
    (host/extend! owner (catalog/facilities store))
    (host/extend! owner (model/declaration root (cond cumulative? "experiments/catalog-cumulative.lain" baseline? "experiments/catalog-baseline.lain" :else "experiments/catalog-discovery.lain")
                                           {:transport (fn [request]
                                                         (let [id (:id request)]
                                                           (println :request id) (flush)
                                                           (swap! requests conj (dissoc request :cancelled?))
                                                           (spit (str evidence "/" id "-context.txt") (:text request))
                                                           (let [reply (if replay-dir
                                                                         (edn/read-string (slurp (str replay-dir "/" id "-response.edn")))
                                                                         (if (and cumulative? (= id "build"))
                                                                           {:kind :reply :text (slurp (str root "/test/fixtures/catalog/library.lain")) :thinking "" :finish-reason "stop" :truncated? false}
                                                                           (if (and library-response (= id "build"))
                                                                             (edn/read-string (slurp library-response))
                                                                             (provider request))))]
                                                             (swap! responses conj reply)
                                                             (spit (str evidence "/" id "-response.edn") (pr-str reply))
                                                             (spit (str evidence "/" id ".lain") (:text reply))
                                                             (println :response id (:finish-reason reply)) (flush)
                                                             reply)))}))
    (when (System/getenv "LAIN_DIAGNOSTIC_ONLY")
      (spit (str evidence "/boot-state.edn")
            (pr-str {:top (into {} (for [n '[cumulative-seed seed add-binding str catalog-guide catalog-example-guide cumulative-catalog-guide]]
                                     [n (let [v (read-value n)] (if (map? v) (select-keys v [:id]) v))]))
                     :locals (observation/binding-views (session/network (host/current-session owner))
                                                        '#{c0 c1 c2 c12 cumulative-seed seed add-binding str catalog-guide catalog-example-guide})}))
      (throw (ex-info "Diagnostic-only boot completed" {})))
    (let [identity (session/session-identity (host/current-session owner))]
      (publish (merge {'quantity 120 'unit-price 3 'hours 8 'hourly-rate 15 'surcharge-rate 0.08 'start true}
                      (when cumulative? {'quantity-next 200 'unit-price-next 4 'hours-next 10 'hourly-rate-next 20 'surcharge-rate-next 0.10})))
      (let [deadline (+ (System/currentTimeMillis) (Long/parseLong (or (System/getenv "LAIN_OUTCOME_TIMEOUT_MS") "1800000")))]
        (loop []
          (when (> (- (System/currentTimeMillis) @diagnostic-time) 10000)
            (reset! diagnostic-time (System/currentTimeMillis))
            (spit (str evidence "/live-state.edn")
                  (pr-str {:receipts (mapv read-value (if cumulative? '[library-admission search-admission compose-admission search-next-admission apply-next-admission] '[library-admission search-admission solve-admission]))
                           :ready (read-value 'library-ready)
                           :stages (observation/binding-views (session/network (host/current-session owner))
                                                              '#{snapshot outgoing packet v1 v2 v3 trigger base task memory c0 c1 c2 c12 cumulative-seed seed add-binding str catalog-guide catalog-example-guide})})))
          (when-let [e (first (host/failures owner))] (throw e))
          (doseq [name (if cumulative? '[library-admission search-admission compose-admission search-next-admission apply-next-admission] '[library-admission search-admission solve-admission])]
            (when (= :rejected (:status (read-value name)))
              (throw (ex-info "Source rejected" {:binding name :receipt (read-value name)}))))
          (cond
            (and (= :admitted (:status (read-value (if cumulative? 'apply-next-admission 'solve-admission)))) (number? (read-value (if cumulative? 'answer-next 'answer)))) nil
            (> (System/currentTimeMillis) deadline) (throw (ex-info "Discovery experiment timed out" {:receipts (mapv read-value '[library-admission search-admission solve-admission])}))
            :else (do (Thread/sleep 100) (recur)))))
      (if cumulative?
        (let [current (host/current-session owner)
              applications (application-evidence current store)
              original #{:operator/linear-v1 :operator/percentage-v1 :operator/legacy-v1 :operator/max-v1}
              added (vec (remove original (keys applications)))
              new-id (first added)
              queries (vec (filter #(= :query (:operation %)) @(:journal store)))
              initial [(read-value 'answer) (read-value 'answer-next)]
              examples [(read-value 'example-linear) (read-value 'example-percentage)]
              request-count (count @requests)
              _ (publish {'quantity 140})
              first-changed [(read-value 'answer) (read-value 'answer-next)]
              _ (publish {'hours-next 12})
              second-changed [(read-value 'answer) (read-value 'answer-next)]
              close? (fn [a b] (and (number? a) (< (abs (- a b)) 0.00001)))
              checks {:examples-verified (and (close? (first examples) 12) (close? (second examples) 108))
                      :initial-correct (every? true? (map close? initial [518.4 1100]))
                      :first-isolated (every? true? (map close? first-changed [583.2 1100]))
                      :second-isolated (every? true? (map close? second-changed [583.2 1144]))
                      :one-new-closure (= 1 (count added))
                      :closure-applied-twice (= 2 (count (get-in applications [new-id :applications])))
                      :prior-operators-composed (and (>= (count (get-in applications [:operator/linear-v1 :applications])) 5)
                                                     (>= (count (get-in applications [:operator/percentage-v1 :applications])) 3))
                      :distractors-unused (and (empty? (get-in applications [:operator/legacy-v1 :applications]))
                                               (empty? (get-in applications [:operator/max-v1 :applications])))
                      :database-rediscovery (boolean (and (= 2 (count queries))
                                                          (some #{new-id} (map first (:result (second queries))))))
                      :examples-retrieved (and (seq (:result (first queries)))
                                               (every? #(and (= 6 (count %)) (string? (last %))) (:result (first queries))))
                      :new-example-stored (string? (get-in @(:references store) [new-id :record :catalog/example]))
                      :stable-session (= identity (session/session-identity (host/current-session owner)))
                      :no-model-on-updates (= 5 request-count (count @requests))
                      :environment-only (every? #(= (:text %) (:content (first (:messages %)))) @requests)}
              result {:checks checks :initial initial :first-changed first-changed :second-changed second-changed
                      :examples examples :new-id new-id :provider-calls (if replay-dir 0 4)
                      :reuse-counts (into {} (map (fn [[id entry]] [id (count (:applications entry))]) applications))
                      :next-notes (read-value 'next-notes)}]
          (doseq [[request response] (map vector @requests @responses)]
            (catalog/archive-admission! store (keyword "archive" (:id request)) (:text response)
                                        (filterv #(= (:id request) (:response %)) @admissions)))
          (spit (str evidence "/applications.edn") (pr-str applications))
          (spit (str evidence "/result.edn") (pr-str result))
          (prn result)
          (assert (every? true? (vals checks)) (str "Cumulative acceptance failed: " checks)))
        (let [before (read-value 'answer) reply (read-value 'user-output)
              current (host/current-session owner)
              applications (application-evidence current store)]
          (doseq [[request response] (map vector @requests @responses)]
            (catalog/archive-admission! store (keyword "archive" (:id request)) (:text response)
                                        (filterv #(= (:id request) (:response %)) @admissions)))
          (spit (str evidence "/applications.edn") (pr-str applications))
          (assert (< (abs (- 518.4 before)) 0.00001) (str "Wrong initial result " before))
          (assert (string? reply))
          (publish {'quantity 140})
          (let [after (read-value 'answer)
                checks {:correct-before (< (abs (- 518.4 before)) 0.00001)
                        :correct-after (< (abs (- 583.2 after)) 0.00001)
                        :stable-session (= identity (session/session-identity (host/current-session owner)))
                        :expected-requests (= (if baseline? 2 3) (count @requests))
                        :provider-calls (if replay-dir 0 (- (count @requests) (if library-response 1 0)))
                        :replayed-from replay-dir
                        :baseline baseline? :thinking-enabled thinking?
                        :candidate-ids (set (mapcat (fn [entry] (map first (:result entry))) (filter #(= :query (:operation %)) @(:journal store))))
                        :queries (count (filter #(= :query (:operation %)) @(:journal store)))
                        :selections (mapv :id (filter #(= :resolve (:operation %)) @(:journal store)))
                        :reuse-counts (into {} (map (fn [[id entry]] [id (count (:applications entry))]) applications))
                        :before before :after after :reply reply :reply-after (read-value 'user-output) :selection-notes (read-value 'selection-notes)}]
            (spit (str evidence "/result.edn") (pr-str checks))
            (prn checks)
            (assert (:correct-after checks))
            (assert (not= (:reply checks) (:reply-after checks)))
            (assert (:stable-session checks))
            (assert (:expected-requests checks))
            (assert (= {:operator/linear-v1 2 :operator/percentage-v1 1 :operator/legacy-v1 0 :operator/max-v1 0}
                       (:reuse-counts checks)))
            (assert (if baseline? (zero? (:queries checks)) (pos? (:queries checks))))
            (when-not baseline?
              (assert (= #{:operator/linear-v1 :operator/percentage-v1 :operator/legacy-v1} (:candidate-ids checks))))
            (assert (= #{:operator/linear-v1 :operator/percentage-v1} (set (:selections checks))))
            (assert (every? (fn [request] (= (:text request) (:content (first (:messages request))))) @requests))))))
    (catch Throwable e
      (spit (str evidence "/failure.edn") (pr-str {:message (ex-message e) :data (ex-data e)}))
      (throw e))
    (finally
      (spit (str evidence "/evidence.edn") (pr-str {:requests @requests :responses @responses :admissions @admissions :catalog @(:journal store)}))
      (prn {:cleanup (host/close! owner)})
      (shutdown-agents))))

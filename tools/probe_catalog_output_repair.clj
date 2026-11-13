(ns tools.probe-catalog-output-repair)

(load-file (str (System/getenv "LAIN_HARNESS_ROOT") "/tools/offline_uuid.clj"))
(require '[clojure.edn :as edn]
         '[lain-harness.catalog :as catalog]
         '[lain-harness.source-restore :as restore]
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

(let [mode (first *command-line-args*)
      _ (assert (#{"accumulate" "reuse"} mode))
      accumulate? (= mode "accumulate")
      repair-mode (or (System/getenv "LAIN_REPAIR_MODE") "fresh")
      _ (assert (and (= mode "reuse") (#{"control" "clean" "exhausted" "replay" "fresh"} repair-mode)))
      provider-calls (atom 0)
      root (System/getenv "LAIN_HARNESS_ROOT")
      evidence (System/getenv "LAIN_EVIDENCE_DIR")
      store (catalog/open! (str evidence "/database"))
      _ (assert (empty? @(:references store)))
      _ (when accumulate?
          (catalog/seed! store [{:catalog/id :cap/delivered-event-cost}
                                {:catalog/id :event-cost/obsolete
                                 :catalog/description "Legacy fixed-price event quote"
                                 :catalog/interface "[quantity hours] -> amount"
                                 :catalog/assumptions "Fixed prices and fixed surcharge; no delivery"
                                 :catalog/status :superseded :catalog/example "(legacy 10 2) => 50"
                                 :catalog/capability [[:catalog/id :cap/event-cost]]}]))
      before (when-not accumulate? (edn/read-string {:default tagged-literal} (slurp (str evidence "/accumulate-result.edn"))))
      base (model/compiler)
      compiler (session/create-session (assoc (session/session-identity base)
                                              :native-extensions (:bootstrap (session/session-declarations base))
                                              :runtime-options (:options (session/session-declarations base))
                                              :propagation-options {:max-steps 300000 :timeout-ms 180000}))
      owner (host/open! compiler)
      journal (atom []) requests (atom []) responses (atom []) result (atom nil)
      provider (blazux/completion-transport {:timeout-ms 600000 :max-tokens 8192 :enable-thinking false})
      read-value #(connected/payload (session/binding-value (host/current-session owner) %))
      publish (fn [entries]
                (host/publish-input! owner (fn [current] (mapv (fn [[n v]] (session/publication-input (session/binding-cell current n) v)) entries)))
                (host/step! owner))
      ports '[restore-receipt-0 restore-receipt-1 restore-receipt-2]
      wait-for (fn [predicate]
                 (let [deadline (+ (System/currentTimeMillis) 900000)]
                   (loop [last-report 0]
                     (when-let [error (first (host/failures owner))] (throw error))
                     (doseq [n ports]
                       (when (= :rejected (:status (read-value n))) (throw (ex-info "Source admission failed" {:port n :receipt (read-value n)}))))
                     (let [now (System/currentTimeMillis)]
                       (when (> (- now last-report) 10000)
                         (spit (str evidence "/" mode "-state.edn")
                               (pr-str {:ports (into {} (map #(vector % (read-value %)) ports))
                                        :outcomes (mapv read-value '[discover-outcome choose-outcome execute-outcome]) :selected (read-value 'selected-id) :operators (keys @(:references store))})))
                       (cond (predicate) true
                             (> now deadline) (throw (ex-info "Directed experiment timed out" {}))
                             :else (do (Thread/sleep 50) (recur (if (> (- now last-report) 10000) now last-report))))))))]
  (try
    (when before
      (assert (:closed before))
      (assert (not (.isPresent (java.lang.ProcessHandle/of (:pid before))))))
    (add-watch journal ::receipts (fn [_ _ _ entries] (spit (str evidence "/" mode "-admissions.edn") (pr-str entries))))
    (host/extend! owner (soul/extension owner journal))
    (host/extend! owner (catalog/facilities store))
    (host/extend! owner (selection/facilities store))
    (host/extend! owner (observation/facilities))
    (host/extend! owner (model/declaration root "experiments/catalog-output-repair.lain"
                                           {:transport
                                            (fn [request]
                                              (let [id (:id request)]
                                                (when (#{"discover" "choose"} id)
                                                  (assert (empty? @(:references store)) "Discovery must precede restoration"))
                                                (println :request mode id) (flush)
                                                (swap! requests conj (dissoc request :cancelled?))
                                                (spit (str evidence "/" mode "-" id "-context.txt") (:text request))
                                                (let [reply (try
                                                              (if (or (= repair-mode "fresh")
                                                                      (and (= repair-mode "replay") (.contains id "-repair-")))
                                                                (do (swap! provider-calls inc) (provider request))
                                                                {:kind :reply :finish-reason "stop" :truncated? false :thinking ""
                                                                 :text (if (.contains id "-repair-")
                                                                         (if (= repair-mode "exhausted")
                                                                           (str "(define note-" id " \"No output connections supplied\")")
                                                                           "(-> answer parent-answer) (-> user-output parent-user-output)")
                                                                         (slurp (str root "/test/fixtures/output-repair/"
                                                                                     (if (= id "execute")
                                                                                       (if (= repair-mode "clean") "correct-execute" "shadowed-execute") id)
                                                                                     ".lain")))})
                                                              (catch Throwable error
                                                                (spit (str evidence "/" mode "-" id "-transport-error.edn")
                                                                      (pr-str {:message (ex-message error)
                                                                               :cause (some-> error ex-cause ex-message)
                                                                               :data (ex-data error)}))
                                                                (throw error)))]
                                                  (swap! responses conj (assoc reply :id id))
                                                  (spit (str evidence "/" mode "-" id "-response.edn") (pr-str reply))
                                                  (spit (str evidence "/" mode "-" id ".lain") (:text reply))
                                                  (println :response mode id (:finish-reason reply)) (flush)
                                                  reply)))}))
    (assert (empty? @requests))
    (assert (empty? @(:references store)))
    (publish (merge {'unit-price 6 'hourly-rate 30 'surcharge-rate 0.05 'accumulate-mode accumulate?}
                    (if accumulate? {'quantity 75 'hours 4 'delivery-charge 25}
                        {'quantity 100 'hours 6 'delivery-charge 40})))
    (publish {'start true})
    (wait-for #(or (some (fn [n] (= :failed (:status (read-value n)))) '[discover-outcome choose-outcome execute-outcome])
                   (= :done (:status (read-value 'execute-outcome)))))
    (if (= repair-mode "exhausted")
      (let [outcome (read-value 'execute-outcome)
            checks {:failed-terminal (= :failed (:status outcome))
                    :exact-budget (= ["discover" "choose" "execute" "execute-repair-1" "execute-repair-2"] (mapv :id @requests))
                    :three-observations (= 3 (count (:attempts outcome)))
                    :same-child (= 1 (count (set (map #(get-in % [:observation :child]) (:attempts outcome)))))
                    :all-parent-outputs-unknown (every? #(= {:first :unknown :second :unknown} (get-in % [:observation :outputs])) (:attempts outcome))
                    :no-provider-calls (zero? @provider-calls)}]
        (reset! result {:repair-mode repair-mode :checks checks :outcome outcome :provider-calls @provider-calls})
        (assert (every? true? (vals checks)) (str checks)))
      (do
        (assert (= :done (:status (read-value 'execute-outcome))) "Lain repair budget exhausted")
        (let [selected (read-value 'selected-id)
              bundle (read-value 'selected-bundle)
              initial (read-value 'answer) reply (read-value 'user-output)
              applied (applications (host/current-session owner) store)
              request-count-before (count @requests)
              admission-count-before (count @journal)
              registered (when accumulate? (:id (read-value 'cumulative-ready)))
              selected-source (apply str (map :source (:modules bundle)))
              restored-source (apply str (map :source (filter #(.startsWith (or (:response %) "") "restore/") @journal)))
              new-bundle (when accumulate?
                           (let [source (:text (first (filter #(= "execute" (:id %)) @responses)))
                                 receipts (vec (filter #(= "execute" (:response %)) @journal))
                                 archives (array-map :archive/build (restore/read-archive store :archive/build)
                                                     :archive/compose (restore/read-archive store :archive/compose)
                                                     :archive/accumulated {:source source :receipts receipts})
                                 saved (restore/build-bundle archives registered)]
                             (catalog/archive-admission! store :archive/accumulated source receipts)
                             saved))
              _ (publish {'delivery-charge (if accumulate? 40 60)})
              delivery-updated (read-value 'answer)
              _ (publish {'quantity (if accumulate? 100 120)})
              _ (publish {'start true})
              quantity-updated (read-value 'answer)
              count-app #(count (get-in applied [% :applications]))
              checks {:no-extra-inference (= request-count-before (count @requests))
                      :no-readmission-on-input-replay (= admission-count-before (count @journal))
                      :base-stage-order (= ["discover" "choose" "execute"] (vec (remove #(.contains % "-repair-") (map :id @requests))))
                      :repair-budget (<= (count @requests) 9)
                      :same-repair-child (every? (fn [n] (= 1 (count (set (map #(get-in % [:observation :child]) (:attempts (read-value n))))))) '[discover-outcome choose-outcome execute-outcome])
                      :expected-repairs (case repair-mode "fresh" true "clean" (= 3 (count @requests)) (= 4 (count @requests)))
                      :missing-output-detected (if (#{"control" "replay"} repair-mode)
                                                 (= {:first :unknown :second :unknown} (get-in (read-value 'execute-outcome) [:attempts 0 :observation :outputs])) true)
                      :repair-diagnostic-in-context (every? #(and (.contains (:text %) ":repair-required") (.contains (:text %) ":timestamp-utc") (.contains (:text %) "parent-answer")) (filter #(.contains (:id %) "-repair-") @requests))
                      :selection-before-restoration (every? #(empty? (:live-before %)) (filter #(= :select-source (:operation %)) @(:journal store)))
                      :correct-selection (= selected (if accumulate? :event-cost/v1 (:registered before)))
                      :obsolete-not-restored (not (contains? @(:references store) :event-cost/obsolete))
                      :query-includes-obsolete (boolean (some #(and (= :query (:operation %))
                                                                    (some (fn [row] (some #{:event-cost/obsolete} row)) (:result %))) @(:journal store)))
                      :exact-saved-source (= selected-source restored-source)
                      :only-required-operators (= (set (keys @(:references store))) (cond-> (set (:operators bundle)) accumulate? (conj registered)))
                      :initial-correct (< (abs (- initial (if accumulate? 623.5 859.0))) 0.00001)
                      :delivery-update-correct (< (abs (- delivery-updated (if accumulate? 638.5 879.0))) 0.00001)
                      :quantity-update-correct (< (abs (- quantity-updated (if accumulate? 796.0 1005.0))) 0.00001)
                      :reply-updated (not= reply (read-value 'user-output))
                      :whole-closure-used (= 1 (count-app (if accumulate? registered selected)))
                      :saved-dependencies-used (and (= 1 (count-app :event-cost/v1)) (= 2 (count-app :operator/linear-v1)) (= 1 (count-app :operator/percentage-v1)))
                      :fresh-session (or accumulate? (not= (:identity before) (session/session-identity (host/current-session owner))))
                      :unchanged-bundle-after-restart (or accumulate? (= (:new-bundle-hash before) (restore/sha256 (pr-str bundle))))
                      :environment-only (every? #(= (:text %) (:content (first (:messages %)))) @requests)}]
          (spit (str evidence "/" mode "-applications.edn") (pr-str applied))
          (spit (str evidence "/" mode "-bundle.edn") (pr-str bundle))
          (when new-bundle (spit (str evidence "/new-bundle.edn") (pr-str new-bundle)))
          (reset! result {:pid (.pid (java.lang.ProcessHandle/current)) :identity (session/session-identity (host/current-session owner))
                          :checks checks :selected selected :registered registered :initial initial :delivery-updated delivery-updated
                          :quantity-updated quantity-updated :selection-notes (read-value 'selection-notes)
                          :reply reply :reply-after (read-value 'user-output) :provider-calls @provider-calls :request-count (count @requests) :repair-mode repair-mode
                          :outcomes (mapv read-value '[discover-outcome choose-outcome execute-outcome])
                          :new-bundle-hash (when new-bundle (restore/sha256 (pr-str new-bundle)))})
          (assert (every? true? (vals checks)) (str checks)))))
    (catch Throwable error
      (spit (str evidence "/" mode "-failure.edn") (pr-str {:message (ex-message error) :data (ex-data error)}))
      (throw error))
    (finally
      (let [closed (= :closed (:status (host/close! owner)))]
        (when @result
          (swap! result assoc :closed closed)
          (spit (str evidence "/" mode "-result.edn") (pr-str @result))
          (prn (dissoc @result :outcomes :outcome)))
        (spit (str evidence "/" mode "-evidence.edn") (pr-str {:requests @requests :responses @responses :catalog @(:journal store) :admissions @journal :closed closed})))
      (shutdown-agents))))

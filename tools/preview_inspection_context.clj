(load-file (str (System/getenv "LAIN_HARNESS_ROOT") "/tools/offline_uuid.clj"))
(require '[clojure.edn :as edn]
         '[lain-harness.soul-session :as soul]
         '[lain-harness.premise-experiment :as premise]
         '[lain-harness.inspection-experiment :as inspection]
         '[lain-harness.model-io-session :as model]
         '[propagators.compiler-2.session :as session]
         '[propagators.compiler-2.session.host :as host]
         '[propagators.compiler-2.session.connected :as connected]
         '[propagators.cells.value :as value]
         '[propagators.network :as net])
(let [root (System/getenv "LAIN_HARNESS_ROOT")
      output (System/getenv "LAIN_EVIDENCE_DIR")
      owner (host/open! (model/compiler)) journal (atom []) requests (atom [])
      read-value #(connected/payload (session/binding-value (host/current-session owner) %))
      publish (fn [name v]
                (host/publish-input! owner (fn [current] [(session/publication-input (session/binding-cell current name) v)]))
                (host/step! owner))
      wait-admission (fn [id]
                       (let [deadline (+ (System/currentTimeMillis) 45000)]
                         (loop []
                           (when-let [e (first (host/failures owner))] (throw e))
                           (let [a (read-value 'admission-result)]
                             (cond
                               (and (= id (:response a)) (= :admitted (:status a))) a
                               (= :rejected (:status a)) (throw (ex-info "Rejected example" a))
                               (> (System/currentTimeMillis) deadline) (throw (ex-info "Timed out" {:admission a}))
                               :else (do (Thread/sleep 20) (recur)))))))
      sources ["(define demo-input)
(define demo-ready (+ 2 3))
(define demo-answer (+ demo-input demo-ready))
(define demo-view (inspect:cell (cell:reference demo-answer)))
(define demo-later)
(define demo-waiting (demo-later 4))
(define demo-directory (environment:overview current-environment (seq:empty)))
(define demo-output)
(-> demo-ready demo-output)
(-> (inspect:cell (cell:reference demo-output)) inspection-feedback)
(define demo-errors (inspect:errors (cell:reference demo-output)))"
               "(-> 7 demo-input)
(-> (network [x] (+ x 1)) demo-later)"]]
  (.mkdirs (java.io.File. output))
  (try
    (host/extend! owner (soul/extension owner journal))
    (host/extend! owner (premise/facilities))
    (host/extend! owner (inspection/facilities))
    (host/extend! owner (model/declaration root "soul-refined.lain"
                                           {:transport (fn [request]
                                                         (let [i (count (swap! requests conj (dissoc request :cancelled?)))]
                                                           (spit (str output "/context-" i ".txt") (:text request))
                                                           {:kind :reply :text (nth sources (dec i)) :thinking "" :finish-reason "stop" :truncated? false}))}))
    (doseq [[name v] {'boxes-1 20 'items-per-box-1 12 'order-quantity-1 200
                      'question-1 "shipment 1 question" 'shared-rule "generated rules; not independently validated"
                      'delivery-note "Tuesday"}]
      (publish name v))
    (publish 'ask-event {:id "user-1" :round 1 :time 1 :content "A shipment contains 20 boxes of 12 items. We need to fulfill an order for 200 items. The damaged percentage has not been reported yet. What can we determine now, and what remains unresolved about the usable stock and whether we can fulfill the order?"})
    (publish 'trigger-1 true)
    (wait-admission "round-1")
    (let [child (:id (read-value 'generated-environment))
          child-value #(connected/payload (net/network-cell-strongest (session/network (host/current-session owner)) (session/scoped-binding-cell (host/current-session owner) child %)))]
      (assert (= 5 (child-value 'demo-ready) (child-value 'demo-output)))
      (assert (value/nothing? (child-value 'demo-answer)))
      (assert (value/nothing? (child-value 'demo-waiting)))
      (assert (= 5 (:value (read-value 'inspection-feedback))))
      (assert (= [] (child-value 'demo-errors)))
      (assert (some #(and (= "demo-later" (:title %)) (= :unknown (:state %)))
                    (edn/read-string {:default tagged-literal} (child-value 'demo-directory))))
      (publish 'ask-event {:id "user-2" :round 2 :time 3 :content "Controlled example completion; no provider request."})
      (publish 'trigger-2 true)
      (wait-admission "round-2")
      (assert (= 12 (child-value 'demo-answer)))
      (assert (= 5 (child-value 'demo-waiting)))
      (assert (some #(and (= "demo-later" (:title %)) (= :known (:state %)))
                    (edn/read-string {:default tagged-literal} (child-value 'demo-directory))))
      (spit (str output "/verification.edn")
            (pr-str {:partial-completion 12 :late-callable 5 :feedback-value 5
                     :directory-updated true :empty-error-inspection true :provider-calls 0
                     :controlled-responses 2 :admissions @journal})))
    (finally (host/close! owner) (shutdown-agents))))

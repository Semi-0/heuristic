(load-file (str (System/getenv "LAIN_HARNESS_ROOT") "/tools/offline_uuid.clj"))
(require '[clojure.test :as test]
         '[lain-harness.model-io-session :as model]
         '[lain-harness.soul-test :as soul-test]
         '[lain-harness.soul-session :as soul]
         '[propagators.compiler-2.session :as session])
(let [original model/compiler
      original-open soul/open!
      original-await soul-test/await!
      opened (atom nil)
      counters (ref test/*initial-report-counters*)]
  ;; Test-local allowance for the existing large documentation environment.
  ;; Production/compiler defaults are unchanged.
  (with-redefs [soul/open! (fn [root options] (let [transport (:transport options) result (original-open root (assoc options :transport (fn [q] (prn :request (:id q)) (flush) (transport q))))] (reset! opened result) result))
                soul-test/await! (fn [owner predicate] (try (original-await owner predicate) (catch Throwable e (doseq [n (quote [construction-trigger construction-request raw-model-result model-result observe-cell])] (prn n (try (soul-test/read-value owner n) (catch Throwable _ :absent)))) (prn :journal @(:journal @opened)) (throw e))))
                model/compiler
                (fn []
                  (let [base (original)]
                    (session/create-session
                     (assoc (session/session-identity base)
                            :native-extensions (:bootstrap (session/session-declarations base))
                            :runtime-options (:options (session/session-declarations base))
                            :propagation-options {:max-steps 300000 :timeout-ms 180000}))))]
    (binding [test/*report-counters* counters]
      (test/test-vars [#'soul-test/staged-context-retains-model-child-code-and-real-memory-feedback
                       #'soul-test/existing-user-ingress-and-acknowledged-delivery-extension])))
  (prn @counters)
  (shutdown-agents)
  (System/exit (+ (:fail @counters) (:error @counters))))

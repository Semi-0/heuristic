(load-file (str (System/getenv "LAIN_HARNESS_ROOT") "/tools/offline_uuid.clj"))
(require '[clojure.test :as test]
         '[lain-harness.model-io-session :as model]
         '[lain-harness.soul-test :as soul-test]
         '[propagators.compiler-2.session :as session])
(let [original model/compiler
      counters (ref test/*initial-report-counters*)]
  ;; Test-local allowance for the existing large documentation environment.
  ;; Production/compiler defaults are unchanged.
  (with-redefs [model/compiler
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

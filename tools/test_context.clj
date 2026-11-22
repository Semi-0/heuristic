(load-file (str (System/getenv "LAIN_HARNESS_ROOT") "/tools/offline_uuid.clj"))
(require 'lain-harness.context-store-test 'lain-harness.context-policy-test)
(let [r (if (seq *command-line-args*)
          (binding [clojure.test/*report-counters* (ref clojure.test/*initial-report-counters*)]
            (clojure.test/test-vars
             (mapv #(or (requiring-resolve (symbol %)) (throw (ex-info "Unknown test" {:name %})))
                   *command-line-args*))
            @clojure.test/*report-counters*)
          (clojure.test/run-tests 'lain-harness.context-store-test 'lain-harness.context-policy-test))]
  (prn :test-summary r)
  (shutdown-agents)
  (System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))

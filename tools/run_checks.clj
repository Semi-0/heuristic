(load-file (str (System/getenv "LAIN_HARNESS_ROOT") "/tools/offline_uuid.clj"))
(require '[clojure.test :as test]
         'propagators.compiler-2.stream-extension-test
         'propagators.compiler-2.immutable-session-test
         'lain-harness.streaming-test
         'lain-harness.immutable-test
         'lain-harness.connected-test
         'lain-harness.interface-test)
(let [result (test/run-tests 'propagators.compiler-2.stream-extension-test 'propagators.compiler-2.immutable-session-test 'lain-harness.streaming-test
                             'lain-harness.immutable-test
                             'lain-harness.connected-test
                             'lain-harness.interface-test)]
  (shutdown-agents)
  (System/exit (if (zero? (+ (:fail result) (:error result))) 0 1)))

(load-file (str (System/getenv "LAIN_HARNESS_ROOT") "/tools/offline_uuid.clj"))
(require '[clojure.test :as t])
(doseq [suite '[propagators.compiler-2.stream-extension-test
                lain-harness.immutable-test
                lain-harness.connected-test
                lain-harness.interface-test
                lain-harness.soul-test
                lain-harness.model-behavior-test
                lain-harness.model-test
                lain-harness.model-io-test
                lain-harness.streaming-test]]
  (try
    (require suite)
    (prn {:suite suite :result (t/run-tests suite)})
    (catch Throwable error (prn {:suite suite :load-error (ex-message error)})))
  (flush))
(shutdown-agents)

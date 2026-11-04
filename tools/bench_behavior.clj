(load-file (str (System/getenv "LAIN_HARNESS_ROOT") "/tools/offline_uuid.clj"))
(require '[lain-harness.behavior-benchmark :as benchmark])
(apply benchmark/run-case! *command-line-args*)
(shutdown-agents)

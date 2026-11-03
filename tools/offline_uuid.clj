(require '[clojure.java.io :as io] '[clojure.string :as str])
(when (= "1" (System/getenv "LAIN_OFFLINE_UUID"))
  (let [original (slurp (io/resource "clj_uuid/node.clj"))
        patched (str/replace original "(let [addresses (all-local-addresses)" "(let [addresses [\"offline-harness-check\"]")]
    (assert (not= original patched) "UUID initialization changed; review offline adjustment")
    (load-string patched)
    (dosync (alter @#'clojure.core/*loaded-libs* conj 'clj-uuid.node))
    (println "Offline UUID initialization enabled; compiler/runtime behavior is unchanged.")))

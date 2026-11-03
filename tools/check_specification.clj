(load-file (str (System/getenv "LAIN_HARNESS_ROOT") "/tools/offline_uuid.clj"))
(require '[lain-harness.runtime :as harness]
         '[propagators.compiler-2.session :as session]
         '[propagators.compiler-2.session.host :as host]
         '[propagators.experimental.visualization.data :as data]
         '[propagators.cells.value :as value])
(let [root (System/getenv "LAIN_HARNESS_ROOT")
      owner (host/open! (session/create-session))
      services (harness/services owner)]
  (try
    (host/extend! owner (harness/facilities-extension services))
    (host/extend! owner (harness/specification-extension services (str root "/soul-refined.lain")))
    (host/extend-source! owner
      "(define check-entry ((slot :entry-point) specification))
       (define check-request ((slot :text) ((slot :request-example) specification)))")
    (let [s (host/current-session owner) observed #(data/payload (session/binding-value s %))]
      (assert (= "soul-refined.lain" (observed 'check-entry)))
      (assert (= "Will I need an umbrella tomorrow in Amsterdam?" (observed 'check-request)))
      (doseq [name '[describe-environment incremental-compile inputs-of actions-of empty-env
                    lexical-child extend-env env:slot specification runtime experiment]]
        (assert (not (value/nothing? (observed name))) (str name " is missing")))
      (doseq [name '[weather-at umbrella-advice message-to-user model-session user-input]]
        (assert (value/nothing? (observed name)) (str name " was unexpectedly supplied")))
      (assert (empty? (host/receipts owner)) "Loading source must not start effects")
      (assert (empty? (host/failures owner))))
    (println "PASS: actual source and native import compiled; interfaces inspectable; optional instances remain pending; no inference or effects launched.")
    (finally (host/close! owner))))
(shutdown-agents)

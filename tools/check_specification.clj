(require '[clojure.string :as str]
         '[propagators.compiler-2.session :as session]
         '[propagators.compiler-2.session.files :as files]
         '[propagators.experimental.visualization.data :as data]
         '[propagators.cells.value :as value])
(let [root (first *command-line-args*)
      source (slurp (str root "/soul-refined.lain"))
      probes "(define check-entry ((slot :entry-point) specification))
              (define check-status ((slot :status) specification))
              (define check-pending ((slot :description) ((slot :interfaces) specification)))
              (define check-request ((slot :text) ((slot :request-example) specification)))
              (define check-acceptance (car ((slot :acceptance) specification)))
              (define check-obligation ((slot :name) (car ((slot :obligations) specification))))
              (define check-call (describe-environment harness-context))"
      extension (files/prepare-extension!
                  {:id :soul-specification-check
                   :files {"soul-refined.lain" source "verification.lain" probes}})
      compiled (session/extend-session (session/create-session) extension)
      turn (session/advance compiled [])
      settled (:session turn)
      observed #(data/payload (session/binding-value settled %))
      missing '[empty-env lexical-child extend-env env:slot describe-environment
                incremental-compile inputs-of actions-of model-session user-input
                message-to-user weather-at umbrella-advice check-pending check-call]]
  (assert (empty? (:requests turn)) "Specification unexpectedly requested effects")
  (doseq [[sym expected] {'check-entry "soul-refined.lain"
                         'check-status "awaiting review; implementations unresolved"
                         'check-request "Will I need an umbrella tomorrow in Amsterdam?"
                         'check-obligation "lexical-environment"}]
    (assert (= expected (observed sym)) (str sym " was " (observed sym))))
  (assert (str/starts-with? (observed 'check-acceptance) "Live updates:"))
  (doseq [sym missing]
    (assert (value/nothing? (observed sym)) (str sym " unexpectedly resolved")))
  (doseq [sym '[specification experiment-interfaces implementation-obligations
                acceptance-scenarios runtime experiment language-guide-index harness-context]]
    (assert (not (value/nothing? (observed sym))) (str sym " unexpectedly pending")))
  (assert (not (re-find #"soul-live|live_v4|h4:|bootstrap-v4|scaffold-v4|\(define umbrella-for" source)))
  (println "PASS: actual source compiled; specification fields inspectable; 13 missing endpoints and a pending call remain unresolved; no archived scaffold dependency.")
  (println "No host session opened, adapter installed, or inference dispatched."))
(shutdown-agents)

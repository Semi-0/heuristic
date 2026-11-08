(load-file (str (System/getenv "LAIN_HARNESS_ROOT") "/tools/offline_uuid.clj"))
(require '[clojure.string :as str]
         '[lain-harness.model-io-session :as model]
         '[lain-harness.soul-session :as soul]
         '[lain-harness.premise-experiment :as premise]
         '[lain-harness.connected-data :as cd]
         '[lain-harness.experiment-observation :as observation]
         '[propagators.compiler-2.session :as session]
         '[propagators.compiler-2.session.connected :as connected]
         '[propagators.compiler-2.session.host :as host]
         '[propagators.experimental.ttms-primitives :as primitives]
         '[propagators.propagator :as prop])
(def root (System/getenv "LAIN_HARNESS_ROOT"))
(def directory "runtime/unchanged-kernel-audit-20261108")
;; Reconstruct the original negative control even after the working fixture
;; gains the completion barrier. Every case is saved beside its observed result.
(def fixture
  (str/replace (slurp (str root "/test/fixtures/environment-context-probe.lain"))
               "(network [envref context-input]\n  (let [context (cell:first context-input)\n        e"
               "(network [envref context]\n  (let [e"))
(def expected-context {"phase" "generalization" "analysis-record" "Analysis text"
                       "generalization-record" "No interface yet."
                       "inspection-feedback" {:status :not-started} "ask" "Test question"})
(def reference-fixture
  (reduce (fn [source [old new]] (str/replace source old new)) fixture
          [["(env:slot \"phase\" pv)" "(env:reference-slot \"phase\" (cell:reference pv))"]
           ["(env:slot \"analysis-record\" av)" "(env:reference-slot \"analysis-record\" (cell:reference av))"]
           ["(env:slot \"generalization-record\" (data:get :generalization context))" "(env:reference-slot \"generalization-record\" (cell:reference (data:get :generalization context)))"]
           ["(env:slot \"inspection-feedback\" (data:get :feedback context))" "(env:reference-slot \"inspection-feedback\" (cell:reference (data:get :feedback context)))"]
           ["(env:slot \"ask\" (data:get :ask context))" "(env:reference-slot \"ask\" (cell:reference (data:get :ask context)))"]]))

(doseq [[label source publish? expected]
        [[:slot-delayed fixture true expected-context]
         [:reference-delayed reference-fixture true expected-context]
         [:slot-immediate (str/replace fixture "(define raw-input-text)" "(define raw-input-text \"Analysis text\")") false expected-context]
         [:reference-immediate (str/replace reference-fixture "(define raw-input-text)" "(define raw-input-text \"Analysis text\")") false expected-context]
         [:native-projection-delayed (str/replace reference-fixture "data:get" "audit:get") true expected-context]
         [:frozen-context-delayed (str/replace reference-fixture
                                               "(network [envref context]\n  (let [e"
                                               "(network [envref context-input]\n  (let [context (cell:first context-input) e") true expected-context]
         [:record-delayed "(load-lain \"soul-refined.lain\") (define raw-input-text) (define record (data:record :phase \"generalization\" :input (cell:first raw-input-text))) (define observed (data:get :phase record))" true "generalization"]
         [:struct-select "(load-lain \"soul-refined.lain\") (define f (network [o] (select ((slot :c) o) 10 20))) (define observed (f (struct :c false)))" false 20]
         [:premise-struct-select "(load-lain \"soul-refined.lain\") (define f (ttms:ttms-premise-closure (network [o] (ttms:select ((slot :c) o) 10 20)) 1)) (define observed (f (struct :c false)))" false 20]]]
  (let [path (str directory "/" (name label) ".lain")
        owner (host/open! (model/compiler))]
    (spit (str root "/" path) source)
    (try
      (host/extend! owner (soul/extension owner (atom [])))
      (host/extend! owner (observation/facilities))
      (host/extend! owner (premise/facilities))
      (host/extend! owner (session/session-extension
                           {:id :audit-projection
                            :primitives {'audit:get (primitives/scalar-operator
                                                     'audit:get (prop/primitive-propagator 'audit:get cd/field-value))}}))
      (host/extend! owner (model/declaration root path {}))
      (when publish?
        (host/publish-input! owner (fn [current] [(session/publication-input (session/binding-cell current 'raw-input-text) "Analysis text")]))
        (host/step! owner))
      (let [value (connected/payload (session/binding-value (host/current-session owner) 'observed))
            actual (if (map? value) (:values value) value)]
        (prn {:case label :actual actual
              :expected expected :pass (= expected actual)
              :host-failures (mapv ex-message (host/failures owner))}))
      (catch Throwable error (prn {:case label :error (ex-message error)}))
      (finally (host/close! owner))))
  (flush))
(shutdown-agents)

(ns tools.audit-unchanged-kernel)

;; Reference suites plus high-level session probes; never patches runtime vars.
(load-file (str (System/getenv "LAIN_HARNESS_ROOT") "/tools/offline_uuid.clj"))
(require '[clojure.test :as t]
         '[lain-harness.session-publication :as publication]
         '[propagators.compiler-2.session :as session]
         '[propagators.experimental.ttms-application :as application]
         '[propagators.experimental.ttms-primitives :as primitives]
         '[propagators.experimental.amb.observation :as observation])

(def suites
  '[propagators.ttms-primitives-test
    propagators.ttms-branching-test
    propagators.ttms-publication-test
    propagators.premise-transport-test
    propagators.experimental.ttms-application-test
    propagators.ttms-compound-boundary-test
    propagators.compiler-2.immutable-session-test
    propagators.compiler-2.runtime.session-extension-test])

(doseq [suite (if (= "--probes-only" (first *command-line-args*)) [] suites)]
  (require suite)
  (prn {:suite suite :result (t/run-tests suite)})
  (flush))

;; Compare plain branches, lexical closures, and premise-gated closures using
;; source declarations and producer extensions, without native fixture seeding.
(doseq [source
        ["(let [] (define f (network [o] (select (p:slot :c o) 10 20))) (let-cell [o] (p:slot :c false o) (f o)))"
         "(let [] (define f (ttms-premise-closure (network [o] (select (p:slot :c o) 10 20)) definition-one)) (let-cell [o] (p:slot :c false o) (f o)))"
         "(let [] (define f (ttms-premise-closure (network [o] (define c (p:slot :c o)) (select c 10 20)) definition-one)) (let-cell [o] (p:slot :c false o) (f o)))"
         "(select false 10 20)"
         "(if false 10 20)"
         "(let [] (define f (network [c] (select c 10 20))) (f false))"
         "(let [] (define f (network [c] (if c 10 20))) (f false))"
         "(let [] (define f (ttms-premise-closure (network [c] (select c 10 20)) definition-one)) (f false))"
         "(let [] (define f (ttms-premise-closure (network [c] (if c 10 20)) definition-one)) (f false))"
         "(let [] (define f (network [x] (select (>= x 250) 0 12))) (f 185))"
         "(let [] (define f (ttms-premise-closure (network [x] (select (>= x 250) 0 12)) definition-one)) (f 185))"]]
  (try
    (let [compiled (session/extend-session
                    (session/create-session {:id (random-uuid) :run-id (random-uuid)
                                             :native-extensions [primitives/session-extension application/session-extension]})
                    (session/session-extension
                     {:id :probe :files {"probe.lain" (str "(define definition-one) (define result " source ")")}}))
          active (publication/publish compiled 'definition-one 1 :active 'definition-one)
          actual (observation/base (session/binding-value active 'result))
          expected (if (.contains source "250") 12 20)]
      (prn {:source source :status :completed
            :expected expected :actual actual :pass (= expected actual)}))
    (catch Throwable error
      (prn {:source source :error (ex-message error)})))
  (flush))
(shutdown-agents)

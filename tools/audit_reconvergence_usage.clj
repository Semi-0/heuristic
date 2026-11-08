(ns tools.audit-reconvergence-usage)

(load-file (str (System/getenv "LAIN_HARNESS_ROOT") "/tools/offline_uuid.clj"))
(require '[lain-harness.session-publication :as publication]
         '[propagators.cells.value :as value]
         '[propagators.compiler-2.session :as session]
         '[propagators.experimental.amb.observation :as observation]
         '[propagators.experimental.ttms-primitives :as primitives])

(doseq [[label source initial-value changed-value]
        [[:native-lifecycle "(if (<= subtotal 200) (+ subtotal 1) (* subtotal 2))" 186 514]
         [:direct-branch "(select (>= subtotal 250) 0 fee)" 12 0]
         [:unchanged-dependencies "(+ subtotal (* subtotal 2))" 555 771]
         [:select-reconvergence "(* (+ subtotal (select (>= subtotal 250) 0 fee)) 0.08M)" 15.76M 20.56M]
         [:if-reconvergence "(* (+ subtotal (if (>= subtotal 250) 0 fee)) 0.08M)" 15.76M 20.56M]
         [:whole-branch "(if (>= subtotal 250) (* subtotal 0.08M) (* (+ subtotal fee) 0.08M))" 15.76M 20.56M]
         [:split-tax "(+ (* subtotal 0.08M) (select (>= subtotal 250) 0 (* fee 0.08M)))" 15.76M 20.56M]]]
  (try
    (let [compiled (session/extend-session
                    (session/create-session {:id (random-uuid) :run-id (random-uuid)
                                             :native-extensions [primitives/session-extension]})
                    (session/session-extension
                     {:id label :files {"main.lain" (str "(define subtotal) (define fee) (define result " source ")")}}))
          settled (-> compiled
                      (publication/publish 'subtotal 1 :active 185)
                      (publication/publish 'fee 1 :active 12))
          changed (publication/publish settled 'subtotal 2 :active 257)
          withdrawn (publication/publish changed 'subtotal 3 :retracted value/nothing)
          restored (publication/publish withdrawn 'subtotal 4 :active 185)]
      (prn {:case label :source source
            :stages (mapv (fn [[stage compiler expected]]
                            (let [actual (observation/base (session/binding-value compiler 'result))]
                              {:stage stage :expected expected :actual actual :pass (= expected actual)}))
                          [[:initial settled initial-value] [:changed changed changed-value]
                           [:withdrawn withdrawn value/nothing] [:restored restored initial-value]])}))
    (catch Throwable error (prn {:case label :error (ex-message error)})))
  (flush))
(shutdown-agents)

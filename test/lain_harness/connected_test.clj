(ns lain-harness.connected-test
  (:require [clojure.test :refer [deftest is]]
            [lain-harness.connected-data :as data]
            [lain-harness.premises :as premises]
            [propagators.cells.value :as value]
            [propagators.compiler-2.compiler.basis :as basis]
            [propagators.compiler-2.model.env :as env]
            [propagators.compiler-2.session :as session]
            [propagators.compiler-2.session.connected :as connected]
            [propagators.compiler-2.session.operators :as ops]
            [propagators.network :as net]))

(defn observed [s id]
  (connected/freeze (session/network s) (net/network-cell-strongest (session/network s) id)))
(defn primitive [f] (ops/prim-op (fn [p] (p f))))

(deftest selected-lain-feedback-stops-without-a-host
  (let [id (basis/stable-node-id :counter)
        initial (session/extend-session (session/create-session)
                                        (session/session-extension
                                         {:id :counter
                                          :cells {id (premises/content id (connected/frozen {:version 0}) 1 :active)}
                                          :primitives {'state (env/cell-binding id)
                                                       'feedback (connected/connector :feedback)
                                                       'ref (data/reference-operator)
                                                       'field (primitive data/field-value)
                                                       'record (primitive data/record-value)}
                                          :files {"counter.lain"
                                                  "(define next (if (< (field :version state) 3)
                                (record :version (+ 1 (field :version state))) state))
                              (define receipt (feedback (ref state) (field :version state) next))"}}))
        step (fn [s]
               (let [turn (session/advance s [])]
                 (:session (session/advance (:session turn) (:next-inputs turn)))))
        states (take 7 (iterate step initial))
        final (last states)]
    (is (= [0 1 2 3 3 3 3] (mapv #(get (observed % id) :version) states)))
    (is (= {:version 0} (observed initial id)))
    (is (empty? (:next-inputs (session/advance final []))))
    (is (empty? (session/dependency-declarations final)))))

(deftest immutable-compare-and-advance-receipts
  (let [id (basis/stable-node-id :versioned-state)
        receipt (basis/stable-node-id :receipt)
        initial (session/extend-session (session/create-session)
                                        (session/session-extension
                                         {:id :state
                                          :cells {id (premises/content id (connected/frozen {:version 0 :value 1}) 1 :active)
                                                  receipt value/nothing}}))
        commit (fn [s token expected next]
                 (:session (session/advance s [{:runtime/input :connected :kind :feedback
                                                :receipt receipt :token token
                                                :values [id expected next]}])))
        next {:version 1 :value 2}
        advanced (commit initial :one 0 next)
        replayed (commit advanced :replay 0 next)
        stale (commit replayed :stale 0 {:version 1 :value 3})
        invalid (commit stale :invalid 1 {:version 3 :value 4})]
    (is (= :accepted (:status (observed advanced receipt))))
    (is (= :replayed (:status (observed replayed receipt))))
    (is (= :stale-version (:status (observed stale receipt))))
    (is (= :invalid-version (:status (observed invalid receipt))))
    (is (= next (observed invalid id)))
    (is (= {:version 0 :value 1} (observed initial id)))))

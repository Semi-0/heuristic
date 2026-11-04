(ns lain-harness.connected-test
  (:require [clojure.test :refer [deftest is]]
            [lain-harness.connected-data :as data]
            [propagators.compiler-2.session :as session]
            [propagators.compiler-2.session.connected :as connected]
            [propagators.network :as net]))

(defn observed [s id]
  (connected/freeze (session/network s) (net/network-cell-strongest (session/network s) id)))
(defn primitive [f] (connected/primitive :test/primitive f))

(deftest selected-lain-feedback-stops-without-a-host
  (let [declared (session/extend-session
                  (session/create-session)
                  (session/session-extension
                   {:id :counter
                    :primitives {'feedback (connected/connector :feedback)
                                 'initialize (connected/connector :initialize)
                                 'ref (data/reference-operator)
                                 'field (primitive data/field-value)
                                 'record (primitive data/record-value)}
                    :files {"counter.lain"
                            "(define state)
                             (initialize (ref state) (record :version 0))
                             (define next (if (< (field :version state) 3)
                               (record :version (+ 1 (field :version state))) state))
                             (define receipt (feedback (ref state) (field :version state) next))"}}))
        id (session/binding-cell declared 'state)
        initial (:session (session/advance declared (session/connected-inputs declared)))
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
  (let [declared (session/extend-session
                  (session/create-session)
                  (session/session-extension
                   {:id :state
                    :primitives {'initialize (connected/connector :initialize)
                                 'ref (data/reference-operator)
                                 'record (primitive data/record-value)}
                    :files {"state.lain" "(define state) (define receipt)
                                         (initialize (ref state) (record :version 0 :value 1))"}}))
        id (session/binding-cell declared 'state)
        receipt (session/binding-cell declared 'receipt)
        initial (:session (session/advance declared (session/connected-inputs declared)))
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

(deftest lain-declares-independent-ttms-sources
  (let [declared (session/extend-session
                  (session/create-session)
                  (session/session-extension
                   {:id :lain-owned-sources
                    :primitives {'session:initialize (connected/connector :initialize)
                                 'cell:reference (data/reference-operator)
                                 'record (primitive data/record-value)}
                    :files {"lib/ports.lain" (slurp (str (System/getenv "LAIN_HARNESS_ROOT") "/lib/ports.lain"))
                            "sources.lain" "(load-lain \"lib/ports.lain\")
                                            (define a (source:new (record :version 0 :name :a)))
                                            (define b (source:new (record :version 0 :name :b)))"}}))
        a (observed declared (session/binding-cell declared 'a))
        b (observed declared (session/binding-cell declared 'b))
        initialized (:session (session/advance declared (session/connected-inputs declared)))]
    (is (not= a b))
    (is (= connected/pending (observed declared a)))
    (is (= {:version 0 :name :a} (observed initialized a)))
    (is (= {:version 0 :name :b} (observed initialized b)))
    (doseq [cell [a b]]
      (let [observations (:support/observations (net/network-cell-content (session/network initialized) cell))]
        (is (= 1 (count observations)))
        (is (= #{{:source cell :timestamp 1 :premises-status :active}} (:support (first observations))))))))

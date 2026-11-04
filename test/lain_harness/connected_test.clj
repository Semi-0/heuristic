(ns lain-harness.connected-test
  (:require [clojure.test :refer [deftest is]]
            [lain-harness.connected-data :as data]
            [lain-harness.premises :as premises]
            [propagators.datastructures.layered-value :as datum]
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
                                 'initialize (data/initialize-operator)
                                 'ref (data/reference-operator)
                                 'field (primitive data/field-value)
                                 'record (primitive data/record-value)}
                    :files {"counter.lain"
                            "(define state)
                             (initialize state (record :version 0))
                             (define next (if (< (field :version state) 3)
                               (record :version (+ 1 (field :version state))) state))
                             (define receipt (feedback (ref state) (field :version state) next))"}}))
        id (session/binding-cell declared 'state)
        initial declared
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
                    :primitives {'initialize (data/initialize-operator)
                                 'ref (data/reference-operator)
                                 'record (primitive data/record-value)}
                    :files {"state.lain" "(define state) (define receipt)
                                         (initialize state (record :version 0 :value 1))"}}))
        id (session/binding-cell declared 'state)
        receipt (session/binding-cell declared 'receipt)
        initial declared
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
                    :primitives {'source:initialize (data/initialize-operator)
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
    (is (= {:version 0 :name :a} (observed declared a)))
    (is (empty? (session/connected-inputs declared)))
    (is (= {:version 0 :name :a} (observed initialized a)))
    (is (= {:version 0 :name :b} (observed initialized b)))
    (doseq [cell [a b]]
      (let [observations (:support/observations (net/network-cell-content (session/network initialized) cell))]
        (is (= 1 (count observations)))
        (is (= #{{:source cell :timestamp 1 :premises-status :active}} (:support (first observations))))))))

(deftest source-initialization-is-a-wired-primitive
  (let [declared (session/extend-session
                  (session/create-session)
                  (session/session-extension
                   {:id :wired-initialization
                    :primitives {'initialize (data/initialize-operator)}
                    :files {"initialize.lain" "(define seed) (define target) (initialize target seed)"}}))
        seed (session/binding-cell declared 'seed)
        target (session/binding-cell declared 'target)
        first-value {:version 0 :payload :first}
        initialized (:session (session/advance declared [(session/publication-input seed first-value)]))
        repeated (:session (session/advance initialized [(session/publication-input seed {:version 1 :payload :later})]))]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"expects a target and initial value"
                          (session/extend-session declared
                                                  (session/session-extension {:id :bad-initialize
                                                                              :files {"bad-initialize.lain" "(initialize target)"}}))))
    (is (= connected/pending (observed declared target)))
    (is (= first-value (observed initialized target)))
    (is (= first-value (observed repeated target)))
    (is (empty? (session/connected-inputs initialized)))
    (is (empty? (session/dependency-declarations initialized)))))

(deftest boolean-propagators-preserve-deciding-premises
  (let [initial (session/extend-session
                 (session/create-session)
                 (session/session-extension
                  {:id :boolean-propagators
                   :primitives {'initialize (data/initialize-operator)
                                'and (data/and-operator) 'or (data/or-operator)}
                   :files {"logic.lain" "(define left) (define right)
                                         (initialize left false)
                                         (define conjunction (and left right))
                                         (define disjunction (or left right))"}}))
        left (session/binding-cell initial 'left)
        right (session/binding-cell initial 'right)
        conjunction (session/binding-cell initial 'conjunction)
        disjunction (session/binding-cell initial 'disjunction)
        both (:session (session/advance initial [(session/publication-input right true)]))
        withdrawn (:session (session/advance both [(session/cell-input left (premises/content left false 2 :retracted))]))
        recovered (:session (session/advance withdrawn [(session/cell-input left (premises/content left true 3 :active))]))
        other-witness (:session (session/advance recovered [(session/cell-input left (premises/content left true 4 :retracted))]))
        no-witness (:session (session/advance other-witness [(session/cell-input right (premises/content right true 2 :retracted))]))
        again (:session (session/advance no-witness [(session/cell-input right (premises/content right true 3 :active))]))
        supports (fn [s cell] (set (map :source (datum/support-of (net/network-cell-strongest (session/network s) cell)))))]
    (is (false? (observed initial conjunction)))
    (is (= connected/pending (observed initial disjunction)))
    (is (= #{left} (supports initial conjunction)))
    (is (true? (observed both disjunction)))
    (is (= #{right} (supports both disjunction)))
    (is (= connected/pending (observed withdrawn conjunction)))
    (is (true? (observed withdrawn disjunction)))
    (is (true? (observed recovered conjunction)))
    (is (= #{left right} (supports recovered conjunction)))
    (is (true? (observed other-witness disjunction)))
    (is (= #{right} (supports other-witness disjunction)))
    (is (= connected/pending (observed no-witness disjunction)))
    (is (true? (observed again disjunction)))
    (is (= #{right} (supports again disjunction)))))

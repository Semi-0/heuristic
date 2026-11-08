(ns lain-harness.session-api-test
  (:require [clojure.test :refer [deftest is testing]]
            [lain-harness.session-publication :as publication]
            [propagators.cells.value :as value]
            [propagators.compiler-2.session :as session]
            [propagators.datastructures.layered-value :as datum]
            [propagators.experimental.amb.observation :as observation]
            [propagators.experimental.ttms-application :as application]
            [propagators.experimental.ttms-primitives :as primitives]))

(defn compile-program [source]
  (session/extend-session
   (session/create-session {:id (random-uuid) :run-id (random-uuid)
                            :native-extensions [primitives/session-extension application/session-extension]})
   (session/session-extension {:id :program :files {"main.lain" source}})))

(defn observed [compiler name]
  (observation/base (session/binding-value compiler name)))

(deftest compiled-list-consumers-follow-definition-premises
  (doseq [nested? [false true]]
    (testing (if nested? "Nested list through Lain accessors" "Flat list through Lain accessors")
      (let [endpoint (compile-program "(define definition-one)")
            with-premise (publication/publish endpoint 'definition-one 1 :active 'definition-one)
            program (str "(define f (ttms-premise-closure (network [x] "
                         (if nested? "(list (list x (+ x 1)))" "(list x (+ x 1))")
                         ") definition-one))\n(define result (f 5))\n"
                         "(define accessed " (if nested? "(car result)" "result") ")\n"
                         "(define left (car accessed))\n(define right (car (cdr accessed)))")
            active (session/extend-session with-premise
                                           (session/session-extension {:id :consumers :files {"consumers.lain" program}}))
            source (session/binding-cell active 'definition-one)
            output (session/binding-cell active 'left)
            withdrawn (publication/publish active 'definition-one 2 :retracted 'definition-one)
            restored (publication/publish withdrawn 'definition-one 3 :active 'definition-one)]
        (is (value/nothing? (observed endpoint 'definition-one)))
        (doseq [name '[left right]]
          (is (value/nothing? (observed withdrawn name))))
        (doseq [[compiler epoch] [[active 1] [restored 3]]
                [name expected] [['left 5] ['right 6]]]
          (is (= expected (observed compiler name)))
          (is (= #{{:source source :timestamp epoch :premises-status :active}}
                 (datum/support-of (session/binding-value compiler name)))))
        (is (= output (session/binding-cell restored 'left)))
        (is (empty? (:cells (session/session-declarations restored))))))))

(deftest producer-extensions-replay-without-changing-the-session
  (let [initial (compile-program "(define input) (define result (+ input 1))")
        admitted (publication/publish initial 'input 1 :active 10)]
    (is (= 11 (observed admitted 'result)))
    (is (identical? admitted (publication/publish admitted 'input 1 :active 10)))
    (is (thrown? Exception (publication/publish admitted 'input 1 :active 99)))
    (is (= 11 (observed admitted 'result)))
    (is (empty? (:cells (session/session-declarations admitted))))))

(deftest compiled-list-consumers-follow-a-new-return-shape
  (let [initial (compile-program
                 "(define definition-one)
                  (define f (ttms-premise-closure
                    (if (= definition-one 1) (network [x] (list x x)) (network [x] (list x)))
                    definition-one))
                  (define result (f 5))
                  (define left (car result))
                  (define right (car (cdr result)))")
        active (publication/publish initial 'definition-one 1 :active 'definition-one)
        changed (publication/publish active 'definition-one 2 :active 1)
        source (session/binding-cell changed 'definition-one)]
    (is (= 5 (observed active 'left)))
    (is (value/nothing? (observed active 'right)))
    (is (= 5 (observed changed 'left)))
    (is (= 5 (observed changed 'right)))
    (is (= #{{:source source :timestamp 2 :premises-status :active}}
           (datum/support-of (session/binding-value changed 'right))))))

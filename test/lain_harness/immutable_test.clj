(ns lain-harness.immutable-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [lain-harness.queue :as queue]
            [lain-harness.stream :as stream]
            [lain-harness.source-reader :as source-reader]
            [lain-harness.admission :as admission]
            [lain-harness.connected-data :as connected-data]
            [lain-harness.runtime :as harness]
            [lain-harness.premises :as premises]
            [propagators.compiler-2.session :as session]
            [propagators.compiler-2.session.connected :as connected]
            [propagators.compiler-2.session.host :as host]
            [propagators.compiler-2.compiler.basis :as basis]
            [propagators.experimental.visualization.data :as data]
            [propagators.network :as net]))

(def root (System/getenv "LAIN_HARNESS_ROOT"))

(deftest immutable-fifo-and-version-feedback
  (let [empty (queue/queue 2)
        a {:id :a :payload 1}
        one (queue/append empty a)
        two (queue/append one {:id :b :payload 2})
        ack (queue/acknowledge two [:a])]
    (is (empty? (queue/history empty)))
    (is (= one (queue/append one a)))
    (is (= [:a :b] (mapv :id (queue/pending two))))
    (is (= [:b] (mapv :id (queue/pending ack))))
    (is (= two (queue/feedback one 1 two)))
    (is (= two (queue/feedback two 1 two)))
    (is (thrown? Exception (queue/feedback empty 1 two)))
    (is (thrown? Exception (queue/append one (assoc a :payload 3))))
    (is (thrown? Exception (queue/append two {:id :c})))
    (is (thrown? Exception (queue/acknowledge two [:b])))
    (is (thrown? Exception (queue/append (queue/close ack) {:id :c}))))
  (let [full (reduce queue/append (queue/queue) (map #(hash-map :id %) (range 256)))]
    (is (= 256 (count (queue/pending full))))
    (is (thrown? Exception (queue/append full {:id 256})))))

(deftest immutable-reader-preserves-prefix
  (let [initial (stream/framer)
        fragment (stream/receive initial "(define a")
        first-form (stream/advance-form (stream/receive fragment " 1)(define b ])") false)
        suffix (stream/advance-form (:state first-form) true)]
    (is (= "" (:buffer initial)))
    (is (= :waiting (:status (stream/advance-form fragment false))))
    (is (= '(define a 1) (:form first-form)))
    (is (= :invalid (:status suffix)))
    (is (= "Invalid Lain form" (:message suffix)))
    (is (= first-form (stream/advance-form (stream/receive fragment " 1)(define b ])") false)))))

(defn fixture []
  (let [owner (host/open! (session/create-session))
        services (harness/services owner)]
    (harness/install-facilities! services)
    (host/extend! owner (harness/specification-extension services (str root "/soul-refined.lain")))
    {:owner owner :services services}))

(deftest actual-source-lain-policies
  (let [{:keys [owner]} (fixture)]
    (try
      (host/extend-source! owner
                           "(define q0 (queue:empty 1))
         (define event-a (data:record :id :a :kind :chunk :payload 7))
         (define q1 (data:get :state (queue:offer q0 event-a)))
         (define duplicate-status (data:get :status (queue:offer q1 event-a)))
         (define overflow-status (data:get :status (queue:offer q1 (data:record :id :b :payload 8))))
         (define conflict-status (data:get :status (queue:offer q1 (data:record :id :a :payload 8))))
         (define ack-status (data:get :status (queue:acknowledge q1 :a)))
         (define wrong-ack-status (data:get :status (queue:acknowledge q1 :b)))
         (define r1 (round:start initial-round-state 1))
         (define active-eligible (round:eligible r1 1))
         (define failed (round:finish r1 true))
         (define failed-eligible (round:eligible failed 1))
         (define resumed-eligible (round:eligible (round:resume failed) 1))
         (define budget-limit (round:eligible (data:record :number 8 :budget 8 :busy false :paused false) 1))
         (define kind-conflict (data:get :status (queue:offer q1 (data:record :id :a :kind :complete :payload 7))))
         (define zero-capacity (data:get :status (queue:offer (queue:empty 0) event-a)))
         (define fractional-capacity (data:get :status (queue:offer (queue:empty 1.5) event-a)))
         (define replay (data:get :status (queue:feedback q1 0 q1)))
         (define stale-replay (data:get :status (queue:feedback q1 99 q1)))
         (define same-turn (data:get :number (round:for-user (data:record :turn :a :number 8) :a)))
         (define next-turn (data:get :number (round:for-user (data:record :turn :a :number 8) :b)))
         (define default-limit pending-event-limit)
         (define default-budget inference-round-budget)")
      (let [s (host/current-session owner)
            observed #(data/payload (session/binding-value s %))]
        (doseq [[name expected] {'duplicate-status :duplicate 'overflow-status :overflow
                                 'conflict-status :conflict 'ack-status :acknowledged
                                 'wrong-ack-status :invalid-acknowledgement
                                 'active-eligible false 'failed-eligible false
                                 'resumed-eligible true 'budget-limit false
                                 'default-limit 256 'default-budget 8
                                 'kind-conflict :conflict 'zero-capacity :invalid-capacity 'fractional-capacity :invalid-capacity
                                 'replay :replayed 'stale-replay :stale-version 'same-turn 8 'next-turn 0}]
          (is (= expected (observed name)) (str name))))
      (finally (host/close! owner)))))

(deftest independent-pure-extension-installation
  (doseq [extensions [[(source-reader/extension) (admission/extension)]
                      [(admission/extension) (source-reader/extension)]]]
    (let [initial (session/create-session)
          first-session (session/extend-session initial (first extensions))
          ready (reduce session/extend-session first-session (rest extensions))]
      (is (empty? (session/dependency-declarations ready)))
      (is (= 1 (count (:primitives (session/extension-parts (first extensions))))))
      (is (= 2 (count (:primitives (session/session-declarations ready)))))
      (is (= 0 (count (:primitives (session/session-declarations initial))))))))

(deftest admission-through-immutable-session
  (let [initial (session/create-session)
        root-env (session/environment initial)
        child (basis/stable-node-id :immutable-child)
        initial (session/extend-session initial
                                        (session/session-extension
                                         {:id :frames :frames {child {:parent root-env :bindings []}}
                                          :primitives {'source:initialize (connected-data/initialize-operator)
                                                       'cell:reference (connected-data/reference-operator)}
                                          :files {"lib/ports.lain" (slurp (str root "/lib/ports.lain"))
                                                  "receipts.lain" "(load-lain \"lib/ports.lain\")
                                                    (define premise-reference (source:new :test))
                                                    (define receipt-first) (define receipt-second) (define receipt-third)"}}))
        premise (data/payload (session/binding-value initial 'premise-reference))
        declaration (fn [id text]
                      {:id id :target child :support-cell premise :files {(str id ".lain") text}})
        receipt #(session/binding-cell initial (symbol (str "receipt-" (name %))))
        input #(session/admission-input % (receipt (:id %)) :response)
        good (declaration :first "(define kept 7)")
        broken (declaration :second "(define kept 8)")
        skipped (declaration :third "(define forbidden 9)")
        result (:session (session/advance initial (mapv input [good broken skipped])))
        observed #(data/payload (net/network-cell-strongest (session/network result) (receipt %)))
        cell (session/scoped-binding-cell result child 'kept)
        replay (:session (session/advance result [(input good)]))]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"receipt must be declared"
                          (session/advance initial [(session/admission-input good
                                                                             (basis/stable-node-id :undeclared-receipt) :response)])))
    (is (= :admitted (:status (observed :first))))
    (is (= :rejected (:status (observed :second))))
    (is (= :skipped (:status (observed :third))))
    (is (= 7 (data/payload (net/network-cell-strongest (session/network result) cell))))
    (is (thrown? Exception (session/binding-cell result 'kept)))
    (is (= (session/source-files result) (session/source-files replay))))
  (testing "The actual source creates a lexical declaration without a compiler worker"
    (let [{:keys [owner]} (fixture)]
      (try
        (let [initial (host/current-session owner)
              source (str "(define admission-child (lexical-child (empty-env)))\n"
                          "(define admission-plan (incremental-compile-form :generated \"(define admitted-result 42)\" admission-child nil))\n"
                          "(define declaration (data:get :declaration admission-plan))\n(define admission-receipt)")
              prepared (session/extend-session initial
                                               (session/session-extension {:id :plan :files {"plan.lain" source}}))
              declaration (connected/payload (data/payload (session/binding-value prepared 'declaration)))
              receipt (session/binding-cell prepared 'admission-receipt)
              admitted (:session (session/advance prepared [(session/admission-input declaration receipt :actual)]))
              target (:target declaration)]
          (is (= 42 (data/payload (net/network-cell-strongest (session/network admitted)
                                                              (session/scoped-binding-cell admitted target 'admitted-result)))))
          (is (thrown? Exception (session/binding-cell admitted 'admitted-result)))
          (is (= :admitted (:status (data/payload (net/network-cell-strongest (session/network admitted) receipt))))))
        (finally (host/close! owner))))))

(deftest pure-core-architecture
  (doseq [file ["queue.clj" "stream.clj" "source_reader.clj" "admission.clj" "environment.clj" "lexical_observation.clj" "connected_data.clj" "value_metadata.clj"]]
    (let [source (slurp (str root "/src/lain_harness/" file))]
      (doseq [forbidden ["(atom " "(swap!" "(reset!" "promise" "future" "Thread."
                         "session.host" "slurp" "spit" "java.net"]]
        (is (not (str/includes? source forbidden)) (str file " contains " forbidden)))))
  (doseq [file ["propagators/compiler_2/session.clj" "propagators/compiler_2/session/connected.clj"]]
    (let [source (slurp file)]
      (doseq [forbidden ["(atom " "(swap!" "(reset!" "promise" "future" "Thread."
                         "session.host" "slurp" "spit" "java.net"]]
        (is (not (str/includes? source forbidden)) (str file " contains " forbidden)))))
  (let [source (slurp (str root "/src/lain_harness/runtime.clj"))]
    (is (not (str/includes? source "lain-code-admission")))
    (is (not (str/includes? source "(promise")))))

(deftest connected-lain-observation-contribution
  (let [{:keys [owner]} (fixture)]
    (try
      (let [before (host/current-session owner)
            prepared (session/extend-session before
                                             (session/session-extension
                                              {:id :observation-demo
                                               :files {"observation-demo.lain"
                                                       "(load-lain \"lib/observation-example.lain\")
                                  (define observed-child (lexical-child (empty-env)))
                                  (define notes (observe-review-notes observed-child))
                                  (define facts (lexical:bindings observed-child))\n(define observation-receipt)\n(define observation-support (source:new :observation))"}}))
            e (data/payload (session/binding-value prepared 'observed-child))
            support (data/payload (session/binding-value prepared 'observation-support))
            declaration {:id :observed-addition :target (:id e) :frames (:frames e)
                         :support-cell support
                         :files {"observed-addition.lain" "(define answer 42)"}}
            receipt (session/binding-cell prepared 'observation-receipt)
            admitted (:session (session/advance prepared [(session/admission-input declaration receipt :observation)]))
            checked (session/extend-session admitted
                                            (session/session-extension
                                             {:id :observation-check
                                              :files {"observation-check.lain"
                                                      "(define note (data:get :review (data:get :car notes)))
                                 (define fact-value (data:get :value (data:get :car facts)))
                                 (define fact-available (data:get :available (data:get :car facts)))"}}))]
        (is (= "Review binding answer" (data/payload (session/binding-value checked 'note))))
        (is (= 42 (data/payload (session/binding-value checked 'fact-value))))
        (let [withdrawn (:session (session/advance checked
                                                   [(session/cell-input support (premises/content support :observation 2 :retracted))]))
              recovered (:session (session/advance withdrawn
                                                   [(session/cell-input support (premises/content support :observation 3 :active))]))]
          (is (false? (data/payload (session/binding-value withdrawn 'fact-available))))
          (is (true? (data/payload (session/binding-value recovered 'fact-available))))
          (is (= (session/source-files checked) (session/source-files recovered))))
        (is (empty? (host/receipts owner))))
      (finally (host/close! owner)))))

(deftest facilities-have-independent-identities
  (let [owner (host/open! (session/create-session))
        services (harness/services owner)
        extensions (harness/facility-extensions services)]
    (try
      (is (= (count extensions) (count (set (map session/extension-id extensions)))))
      (doseq [extension extensions]
        (is (= 1 (count (:primitives (session/extension-parts extension))))))
      (doseq [extension (reverse extensions)] (host/extend! owner extension))
      (host/extend! owner (harness/specification-extension services (str root "/soul-refined.lain")))
      (is (empty? (host/receipts owner)))
      (is (empty? (host/failures owner)))
      (finally (host/close! owner)))))

(deftest extensions-declare-operators-and-lain-not-preseeded-cells
  (let [owner (host/open! (session/create-session))
        services (harness/services owner)]
    (try
      (doseq [extension (concat (harness/facility-extensions services)
                                [(harness/transport-extension services (fn [& _]))
                                 (harness/message-extension services)])]
        (let [parts (session/extension-parts extension)]
          (is (empty? (:cells parts)))
          (is (empty? (:frames parts)))
          (is (empty? (:derivations parts)))
          (is (empty? (:interfaces parts)))))
      (doseq [file ["runtime" "connected_data" "environment" "lexical_observation" "value_metadata" "admission" "source_reader"]]
        (let [source (slurp (str root "/src/lain_harness/" file ".clj"))]
          (is (not (re-find #"operator/operator-closure\s|:compiler-activate|:application-installer|connected/frozen" source)))))
      (finally (host/close! owner)))))

(deftest lain-model-instances-own-their-sources
  (let [{:keys [owner services]} (fixture)
        requests (atom [])
        transport (fn [request _emit!] (swap! requests conj request))]
    (try
      (host/extend! owner (harness/transport-extension services transport))
      (host/extend-source! owner
                           "(define first-user (integration:user \"user-one\"))
         (define second-user (integration:user \"user-two\"))
         (define capabilities (struct :request transport:stream))
         (define model-session (integration:model \"one\" first-user capabilities))
         (define second-model (integration:model \"two\" second-user capabilities))")
      (host/step! owner)
      (let [current (host/current-session owner)
            one (harness/model-state services "one")
            two (harness/model-state services "two")]
        (is (= "one" (:model-id one)))
        (is (= "two" (:model-id two)))
        (is (= :idle (:active one) (:active two)))
        (is (= 256 (:capacity (:inputs one)) (:capacity (:outputs two))))
        (is (= 8 (:budget (:round one)) (:budget (:round two))))
        (doseq [part [:state :events :delivered :commands]]
          (is (not= (harness/model-cell current "one" part)
                    (harness/model-cell current "two" part))))
        (is (empty? @requests))
        (is (empty? (host/receipts owner)))
        (is (empty? (host/failures owner))))
      (harness/resume-model! services "one" "resume-1")
      (host/step! owner)
      (host/step! owner)
      (let [resumed (harness/model-state services "one")]
        (is (= ["resume-1"] (mapv :id (harness/list-values (:commands resumed)))))
        (is (= connected/empty-list (:commands (harness/model-state services "two"))))
        (harness/resume-model! services "one" "resume-1")
        (host/step! owner)
        (is (= resumed (harness/model-state services "one"))))
      (finally (host/close! owner)))))

(deftest boolean-and-command-policies
  (let [{:keys [owner]} (fixture)]
    (try
      (host/extend-source! owner
                           "         (define logic-and (and true true))
         (define logic-and-false (and true false))
         (define logic-or (or false true))
         (define logic-or-false (or false false))
         (define logic-empty-and (and))
         (define logic-empty-or (or))
         (define logic-not (not false))
         (define logic-pending)
         (define logic-decided (and false logic-pending))
         (define cond-selected (cond [true 7 else logic-pending]))
         (define string-capacity (data:get :status (queue:offer (queue:empty \"bad\") (data:record :id :a :payload 7))))
         (define paused-model (data:with (model:initial 256 8)
                               (data:record :round (data:record :turn :user-one :number 8 :budget 8 :busy false :paused true))))
         (define resumed-model (model:command paused-model (data:record :id :resume-one :kind :resume)))
         (define resume-paused (data:get :paused (data:get :round resumed-model)))
         (define resume-round (data:get :number (data:get :round resumed-model)))
         (define resume-turn (data:get :turn (data:get :round resumed-model)))
         (define resume-eligible (round:eligible (data:get :round resumed-model) 1))
         (define resume-inputs (data:equal? (data:get :inputs paused-model) (data:get :inputs resumed-model)))
         (define resume-outputs (data:equal? (data:get :outputs paused-model) (data:get :outputs resumed-model)))
 ")
      (let [s (host/current-session owner)]
        (doseq [[name expected] {'logic-and true 'logic-and-false false 'logic-or true 'logic-or-false false
                                 'logic-empty-and true 'logic-empty-or false 'logic-not true
                                 'logic-decided false 'cond-selected 7 'string-capacity :invalid-capacity
                                 'resume-paused false 'resume-round 8 'resume-turn :user-one 'resume-eligible false
                                 'resume-inputs true 'resume-outputs true}]
          (is (= expected (data/payload (session/binding-value s name))) (str name))))
      (finally (host/close! owner)))))

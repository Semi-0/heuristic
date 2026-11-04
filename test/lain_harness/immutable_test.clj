(ns lain-harness.immutable-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [lain-harness.queue :as queue]
            [lain-harness.stream :as stream]
            [lain-harness.source-reader :as source-reader]
            [lain-harness.admission :as admission]
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
        premise (basis/stable-node-id :immutable-premise)
        initial (session/extend-session initial
                                        (session/session-extension
                                         {:id :frames :frames {child {:parent root-env :bindings []}}
                                          :cells {premise (premises/content premise :test 1 :active)}}))
        declaration (fn [id text]
                      {:id id :target child :support-cell premise :files {(str id ".lain") text}})
        receipt #(basis/stable-node-id :receipt %)
        input #(session/admission-input % (receipt (:id %)) :response)
        good (declaration :first "(define kept 7)")
        broken (declaration :second "(define kept 8)")
        skipped (declaration :third "(define forbidden 9)")
        result (:session (session/advance initial (mapv input [good broken skipped])))
        observed #(data/payload (net/network-cell-strongest (session/network result) (receipt %)))
        cell (session/scoped-binding-cell result child 'kept)
        replay (:session (session/advance result [(input good)]))]
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
                          "(define admission-plan (incremental-compile-form :generated \"(define result 42)\" admission-child nil))\n"
                          "(define declaration (data:get :declaration admission-plan))")
              prepared (session/extend-session initial
                                               (session/session-extension {:id :plan :files {"plan.lain" source}}))
              declaration (connected/payload (data/payload (session/binding-value prepared 'declaration)))
              receipt (basis/stable-node-id :actual-source-receipt)
              admitted (:session (session/advance prepared [(session/admission-input declaration receipt :actual)]))
              target (:target declaration)]
          (is (= 42 (data/payload (net/network-cell-strongest (session/network admitted)
                                                              (session/scoped-binding-cell admitted target 'result)))))
          (is (thrown? Exception (session/binding-cell admitted 'result)))
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
                                  (define facts (lexical:bindings observed-child))"}}))
            e (data/payload (session/binding-value prepared 'observed-child))
            support (basis/stable-node-id :observed-support)
            declaration {:id :observed-addition :target (:id e) :frames (:frames e)
                         :support-cell support
                         :cells {support (premises/content support :observation 1 :active)}
                         :files {"observed-addition.lain" "(define answer 42)"}}
            receipt (basis/stable-node-id :observation-admission)
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
                                [(harness/model-extension services {:id "one" :transport (fn [& _])})
                                 (harness/model-extension services {:id "two" :name 'second-model :transport (fn [& _])})
                                 (harness/user-extension "user")
                                 (harness/message-extension services "one")])]
        (let [parts (session/extension-parts extension)]
          (is (empty? (:cells parts)))
          (is (empty? (:frames parts)))
          (is (empty? (:derivations parts)))))
      (doseq [file ["runtime" "connected_data" "environment" "lexical_observation" "value_metadata" "admission" "source_reader"]]
        (let [source (slurp (str root "/src/lain_harness/" file ".clj"))]
          (is (not (re-find #"operator/operator-closure\s|:compiler-activate|:application-installer|connected/frozen" source)))))
      (finally (host/close! owner)))))

(deftest lain-model-instances-own-their-sources
  (let [{:keys [owner services]} (fixture)
        requests (atom [])
        transport (fn [request _emit!] (swap! requests conj request))]
    (try
      (host/extend! owner (harness/model-extension services {:id "one" :transport transport}))
      (host/extend! owner (harness/model-extension services {:id "two" :name 'second-model :transport transport}))
      (host/step! owner)
      (let [current (host/current-session owner)
            one (harness/model-state services "one")
            two (harness/model-state services "two")]
        (is (= "one" (:model-id one)))
        (is (= "two" (:model-id two)))
        (is (= :idle (:active one) (:active two)))
        (doseq [part [:state :events :delivered]]
          (is (not= (harness/model-cell current "one" part)
                    (harness/model-cell current "two" part))))
        (is (empty? @requests))
        (is (empty? (host/receipts owner)))
        (is (empty? (host/failures owner))))
      (finally (host/close! owner)))))

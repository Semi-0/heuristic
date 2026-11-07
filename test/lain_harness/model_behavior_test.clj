(ns lain-harness.model-behavior-test
  (:require [clojure.test :refer [deftest is]]
            [lain-harness.behavior :as behavior]
            [lain-harness.behavior-test :as b]
            [lain-harness.runtime :as h]
            [propagators.compiler-2.session :as session]
            [propagators.compiler-2.session.connected :as connected]
            [propagators.compiler-2.session.files :as files]
            [propagators.compiler-2.session.host :as host]
            [propagators.datastructures.support-collection :as collection]
            [propagators.experimental.temporal-behavior :as temporal]))

(defn open-fixture [fixture]
  (let [owner (host/open! (session/create-session))]
    (try
      (h/install-facilities! (h/services owner))
      (session/extend-session
       (host/current-session owner)
       (files/prepare-extension! {:id [:isolated fixture] :root b/root
                                  :read-files [(str "test/fixtures/" fixture ".lain")]}))
      (finally (host/close! owner)))))

(def reducer-session (delay (open-fixture "model-reducer")))
(def connections-session (delay (open-fixture "model-connections")))

(defn snapshot [s binding revision xs]
  (b/publish s binding
             [(collection/content {:base (h/linked-list xs)
                                   :support #{(b/premise binding revision :active)}})]))
(defn current [s] (connected/payload (session/binding-value s 'test-current)))
(defn event [id time kind payload]
  {:id id :time time :value {:kind kind :payload payload}})
(defn settle [s]
  (loop [s s steps 0]
    (let [pending (vec (session/connected-inputs s))]
      (when (> steps 20) (throw (ex-info "Connection did not settle" {:steps steps})))
      (if (empty? pending) s
          (recur (:session (session/advance s pending)) (inc steps))))))
(defn journal [s]
  (h/read-cell s (connected/payload (session/binding-value s 'test-journal))))

(deftest reducer-is-a-compiled-independent-state-machine
  (let [initial (snapshot @reducer-session 'test-events 0 [])
        context (event :context 1 :context "controlled")
        user (event :user 2 :user {:id "u1" :kind :user :payload "hello"})
        started (snapshot initial 'test-events 1 [context user])
        state (current started)
        completed (-> state
                      (assoc :version (inc (:version state)))
                      (assoc-in [:parser :status] :done))
        acknowledgement (event :compiler 3 :compiler {:expected (:version state) :next completed})
        settled (snapshot started 'test-events 2 [context user acknowledgement])
        counters (atom {})
        replayed (binding [temporal/*instrument* counters]
                   (snapshot settled 'test-events 2 [context user acknowledgement]))
        withdrawn (snapshot settled 'test-events 3 [context])
        recovered (snapshot withdrawn 'test-events 4 [context user acknowledgement])]
    (is (= :idle (:active (current initial))))
    (is (= "controlled" (:context state)))
    (is (= 1 (:serial state)))
    (is (= "u1" (get-in state [:round :turn])))
    (is (= 1 (get-in state [:active :id])))
    (is (= :idle (:active (current settled))))
    (is (zero? (get-in (current settled) [:inputs :count])))
    (is (= (current settled) (current replayed) (current recovered)))
    (is (= 0 (get @counters :reducer-invocations 0)))
    (is (= 0 (:serial (current withdrawn))))
    (is (= (b/topology initial) (b/topology recovered)))
    (is (= 3 (count (:records (behavior/observed settled 'test-states)))))))

(deftest event-and-context-connections-work-with-a-recording-journal
  (let [initial @connections-session
        event {:id "u1" :kind :user :payload "hello"}
        admitted (settle (snapshot initial 'test-input 0 [event]))
        replayed (settle (snapshot admitted 'test-input 1 [event]))
        contextual (settle (b/publish replayed 'test-context
                                      [(collection/content {:base "context" :support #{}})]))
        entries (h/list-values (:events (journal contextual)))]
    (is (= 1 (:version (journal admitted))))
    (is (= (journal admitted) (journal replayed)))
    (is (= [1 2] (mapv :time entries)))
    (is (= [:user :context] (mapv #(get-in % [:value :kind]) entries)))
    (is (= event (get-in (first entries) [:value :payload])))))

(deftest compiler-transition-and-request-connections-are-independent
  (let [publish (fn [s binding revision v]
                  (b/publish s binding
                             [(collection/content {:base v :support #{(b/premise binding revision :active)}})]))
        state {:version 0 :active :idle}
        next {:version 1 :active {:id 1 :messages :lain.data/empty}}
        same (-> @connections-session
                 (publish 'test-previous 0 state)
                 (publish 'test-next 0 state)
                 settle)
        proposal (-> same (publish 'test-next 1 next) settle)
        request (publish proposal 'test-request-state 0 next)
        idle (publish request 'test-request-state 1 state)
        entries (h/list-values (:events (journal proposal)))]
    (is (= 0 (:version (journal same))))
    (is (= 1 (count entries)))
    (is (= {:kind :compiler :payload {:expected 0 :next next}} (:value (first entries))))
    (is (= (:active next) (connected/payload (session/binding-value request 'test-recorded-request))))
    (is (= :idle (connected/payload (session/binding-value idle 'test-recorded-request))))))

(deftest reducer-failure-resume-and-stale-proposals
  (let [events [(event :context 1 :context "controlled")
                (event :user 2 :user {:id "u1" :kind :user :payload "hello"})
                (event :failure 3 :source {:id "failure" :kind :failure :payload "offline"})]
        failed (snapshot @reducer-session 'test-events 0 events)
        resumed (snapshot failed 'test-events 1
                          (conj events (event :resume 4 :command {:id "resume" :kind :resume})))
        stale (snapshot failed 'test-events 1
                        (conj events (event :stale 4 :compiler {:expected 999 :next (current resumed)})))]
    (is (= :idle (:active (current failed))))
    (is (true? (get-in (current failed) [:round :paused])))
    (is (= "offline" (:diagnostic (current failed))))
    (is (= 2 (:serial (current resumed))))
    (is (= 2 (get-in (current resumed) [:round :number])))
    (is (= (current failed) (current stale)))))

(deftest empty-source-then-batched-events-are-admitted-once
  (let [initial (settle (snapshot @connections-session 'test-source 0 []))
        chunk {:id [1 1] :kind :chunk :payload "hello"}
        complete {:id [1 2] :kind :complete :payload ""}
        admitted (settle (snapshot initial 'test-source 1 [chunk complete]))
        entries (h/list-values (:events (journal admitted)))]
    (is (= [chunk complete] (mapv #(get-in % [:value :payload]) entries)))
    (is (= [1 2] (mapv :time entries)))))

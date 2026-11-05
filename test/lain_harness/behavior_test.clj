(ns lain-harness.behavior-test
  (:require [clojure.test :refer [deftest is testing]]
            [lain-harness.behavior :as behavior]
            [propagators.cells.merge :as merge]
            [propagators.cells.value :as value]
            [propagators.compiler-2.compiler.basis :as basis]
            [propagators.compiler-2.session :as session]
            [propagators.compiler-2.session.connected :as connected]
            [propagators.datastructures.reducer-cell :as reducer]
            [propagators.datastructures.support-collection :as collection]
            [propagators.experimental.temporal-behavior :as temporal]
            [propagators.network :as net]))

(def root (System/getenv "LAIN_HARNESS_ROOT"))
(defn open [fixture]
  (session/extend-session (behavior/create-session)
                          (behavior/extension root (str "test/fixtures/behavior/" fixture ".lain"))))
(def minimal (delay (open "minimal")))
(def composed (delay (open "scan")))
(def windowed (delay (open "window")))

(defn premise [id revision status]
  {:source (basis/stable-node-id :behavior-test id) :timestamp revision :premises-status status})
(defn supported
  ([event] (supported event #{(premise (:id event) 0 :active)}))
  ([event support] (collection/content {:base event :support support :premise-state support})))
(defn point [id time payload] (supported (temporal/occurrence id time payload)))
(defn belief [id revision status]
  (collection/content {:premise-state #{(premise id revision status)}}))
(defn publish
  ([s updates] (publish s 'input-cell updates))
  ([s binding updates]
   (:session (session/advance s (mapv #(session/cell-input (session/binding-cell s binding) %) updates)))))
(defn records [s binding] (:records (behavior/observed s binding)))
(defn values [s binding] (mapv :value (records s binding)))
(defn topology [s] (set (keys (net/net-env (session/network s)))))
(defn content [s binding] (net/network-cell-content (session/network s) (session/binding-cell s binding)))
(defn slots [s binding] (reducer/reducer-slots (content s binding)))

(defn reference-scan
  "Independent scalar reference: resolve revisions and beliefs, order by semantic
  time and identity, then recompute every prefix. No runtime algebra or cache."
  [updates]
  (let [observations (into #{} (mapcat :support/observations) updates)
        states (->> updates (mapcat :support/states) (group-by :source)
                    (map (fn [[source xs]] [source (apply max-key :timestamp xs)])) (into {}))
        current (->> observations
                     (group-by #(get-in % [:base :id]))
                     vals
                     (map #(apply max-key (fn [o] (get-in o [:base :revision])) %))
                     (filter #(every? (fn [p] (= :active (:premises-status (get states (:source p) p)))) (:support %)))
                     (sort-by #(vector (get-in % [:base :time]) (pr-str (get-in % [:base :id])))))
        payloads (map #(get-in % [:base :value]) current)]
    (if (seq payloads) (vec (reductions + payloads)) [])))

(def points (mapv #(point % % %) [1 2 3]))

(deftest compiled-scan-and-compositions
  (let [initial @composed
        settled (publish initial points)]
    (is (= [] (values initial 'sums)))
    (is (= [] (values initial 'initialized)))
    (doseq [[binding expected] {'sums [1 3 6] 'initialized [11 13 16] 'digits [1 12 123]
                                'doubled [2 6 12] 'sampled [12] 'between [3]
                                'filtered [2 3] 'filtered-sums [2 5] 'combined [3 9 18]
                                'archived [1 3 6] 'reversed [6 3 1] 'latest [6]
                                'mapped-history [101 103 106]}]
      (is (= expected (values settled binding)) (str binding)))
    (is (= [] (values settled 'independent)))
    (is (= [1 3 6] (reference-scan points)))
    (is (= (topology initial) (topology settled)))
    (is (= [] (values initial 'sums)))
    (is (= [1 2 3] (mapv :time (records settled 'initialized))))))

(deftest withdrawal-and-premise-only-exact-recovery
  (let [initial @minimal
        settled (publish initial points)
        withdrawal (belief 2 1 :retracted)
        recovery (belief 2 2 :active)
        withdrawn (publish settled [withdrawal])
        recovered (publish withdrawn [recovery])]
    (is (= [1 4] (values withdrawn 'sums)))
    (is (= [1 3 6] (values recovered 'sums)))
    (is (= (reference-scan (conj points withdrawal)) (values withdrawn 'sums)))
    (is (= (reference-scan (conj points withdrawal recovery)) (values recovered 'sums)))
    (is (= (first (records settled 'sums)) (first (records recovered 'sums))))
    (is (= #{(premise 1 0 :active)} (:support (first (records recovered 'sums)))))
    (is (contains? (:support (last (records recovered 'sums))) (premise 2 2 :active)))
    (is (contains? (get-in (last (records withdrawn 'sums)) [:selection :states])
                   (premise 2 1 :retracted)))
    (is (= [1 3 6] (values settled 'sums)))
    (is (= (topology initial) (topology recovered)))))

(defn permutations [xs]
  (if (empty? xs) [[]]
      (mapcat (fn [x] (map #(cons x %) (permutations (remove #{x} xs)))) xs)))

(deftest permutations-batching-regrouping-and-duplicates
  (let [initial @minimal
        expected (behavior/observed (publish initial points) 'sums)]
    (doseq [order (permutations points)]
      (let [incremental (reduce #(publish %1 [%2]) initial order)
            batched (publish initial order)
            grouped (publish initial [(reduce temporal/join-evidence temporal/empty-evidence order)])
            duplicates (publish batched (concat order order))]
        (doseq [s [incremental batched grouped duplicates]]
          (is (= expected (behavior/observed s 'sums))))))
    (testing "withdrawal arriving before its retained observation is equivalent"
      (let [withdrawal (belief 2 1 :retracted)
            a (publish initial (conj points withdrawal))
            b (publish (publish initial [withdrawal]) (reverse points))]
        (is (= (behavior/observed a 'sums) (behavior/observed b 'sums)))))))

(deftest semantic-order-and-evidence-revision
  (let [events [(point :b 10 2) (point :a 10 1) (point :c 20 3)]
        s (publish @composed events)
        newer (supported (temporal/occurrence :b 10 1 7))
        refined (publish s [newer])]
    (is (= [1 12 123] (values s 'digits)))
    (is (= [:a :b :c] (mapv :id (records s 'digits))))
    (is (= [1 17 173] (values refined 'digits)))
    (is (= (behavior/observed refined 'digits)
           (behavior/observed (publish refined events) 'digits)))))

(deftest intervals-points-and-partial-information
  (let [initial (open "intervals")
        a (supported (temporal/interval :a 1 2 0 10))
        b (supported (temporal/interval :b 2 3 0 20))
        s (publish initial [a b])]
    (is (= [10] (values s 'at-start)))
    (is (= [20] (values s 'at-boundary)))
    (is (= [] (values s 'outside)))
    (is (= [20] (values s 'held-after)))
    (let [p (publish initial [(point :point 1 9)])]
      (is (= [] (values p 'at-boundary)))
      (is (= [9] (values p 'held-after))))
    (let [partial (publish initial [(point :partial 1 {:a 1}) (point :partial 1 {:b 2})])]
      (is (= [{:a 1 :b 2}] (values partial 'at-start))))
    (is (thrown? Exception (temporal/interval :empty 2 2 0 1)))
    (let [r {:id :x :temporal/type :interval :time 1 :end 2
             :candidates #{{:base 10 :support #{}}} :selection {:observations #{} :states #{}}}]
      (is (= [(assoc r :end 3)] (temporal/normalize [r (assoc r :time 2 :end 3)]))))))

(deftest alternatives-conflicts-and-unknowns
  (let [a (supported (temporal/occurrence :a 1 1) #{(premise :proof-a 0 :active)})
        b (supported (temporal/occurrence :a 1 1) #{(premise :proof-b 0 :active)})
        s (publish @minimal [a b])
        withdrawn (publish s [(belief :proof-a 1 :retracted)])]
    (is (= [1] (values s 'sums)))
    (is (= 2 (count (:alternatives (first (records s 'sums))))))
    (is (= [1] (values withdrawn 'sums)))
    (is (= #{(premise :proof-b 0 :active)} (:support (first (records withdrawn 'sums)))))
    (let [conflicted (publish @minimal [(point :a 1 1) (point :a 1 2)])]
      (is (value/contradiction? (first (values conflicted 'sums))))
      (is (= 2 (count (:alternatives (first (records conflicted 'sums))))))))
  (let [unknown (publish @minimal [(point :a 1 value/nothing) (point :b 2 2)])
        refined (publish unknown [(point :a 1 1)])]
    (is (every? value/nothing? (values unknown 'sums)))
    (is (= [1 3] (values refined 'sums)))))

(deftest merge-time-reduction-cache-and-instance-isolation
  (let [counter (atom {})
        initial @minimal
        s (binding [temporal/*instrument* counter] (reduce #(publish %1 [%2]) initial points))
        calls (:reducer-invocations @counter)
        before @counter
        copied (binding [temporal/*instrument* counter]
                 (dotimes [_ 10]
                   (merge/strongest-value (content s 'sums) (session/network s)))
                 (publish s points))
        recomputed (binding [temporal/*reuse* false] (publish initial points))]
    (is (= 2 calls))
    (is (= before @counter))
    (is (= (behavior/observed s 'sums) (behavior/observed copied 'sums)))
    (is (= (behavior/observed s 'sums) (behavior/observed recomputed 'sums)))
    (is (= [] (values initial 'sums)))
    (is (= (topology initial) (topology s)))))

(deftest bounded-payload-cache-and-stale-replay
  (let [initial @windowed
        events (mapv #(point % % 1) (range 1 71))
        s (publish initial events)
        source (slots s 'input-cell)
        sums (slots s 'sums)
        replayed (publish s (take 6 events))
        recovered (publish replayed [(belief 1 2 :active)])]
    (is (= (vec (range 1 65)) (values s 'sums)))
    (is (= 64 (count (get-in source [:evidence :support/observations]))))
    (is (= 64 (count (:cache sums))))
    (is (= 64 (count (get-in sums [:result :records]))))
    (is (= 6 (count (:unavailable-ids source))))
    (is (= (set (range 7 71))
           (into #{} (mapcat (fn [entry]
                               (map #(get-in % [:base :id])
                                    (get-in entry [:record :selection :observations])))) (:cache sums))))
    (is (contains? (:unavailable-ids (behavior/observed recovered 'sums)) 1))
    (is (= :unavailable (:status (behavior/observed s 'old-query))))
    (is (= (behavior/observed s 'sums) (behavior/observed replayed 'sums)))
    (is (= (values s 'sums) (values recovered 'sums)))
    (is (= (topology initial) (topology recovered)))))

(deftest temporal-selection-boundaries-and-filter-unknowns
  (let [initial @composed
        settled (publish initial points)
        withdrawn (publish settled [(belief 2 1 :retracted)])
        recovered (publish withdrawn [(belief 2 2 :active)])]
    (is (= [1 4] (values withdrawn 'sums)))
    (is (= [2 8] (values withdrawn 'doubled)))
    (is (= [1] (values withdrawn 'between)))
    (is (= [1 3 6] (values recovered 'sums)))
    (is (contains? (get-in (first (records settled 'held)) [:selection :observations])
                   (first (:support/observations (second points)))))
    (let [unknown (publish initial [(point :x 1 value/nothing) (point :y 2 2)])]
      (is (every? value/nothing? (values unknown 'filtered-sums)))))
  (let [initial (open "intervals")
        left [(supported (temporal/interval :a 1 5 0 1))
              (supported (temporal/interval :b 2 4 0 2))]
        right [(supported (temporal/interval :c 1 3 0 10))
               (supported (temporal/interval :d 3 5 0 20))]
        settled (publish (publish initial left) 'other-input right)]
    (is (= [1 2 3 3] (mapv :time (records settled 'lifted))))
    (is (= [11 23 44 66] (values settled 'lifted-scan)))
    (is (value/contradiction? (first (values settled 'at-boundary))))
    (is (= :unknown (:status (behavior/observed initial 'at-start))))))

(deftest source-import-selects-local-evaluation
  (let [plain (session/create-session)
        imported (session/extend-session plain
                                         (behavior/extension root "test/fixtures/behavior/minimal.lain"))
        replayed (session/extend-session imported
                                         (behavior/extension root "test/fixtures/behavior/minimal.lain"))]
    (is (nil? (:evaluation (session/session-declarations plain))))
    (is (= #{:cell-evaluator :iterator-factory}
           (set (keys (:evaluation (session/session-declarations imported))))))
    (is (identical? imported replayed))
    (is (= [1 3 6] (values (publish imported points) 'sums)))
    (is (= [1 3 6] (values (publish (session/rerun imported (random-uuid)) points) 'sums)))
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Conflicting monotone extension"
                          (session/extend-session imported
                                                  (session/session-extension {:id :incompatible-evaluator
                                                                              :evaluation {:cell-evaluator identity}}))))))

(deftest authoritative-snapshot-adapter
  (let [initial (open "snapshot")
        list-of (fn [xs] (reduce (fn [tail v] {:car v :cdr tail}) :lain.data/empty (reverse xs)))
        snapshot (fn [revision xs]
                   (collection/content {:base (list-of xs)
                                        :support #{(premise :snapshot revision :active)}}))
        empty-state (publish initial 'event-list [(snapshot 0 [])])
        events [{:id :a :time 1 :value 1} {:id :b :time 2 :value 2}]
        settled (publish empty-state 'event-list [(snapshot 1 events)])
        removed (publish settled 'event-list [(snapshot 2 [(first events)])])
        recovered (publish removed 'event-list [(snapshot 3 events)])]
    (is (= 0 (connected/payload (session/binding-value empty-state 'current))))
    (is (= [1 3] (values settled 'states)))
    (is (= [1] (values removed 'states)))
    (is (= [1 3] (values recovered 'states)))
    (is (= 3 (connected/payload (session/binding-value recovered 'current))))
    (is (= 3 (connected/payload (session/binding-value recovered 'nested-current))))
    (is (= (topology empty-state) (topology recovered)))))

(deftest snapshot-proof-changes-reuse-values-and-withdraw-current
  (let [initial (open "snapshot")
        entries {:car {:id :one :time 1 :value 1}
                 :cdr {:car {:id :two :time 2 :value 2} :cdr :lain.data/empty}}
        snapshot (fn [revision]
                   (collection/content {:base entries :support #{(premise :snapshot revision :active)}}))
        settled (publish initial 'event-list [(snapshot 0)])
        counters (atom {})
        justified (binding [temporal/*instrument* counters]
                    (publish settled 'event-list [(snapshot 1)]))
        withdrawn (publish justified 'event-list [(belief :snapshot 2 :retracted)])
        recovered (publish withdrawn 'event-list [(snapshot 3)])]
    (is (= [1 3] (values justified 'states)))
    (is (= 0 (get @counters :reducer-invocations 0)))
    (is (= #{(premise :snapshot 1 :active)} (:support (last (records justified 'states)))))
    (is (value/nothing? (connected/payload
                         (session/binding-value withdrawn 'current))))
    (is (= 3 (connected/payload
              (session/binding-value recovered 'current))))))

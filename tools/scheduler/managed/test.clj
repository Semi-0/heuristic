(load-file "/home/lain/projects/lain-workspace/tools/scheduler/managed/main.clj")
(require '[scheduler.managed.main]
         '[clojure.test :refer [deftest is run-tests]])
(def directory "/home/lain/projects/lain-workspace/tools/scheduler/managed")
(def job {:poll_seconds 1 :max_ticks 8})
(def base {:notified false :terminal false :expired false :started false :admitted false :idle false})

(defn scenario [observations]
  (let [calls (atom []) clock (promise) stopped (atom false)
        result (future
                 (scheduler.managed.main/run!
                  directory job
                  (fn [op args]
                    (swap! calls conj op)
                    (case op
                      "observe" (nth observations (dec (long (second args))))
                      "start" {:status "launched"}
                      "wake" {:status "sent"}))
                  (fn [_ _ emit!] (deliver clock emit!) #(reset! stopped true))
                  60000))]
    (let [emit! (deref clock 30000 nil)]
      (when-not (fn? emit!)
        (println "EARLY RESULT" (if (realized? result) @result :still-running)))
      (is (fn? emit!))
      (doseq [n (range 1 (inc (count observations)))]
        (emit! n)
        ;; Wait for this observation's effect to settle, then advance the synthetic clock.
        (Thread/sleep 400)))
    (let [outcome (deref result 65000 {:result {:status :test-timeout}})]
      (is (= :closed (get-in outcome [:result :status])) (pr-str outcome))
      (is @stopped)
      @calls)))

(deftest clock-queue-codex
  (let [calls (scenario [base
                         (assoc base :admitted true :idle false)
                         (assoc base :admitted true :idle true)
                         (assoc base :started true)
                         (assoc base :started true :terminal true)])]
    (is (= 5 (count (filter #{"observe"} calls))))
    (is (= 1 (count (filter #{"start"} calls))))
    (is (= 1 (count (filter #{"wake"} calls))))))

(deftest terminal-without-admission
  (is (= ["observe" "wake"] (scenario [(assoc base :started true :terminal true)]))))
(deftest expired-without-launch
  (is (= ["observe" "wake"] (scenario [(assoc base :expired true)]))))
(deftest replay-does-not-notify
  (is (= ["observe"] (scenario [(assoc base :notified true :terminal true)]))))

(let [result (run-tests)]
  (shutdown-agents)
  (System/exit (if (zero? (+ (:fail result) (:error result))) 0 1)))

(ns lain-harness.behavior-benchmark
  (:require [lain-harness.behavior-test :as fixture]
            [propagators.cells.cell :as cell]
            [propagators.compiler-2.session :as session]
            [propagators.datastructures.reducer-cell :as reducer]
            [propagators.datastructures.support-collection :as collection]
            [propagators.experimental.temporal-behavior :as temporal]
            [propagators.network :as net]))

(defn milliseconds [start] (/ (- (System/nanoTime) start) 1e6))
(defn heap [] (let [r (Runtime/getRuntime)] (- (.totalMemory r) (.freeMemory r))))
(defn percentile [xs quantile]
  (nth (vec (sort xs)) (max 0 (dec (int (Math/ceil (* quantile (count xs))))))))

(defn retained-counts
  "Count unique semantic reducer identities across real cell contents and nested
  transported inputs. Template topology is static setup, excluded from data counts.
  Selection references are counted separately to expose quadratic provenance."
  [s]
  (let [roots (keep #(when (cell/cell? %) (cell/cell-content %)) (vals (net/net-env (session/network s))))
        contents (mapcat #(tree-seq reducer/reducer-cell?
                                    (fn [r] (vals (:inputs (reducer/reducer-slots r)))) %) roots)
        reducers (vals (into {} (map (juxt reducer/reducer-id reducer/reducer-slots))
                             (filter reducer/reducer-cell? contents)))
        evidence (concat (filter collection/content? contents) (keep :evidence reducers))]
    {:retained-evidence (count (into #{} (mapcat :support/observations) evidence))
     :derived-candidates (reduce + (for [r reducers record (get-in r [:result :records])] (count (:candidates record))))
     :cache-entries (reduce + (map #(count (:cache %)) reducers))
     :selection-references (reduce + (for [r reducers record (get-in r [:result :records])]
                                       (count (get-in record [:selection :observations]))))
     :premise-states (count (into #{} (mapcat :support/states) evidence))
     :replay-tombstones (count (into #{} (mapcat :unavailable-ids) reducers))}))

(defn- validate! [s workload events]
  (let [expected (if (= workload :window)
                   (fixture/reference-scan (take-last 64 events))
                   (fixture/reference-scan events))]
    (assert (= expected (fixture/values s 'sums)) "Incorrect benchmark scan")
    (when (= workload :composed)
      (assert (= [(* 2 (last expected))] (fixture/values s 'sampled)) "Incorrect composed sample"))
    (when (= workload :window)
      (let [counts (retained-counts s)]
        (assert (<= (:retained-evidence counts) 64) "Window retained old payloads")
        (assert (<= (:cache-entries counts) 64) "Window retained old cache entries")))))

(defn- run-once [initial workload events]
  (let [baseline-topology (fixture/topology initial)
        preparation-start (System/nanoTime)
        prepared (if (#{:duplicate :recovery} workload) (fixture/publish initial events) initial)
        preparation-ms (milliseconds preparation-start)
        middle (inc (quot (count events) 2))
        updates (case workload
                  :recovery [[(fixture/belief middle 1 :retracted)] [(fixture/belief middle 2 :active)]]
                  (mapv vector events))
        counter (atom {})
        memory-before (heap)
        started (System/nanoTime)
        [settled latencies]
        (binding [temporal/*instrument* counter]
          (reduce (fn [[s timings] update]
                    (let [start (System/nanoTime)
                          next-session (fixture/publish s update)
                          elapsed (milliseconds start)]
                      (when (and (= workload :recovery) (= update (first updates)))
                        (assert (= (fixture/reference-scan (concat events update))
                                   (fixture/values next-session 'sums)) "Withdrawal failed"))
                      [next-session (conj timings elapsed)])) [prepared []] updates))
        elapsed (milliseconds started)
        memory-after (heap)]
    (validate! settled workload events)
    (assert (= baseline-topology (fixture/topology settled)) "Active topology grew")
    (when (= workload :duplicate)
      (assert (zero? (get @counter :reducer-invocations 0)) "Duplicate replay reduced again"))
    {:processing-ms elapsed :preparation-ms preparation-ms
     :median-ms (percentile latencies 0.5) :p95-ms (percentile latencies 0.95)
     :latencies-ms latencies :reducer-invocations (get @counter :reducer-invocations 0)
     :topology-before (count baseline-topology) :topology-after (count (fixture/topology settled))
     :retained (retained-counts settled) :approximate-heap-change-bytes (- memory-after memory-before)}))

(defn run-case! [workload-text size-text]
  (let [workload (keyword workload-text) size (parse-long size-text)
        start (System/nanoTime)
        initial (fixture/open (case workload :window "window" :composed "benchmark" "minimal"))
        initial (if (= workload :composed)
                  (fixture/publish initial 'query-time [size]) initial)
        setup-ms (milliseconds start)
        events (mapv #(fixture/point % % (inc (mod % 7))) (range 1 (inc size)))
        baseline-start (System/nanoTime)
        _ (when (= workload :scan)
            (doseq [n (range 1 (inc size))] (fixture/reference-scan (subvec events 0 n))))
        baseline-ms (when (= workload :scan) (milliseconds baseline-start))]
    (prn {:workload workload :size size :phase :setup :compile-setup-ms setup-ms
          :reference-recomputation-ms baseline-ms
          :environment {:java (System/getProperty "java.version")
                        :clojure (clojure-version) :os (System/getProperty "os.name")
                        :arch (System/getProperty "os.arch")
                        :max-heap-bytes (.maxMemory (Runtime/getRuntime))}})
    (flush)
    (let [warmup (run-once initial workload events)]
      (prn (merge {:workload workload :size size :phase :warmup-complete :validated true}
                  (select-keys warmup [:retained :topology-before :topology-after]))))
    (flush)
    (let [runs (mapv (fn [repetition]
                       (let [result (run-once initial workload events)]
                         (prn (merge {:workload workload :size size :repetition repetition :validated true}
                                     (dissoc result :latencies-ms)))
                         (flush)
                         result)) (range 1 4))
          latencies (mapcat :latencies-ms runs)]
      (prn {:workload workload :size size :status :complete :validated true
            :compile-setup-ms setup-ms :warmup-runs 1 :measured-runs 3
            :median-ms (percentile latencies 0.5) :p95-ms (percentile latencies 0.95)
            :processing-total-ms (reduce + (map :processing-ms runs))
            :reference-recomputation-ms baseline-ms}))
    (flush)))

(ns scheduler.reactive-scheduler.ingress-test)
(load-file "/home/lain/projects/lain-workspace/tools/scheduler/reactive_scheduler/adapter.clj")
(require '[scheduler.reactive-scheduler.adapter :as a]
         '[propagators.compiler-2.session :as s]
         '[propagators.compiler-2.session.host :as host]
         '[clojure.java.io :as io])
(def root (first *command-line-args*))
(def notifications (atom []))
(defn recording-io! [op args]
  (let [[task value] args]
    (case op
      :observe {:terminal true :acknowledged false}
      :notify (do (swap! notifications conj (:id task)) {:task (:id task)})
      :acknowledge (assoc value :acknowledged true))))
(defn task [id] {:id id :unit "synthetic-run" :deadline 9999999999})
(defn source [id text] (s/session-extension {:id id :files {(str "test-" (name id) ".lain") text}}))
(defn await! [m owner pred label]
  (let [end (+ (System/currentTimeMillis) 30000)]
    (loop []
      (when-let [e (first (host/failures owner))] (throw e))
      (when-let [e (first @(:errors m))] (throw (ex-info "Effect error" e)))
      (cond (pred) true
            (> (System/currentTimeMillis) end) (throw (ex-info "Timeout" {:label label :metrics @(:metrics m) :observer-errors @(:observer-errors m)}))
            :else (do (Thread/sleep 50) (recur))))))
(defn count-files [m dir] (count (filter #(.isFile %) (file-seq (io/file (:root m) dir)))))
(defn shape [owner]
  (let [nodes (count (:graph (s/network (host/current-session owner))))
        receipts (count (host/receipts owner))
        bytes (count (.getBytes (pr-str (s/network (host/current-session owner))) "UTF-8"))]
    (dotimes [_ 3] (System/gc) (Thread/sleep 100))
    {:nodes nodes :receipts receipts :network-edn-bytes bytes
     :used-heap-bytes (- (.totalMemory (Runtime/getRuntime)) (.freeMemory (Runtime/getRuntime)))}))
(def result (atom {}))
(try
  (let [m (assoc (a/manager (str root "/queue") recording-io!) :clock-period 0.1)
        owner (host/open! (s/create-session) {:cleanup-timeout-ms 30000})
        command "11111111111111111111111111111111"
        envelope {:op "enqueue" :task (task "first")}
        file (io/file (:root m) "inbox" (str command ".json"))]
    (try
      (host/extend! owner (a/extension m))
      (host/extend! owner (source :test/runtime "(define clock (clock:shared 0.1)) (define queue (queue:open)) (define scheduler (runtime clock queue))"))
      (a/atomic-write! file envelope)
      (await! m owner #(>= @(:tick m) 4) :clock)
      (let [other (a/manager (:root m) recording-io!)]
        (try
          (assert (try (a/start-runner! other (:clock-id other) :watch-task (:root other) (fn [_]))
                       false (catch java.nio.channels.OverlappingFileLockException _ true)))
          (assert (not (realized? (:runner-ready other))))
          (swap! result assoc :duplicate-runner-rejected-before-ready :passed)
          (finally (.shutdown ^java.util.concurrent.ExecutorService (:retirer other)))))
      (assert (.exists file))
      (assert (zero? (count-files m "queued")))
      (assert (zero? (get @(:metrics m) :created 0)))
      (host/extend! owner (source :test/ingress "(define inbox (inbox:open)) (define ingress (receive-tasks inbox queue))"))
      (await! m owner #(and (= 1 (count-files m "completed")) (not (.exists file)) (empty? @(:scopes m))) :first-complete)
      (assert (zero? (count-files m "log")) "Unconnected observers must not log")
      (host/extend! owner (source :test/observer "(define log (log:open)) (define observation (observe-scheduler queue scheduler log))"))
      (await! m owner #(and (zero? (count-files m "queue-events")) (zero? (count-files m "runtime-events"))) :initial-logs)
      (let [before (shape owner)]
        (doseq [n (range 10)]
          (a/enqueue! m (task (str "task-" n)))
          (await! m owner #(and (= (+ n 2) (count-files m "completed")) (empty? @(:scopes m))) :completed))
        (await! m owner #(and (zero? (count-files m "queue-events")) (zero? (count-files m "runtime-events"))) :logs-drained)
        (let [after (shape owner)]
          (assert (= (:nodes before) (:nodes after)))
          (swap! result assoc :before before :after after)))
      (a/atomic-write! file envelope)
      (await! m owner #(not (.exists file)) :replay)
      (assert (= 11 (:created @(:metrics m))))
      (assert (= 11 (count @notifications)))
      (let [records (map a/read-record (filter #(.isFile %) (file-seq (io/file (:root m) "log"))))]
        (assert (= #{"queue" "runtime"} (set (map :kind records))))
        (swap! result assoc :log-records (count records)))
      (swap! result assoc :ingress-required :passed :observer-attached-later :passed :command-replay :passed)
      (finally (host/close! owner))))
  (let [m (assoc (a/manager (str root "/failed-observer") recording-io!) :clock-period 0.1 :fail-log? true)
        owner (host/open! (s/create-session) {:cleanup-timeout-ms 30000})]
    (try
      (host/extend! owner (a/extension m))
      (host/extend! owner (a/start-extension))
      (a/enqueue! m (task "observer-fault"))
      (await! m owner #(and (= 1 (count-files m "completed")) (empty? @(:scopes m)) (seq @(:observer-errors m))) :observer-fault)
      (assert (empty? (host/failures owner)))
      (swap! result assoc :observer-failure-isolated :passed)
      (finally (host/close! owner))))
  (swap! result assoc :status :passed)
  (catch Throwable e (swap! result assoc :status :failed :error (Throwable->map e))))
(spit (str root "/result.edn") (pr-str @result))
(println (pr-str @result))
(shutdown-agents)
(System/exit (if (= :passed (:status @result)) 0 1))

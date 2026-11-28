(load-file "/home/lain/projects/lain-workspace/tools/scheduler/live_queue/bridge.clj")
(require '[scheduler.live-queue.bridge :as bridge]
         '[propagators.compiler-2.session :as session]
         '[propagators.compiler-2.session.host :as host]
         '[clojure.data.json :as json])

(def root (first *command-line-args*))
(def thread "01a1a907-9c5b-77a2-af92-66ed9c602ab1")
(def unit "lain-test-04863c7227364b97ba1cf778ed693b68.service")
(def task-source (str "(task:record \"real-receipt-smoke\" " (pr-str unit) " 9999999999 " (pr-str thread) ")"))
(defn declaration [id source]
  (session/session-extension {:id id :files {(str (name id) ".lain") source}}))
(defn await! [owner condition]
  (let [end (+ (System/currentTimeMillis) 30000)]
    (loop []
      (when-let [error (first (host/failures owner))] (throw error))
      (cond (condition) true
            (> (System/currentTimeMillis) end) (throw (ex-info "Timed out" {}))
            :else (do (Thread/sleep 50) (recur))))))
(defn acknowledged? []
  (some (fn [f] (when (= "state.json" (.getName f))
                  (:acknowledged (json/read-str (slurp f) :key-fn keyword))))
        (file-seq (java.io.File. root))))

(def outcomes (atom []))
(doseq [pass [1 2]]
  (let [metrics (atom {}) callback (atom nil) owner (host/open! (session/create-session))]
    (try
      (host/extend! owner (bridge/extension root metrics callback))
      (host/extend! owner
                    (declaration :scheduler/start
                                 "(define clock (clock:every 1)) (define tasks (let-cell [port] port)) (define notifications (runtime clock :queue :codex tasks))"))
      (await! owner #(>= (get @metrics :ticks 0) 2))
      (assert (zero? (get @metrics :observe 0)) "Empty open queue must not observe a task")
      (let [task (declaration :scheduler/add
                              (str "(define tail (let-cell [port] port)) (-> (cons " task-source " tail) tasks)"))]
        (host/extend! owner task)
        (await! owner #(pos? (get @metrics :observe 0)))
        (if (= pass 1)
          (await! owner #(and (acknowledged?) (pos? (get @metrics :acknowledge 0))))
          (await! owner #(>= (get @metrics :observe 0) 2)))
        (host/extend! owner task)
        ;; An additional queue position with the SAME task/run identity must not redeliver.
        (host/extend! owner (declaration :scheduler/duplicate (str "(-> (list " task-source ") tail)"))))
      (let [ticks (:ticks @metrics) notifications (get @metrics :notify 0)]
        (await! owner #(>= (get @metrics :ticks 0) (+ ticks 3)))
        (assert (= notifications (get @metrics :notify 0)) "Acknowledged task was notified again"))
      (assert (= 1 (:clock @metrics)))
      (assert (zero? (get @metrics :clock-cleanup 0)))
      (when (= pass 2) (assert (zero? (get @metrics :notify 0)) "Reopen resent acknowledged event"))
      (assert (empty? (host/failures owner)))
      (finally (host/close! owner)))
    (let [late-result (@callback)]
      (assert (= :closed late-result) "Closed owner's callback must be rejected")
      (swap! outcomes conj {:pass pass :metrics @metrics :late-callback late-result}))))
(spit (str root "/full-result.edn") (pr-str {:status :passed :outcomes @outcomes}))
(println (pr-str {:status :passed :outcomes @outcomes}))
(shutdown-agents)

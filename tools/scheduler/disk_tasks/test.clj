(ns scheduler.disk-tasks.test)
(load-file "/home/lain/projects/lain-workspace/tools/scheduler/disk_tasks/adapter.clj")
(require '[scheduler.disk-tasks.adapter :as a]
         '[propagators.compiler-2.session :as s]
         '[propagators.compiler-2.session.host :as host])
(def root (first *command-line-args*))
(def state (atom {:terminal #{} :notifications [] :observations {}}))
(defn recording-io! [op args]
  (let [[task value] args id (:id task)]
    (case op
      :observe (do (swap! state update-in [:observations id] (fnil inc 0))
                   {:terminal (contains? (:terminal @state) id) :acknowledged false})
      :notify (do (swap! state update :notifications conj [id value]) {:task id :event value})
      :acknowledge (do (assert (= id (:task value))) (assoc value :acknowledged true)))))
(defn task [id deadline] {:id id :unit "run-1" :deadline deadline})
(defn boot! [m]
  (let [owner (host/open! (s/create-session))]
    (try
      (host/extend! owner (a/extension m))
      (host/extend! owner (s/session-extension {:id :test/start :files {"start.lain" "(define clock (clock:shared 0.1)) (define scheduler (runtime clock (queue:open)))"}}))
      owner
      (catch Throwable e (host/close! owner) (throw e)))))
(defn await! [m owner pred label]
  (let [end (+ (System/currentTimeMillis) 30000)]
    (loop []
      (doseq [o (cons owner (keep deref (vals @(:scopes m))))]
        (when-let [e (first (host/failures o))] (throw e)))
      (cond (pred) true
            (> (System/currentTimeMillis) end) (throw (ex-info "Timed out" {:label label :errors @(:errors m) :metrics @(:metrics m)}))
            :else (do (Thread/sleep 50) (recur))))))
(defn done? [m t] (and (.exists (a/path m "completed" (a/key-of t))) (empty? @(:scopes m))))
(defn shape [owner]
  {:nodes (count (:graph (s/network (host/current-session owner))))
   :receipts (count (host/receipts owner))})
(def results (atom {}))
(try
  (let [m (a/manager (str root "/queue") recording-io!) owner (boot! m)]
    (try
      (await! m owner #(and (>= @(:tick m) 3) (= 2 (count (host/receipts owner)))) :ready)
      (let [baseline (shape owner) t (task "waiting" 9999999999)]
        (a/enqueue! m t)
        (await! m owner #(>= (get-in @state [:observations "waiting"] 0) 2) :waiting)
        (let [late (first (vals @(:subscribers m)))]
          ;; A waiting task must not prevent another task's deadline alert.
          (a/enqueue! m (task "deadline" 0))
          (await! m owner #(.exists (a/path m "completed" ["deadline" "run-1"])) :concurrent-deadline)
          (assert (contains? @(:scopes m) ["waiting" "run-1"]))
          (swap! state update :terminal conj "waiting")
          (await! m owner #(done? m t) :completion)
          (assert (= :closed (late 9999999999))))
        (let [n (get-in @state [:observations "waiting"]) tick @(:tick m)]
          (await! m owner #(>= @(:tick m) (+ tick 3)) :clock-alive)
          (assert (= n (get-in @state [:observations "waiting"]))))
        (assert (= :duplicate (a/enqueue! m t)))
        (assert (try (a/enqueue! m (assoc t :deadline 3)) false (catch Exception _ true)))
        (let [deadline (task "deadline" 0)]
          (a/enqueue! m deadline)
          (await! m owner #(done? m deadline) :deadline))
        (doseq [n (range 8)]
          (let [id (str "batch-" n) t (task id 9999999999)]
            (swap! state update :terminal conj id)
            (a/enqueue! m t)
            (await! m owner #(done? m t) id)
            (assert (= baseline (shape owner)) "Parent grew after task")))
        (dotimes [_ 3] (System/gc) (Thread/sleep 100))
        (swap! results assoc :steady {:before baseline :after (shape owner)
                                      :completed 10 :metrics @(:metrics m)
                                      :weak-owners-live (count (filter #(.get %) @(:weak m)))
                                      :weak-networks-live (count (filter #(.get %) @(:networks m)))})
        (assert (empty? @(:errors m)))
        (assert (= 10 (count (:notifications @state))))
        (assert (some #{["deadline" "deadline"]} (:notifications @state)))
        ;; Interrupted active entry stays on disk and is NOT automatically replayed.
        (a/enqueue! m (task "unfinished" 9999999999))
        (await! m owner #(pos? (get-in @state [:observations "unfinished"] 0)) :unfinished))
      (finally (host/close! owner)))
    (assert (empty? @(:scopes m)))
    (assert (empty? @(:subscribers m))))
  (let [m (a/manager (str root "/queue") recording-io!) owner (boot! m) before (count (:notifications @state))]
    (try
      (assert (= :duplicate (a/enqueue! m (task "waiting" 9999999999))))
      (await! m owner #(>= @(:tick m) 5) :reopen)
      (assert (zero? (get @(:metrics m) :created 0)))
      (assert (= before (count (:notifications @state))))
      (assert (.exists (a/path m "active" ["unfinished" "run-1"])))
      (swap! results assoc :reopen :passed)
      (finally (host/close! owner))))
  (let [m (assoc (a/manager (str root "/renewal") recording-io!) :renew-seconds 0.3)
        owner (boot! m) t (task "renewing" 9999999999)]
    (try
      (a/enqueue! m t)
      (await! m owner #(>= (get @(:metrics m) :renewed 0) 2) :renewal)
      (swap! state update :terminal conj "renewing")
      (await! m owner #(done? m t) :renewed-completion)
      (assert (= 1 (count (filter #(= "renewing" (first %)) (:notifications @state)))))
      (swap! results assoc :renewal {:status :passed :metrics @(:metrics m) :parent (shape owner)})
      (finally (host/close! owner))))
  (let [m (assoc (a/manager (str root "/failed-write") recording-io!) :persist! (fn [& _] (throw (ex-info "Injected disk failure" {}))))
        owner (boot! m) t (task "disk-failure" 0)]
    (try
      (a/enqueue! m t)
      (await! m owner #(seq @(:errors m)) :disk-failure)
      (assert (= 1 (count @(:scopes m))))
      (assert (not (.exists (a/path m "completed" (a/key-of t)))))
      (assert (.exists (a/path m "active" (a/key-of t))))
      (swap! results assoc :failed-write-keeps-scope :passed)
      (finally (host/close! owner))))
  (swap! results assoc :status :passed)
  (catch Throwable e (swap! results assoc :status :failed :error (Throwable->map e))))
(spit (str root "/result.edn") (pr-str @results))
(println (pr-str @results))
(shutdown-agents)
(System/exit (if (= :passed (:status @results)) 0 1))

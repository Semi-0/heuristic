(load-file "/home/lain/projects/lain-workspace/tools/scheduler/map_task/adapter.clj")
(require '[scheduler.map-task.adapter :as adapter]
         '[propagators.compiler-2.session :as session]
         '[propagators.compiler-2.session.host :as host])

(def state (atom {:terminal #{} :acknowledged #{} :observations {} :notifications []}))
(def m
  (adapter/manager
   (fn [op args]
     (let [[task value] args id (:id task)]
       (case op
         :observe (do (swap! state update-in [:observations id] (fnil inc 0))
                      {:terminal (contains? (:terminal @state) id)
                       :acknowledged (contains? (:acknowledged @state) id)})
         :notify (do (swap! state update :notifications conj [id value])
                     {:task id :event value :accepted true})
         :acknowledge (do (assert (= id (:task value)))
                          (swap! state update :acknowledged conj id)
                          (assoc value :acknowledged true)))))))
(def owner (host/open! (session/create-session)))
(defn extension [id code] (session/session-extension {:id id :files {(str (name id) ".lain") code}}))
(defn await! [pred label]
  (let [end (+ (System/currentTimeMillis) 25000)]
    (loop []
      (doseq [o (cons owner (keep deref (vals @(:scopes m))))]
        (when-let [e (first (host/failures o))] (throw e)))
      (when-let [e (first @(:errors m))] (throw e))
      (cond (pred) true
            (> (System/currentTimeMillis) end) (throw (ex-info "Timed out" {:label label :metrics @(:metrics m) :state @state}))
            :else (do (Thread/sleep 50) (recur))))))
(def report (atom {}))
(try
  (host/extend! owner (adapter/extension m))
  (host/extend! owner (extension :test/start "(define clock (clock:shared 0.2)) (define tasks) (define result (runtime clock tasks))"))
  (await! #(>= @(:tick m) 2) :empty-clock)
  (assert (empty? @(:scopes m)))
  (host/extend! owner (extension :test/a "(define tail-a) (-> (cons (task:record \"a\" \"run-1\" 999999) tail-a) tasks)"))
  (await! #(>= (get-in @state [:observations "a"] 0) 2) :waiting-a)
  (let [late (first (vals @(:subscribers m)))
        replay (extension :test/b "(define tail-b) (-> (cons (task:record \"b\" \"run-1\" 0) tail-b) tail-a)")]
    (swap! state update :terminal conj "a")
    (await! #(and (empty? @(:scopes m)) (contains? @(:completed m) ["a" "run-1"])) :retired-a)
    (assert (= :closed (late 999999)) "Late child callback must be rejected")
    (let [before (get-in @state [:observations "a"]) tick @(:tick m)]
      (await! #(>= @(:tick m) (+ tick 3)) :shared-clock-survives)
      (assert (= before (get-in @state [:observations "a"])) "Retired task still polled"))
    (host/extend! owner replay)
    (host/extend! owner replay)
    (await! #(contains? @(:completed m) ["b" "run-1"]) :deadline-b))
  ;; Duplicate acknowledged task: no child, no additional notification.
  (host/extend! owner (extension :test/duplicate "(define tail-c) (-> (cons (task:record \"a\" \"run-1\" 999999) tail-c) tail-b)"))
  (swap! state update :terminal conj "c")
  (host/extend! owner (extension :test/c "(define tail-d) (-> (cons (task:record \"c\" \"run-1\" 999999) tail-d) tail-c)"))
  (await! #(contains? @(:completed m) ["c" "run-1"]) :added-c)
  (assert (= 3 (:created @(:metrics m))))
  (assert (= 3 (:retired @(:metrics m))))
  (assert (empty? @(:subscribers m)))
  (assert (= [["a" "completed"] ["b" "deadline"] ["c" "completed"]] (:notifications @state)))
  ;; An active task must also be cleaned up when the parent is closed.
  (host/extend! owner (extension :test/d "(-> (list (task:record \"d\" \"run-1\" 999999)) tail-d)"))
  (await! #(pos? (get-in @state [:observations "d"] 0)) :waiting-d)
  (assert (= 1 (:clocks @(:metrics m))))
  (assert (zero? (get @(:metrics m) :clock-cleanups 0)))
  (host/close! owner)
  (adapter/close-manager! m)
  (assert (empty? @(:scopes m)))
  (assert (empty? @(:subscribers m)))
  (assert (= 4 (:retired @(:metrics m))))
  (assert (= 1 (:clock-cleanups @(:metrics m))))
  ;; Diagnostic, not a deterministic GC requirement. Only weak refs remain here.
  (dotimes [_ 3] (System/gc) (Thread/sleep 100))
  (reset! report {:status :passed :metrics @(:metrics m) :state @state
                  :weak-owners-live (count (filter #(.get %) @(:weak m)))
                  :weak-networks-live (count (filter #(.get %) @(:networks m)))
                  :active-scopes (count @(:scopes m)) :subscribers (count @(:subscribers m))})
  (catch Throwable e (reset! report {:status :failed :error (Throwable->map e) :metrics @(:metrics m) :state @state}))
  (finally (host/close! owner) (adapter/close-manager! m)))
(spit (first *command-line-args*) (pr-str @report))
(println (pr-str @report))
(shutdown-agents)
(System/exit (if (= :passed (:status @report)) 0 1))

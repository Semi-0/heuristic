(ns scheduler.live-queue.run
  (:require
   [propagators.compiler-2.session :as session]
   [propagators.compiler-2.session.files :as files]
   [propagators.compiler-2.session.host :as host]
   [propagators.compiler-2.session.operators :as ops]
   [propagators.stdlib.source-publication :as publication])
  (:import [java.util.concurrent Executors ScheduledExecutorService TimeUnit]))

(def directory "/home/lain/projects/lain-workspace/tools/scheduler/live_queue")
(def runtime-root "/home/lain/reports/xiaobao-takeover/20261114-r3/source-3c3f643")
(defn prim [f] (ops/prim-op (fn [make] (make f))))
(defn async-op [interpreter op]
  (ops/async-op interpreter
                (fn [make] (make (fn [& args] (ops/effect-output {:op op :args args}))))))

(defn extension [state]
  (let [interpreter
        (reify ops/EffectInterpreter
          (interpret-effect! [_ _ context {:keys [op args]} emit!]
            (case op
              :clock
              (let [timer (Executors/newSingleThreadScheduledExecutor)
                    period (first args)
                    tick (atom 0)]
                (swap! state update :clock-subscriptions inc)
                (.scheduleWithFixedDelay
                 ^ScheduledExecutorService timer
                 ^Runnable (fn []
                             (let [n (swap! tick inc)]
                               (swap! state assoc :ticks n)
                               (emit! (publication/active [:clock (:id context)] n n))))
                 0 (long (* 1000 period)) TimeUnit/MILLISECONDS)
                (fn []
                  (.shutdownNow timer)
                  (swap! state update :clock-cleanups inc)
                  {:status :closed}))
              :observe
              (let [[_ task tick] args id (:id task)
                    observed {:terminal (contains? (:terminal @state) id)
                              :acknowledged (contains? (:acknowledged @state) id)}]
                (swap! state update :observations conj [id tick])
                (emit! observed)
                ((:complete! context))
                nil)
              :notify
              (let [[_ task kind] args id (:id task) key [id kind]]
                ;; Recording sink's idempotency boundary, independent of Lain's selection.
                (swap! state update :notification-attempts conj key)
                (swap! state update :delivered conj key)
                (emit! {:task id :event key :status "accepted"})
                ((:complete! context))
                nil)
              :acknowledge
              (let [[_ task receipt] args]
                (when-not (= (:id task) (:task receipt))
                  (throw (ex-info "Receipt task mismatch" {})))
                (swap! state update :acknowledged conj (:id task))
                (emit! receipt)
                ((:complete! context))
                nil))))]
    (files/prepare-extension!
     {:id :scheduler/live_queue-v1 :root runtime-root
      :files {"runtime.lain" (slurp (str directory "/runtime.lain"))
              "example.lain" (slurp (str directory "/example.lain"))}
      :primitives {'field (prim (fn [record key] (get record (keyword key))))
                   'task:record (prim (fn [id deadline] {:id id :deadline deadline}))
                   'clock:every (async-op interpreter :clock)
                   'queue:observe (async-op interpreter :observe)
                   'codex:notify (async-op interpreter :notify)
                   'queue:acknowledge (async-op interpreter :acknowledge)}})))

(defn await! [owner predicate label]
  (let [end (+ (System/currentTimeMillis) 20000)]
    (loop []
      (when-let [error (first (host/failures owner))] (throw error))
      (cond
        (predicate) true
        (> (System/currentTimeMillis) end) (throw (ex-info "Timed out" {:waiting label}))
        :else (do (Thread/sleep 50) (recur))))))

(let [state (atom {:ticks 0 :clock-subscriptions 0 :clock-cleanups 0
                   :terminal #{} :acknowledged #{} :delivered #{}
                   :notification-attempts [] :observations []})
      owner (host/open! (session/create-session))
      report (atom {})]
  (try
    (host/extend! owner (extension state))
    (await! owner #(>= (:ticks @state) 2) :clock)
    (assert (empty? (:delivered @state)) "Running tasks must not alert")
    (swap! state update :terminal conj "a")
    (await! owner #(contains? (:acknowledged @state) "a") :completed-a)
    (await! owner #(contains? (:acknowledged @state) "b") :deadline-b)
    (assert (= #{["a" "completed"] ["b" "deadline"]} (:delivered @state)))
    ;; Add a task by extending the OPEN Lain list. No host iteration over queue items.
    (swap! state update :terminal conj "c")
    (let [extra (session/session-extension
                 {:id :scheduler/add-task-c
                  :files {"add-c.lain" "(-> (list (task:record \"c\" 100)) later-tasks)"}})]
      (host/extend! owner extra)
      (await! owner #(contains? (:acknowledged @state) "c") :added-c)
      (host/extend! owner extra))
    (let [tick (:ticks @state) attempts (count (:notification-attempts @state))]
      (await! owner #(>= (:ticks @state) (+ tick 3)) :clock-keeps-running)
      (assert (= attempts (count (:notification-attempts @state))) "Acknowledged tasks must stay quiet"))
    (assert (= #{["a" "completed"] ["b" "deadline"] ["c" "completed"]} (:delivered @state)))
    (assert (= 1 (:clock-subscriptions @state)) "One shared clock")
    (assert (= 0 (:clock-cleanups @state)) "Completing jobs must not stop clock")
    (assert (empty? (host/failures owner)))
    (reset! report {:status "passed" :state @state})
    (catch Throwable error
      (reset! report {:status "failed" :error (Throwable->map error) :state @state}))
    (finally (host/close! owner)))
  (swap! report assoc :clock-cleanups-after-owner-close (:clock-cleanups @state))
  (spit (first *command-line-args*) (pr-str @report))
  (println (pr-str (select-keys @report [:status :clock-cleanups-after-owner-close])))
  (shutdown-agents)
  (System/exit (if (= "passed" (:status @report)) 0 1)))

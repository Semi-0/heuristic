(ns scheduler.map-task.adapter
  "Disposable session scopes; task policy and queue traversal remain Lain."
  (:require [propagators.compiler-2.session :as session]
            [propagators.compiler-2.session.files :as files]
            [propagators.compiler-2.session.host :as host]
            [propagators.compiler-2.session.operators :as ops]
            [propagators.stdlib.source-publication :as publication])
  (:import [java.lang.ref WeakReference]
           [java.util.concurrent Executors ScheduledExecutorService ExecutorService TimeUnit]))

(def directory "/home/lain/projects/lain-workspace/tools/scheduler/map_task")
(def runtime-root "/home/lain/reports/xiaobao-takeover/20261114-r3/source-3c3f643")
(defn primitive [f] (ops/prim-op (fn [make] (make f))))
(defn effect [interpreter op]
  (ops/async-op interpreter (fn [make] (make (fn [& args] (ops/effect-output {:op op :args args}))))))

(defn manager
  "IO is injected; no poll/alert policy here. Weak references are diagnostics only."
  [io!]
  {:io! io! :scopes (atom {}) :completed (atom {}) :subscribers (atom {})
   :clock-id (str (random-uuid)) :tick (atom 0) :metrics (atom {}) :weak (atom [])
   :networks (atom []) :errors (atom []) :retirer (Executors/newSingleThreadExecutor)})
(defn count! [m key] (swap! (:metrics m) update key (fnil inc 0)))

(defn close-scope! [m key holder]
  (locking holder
    (when-let [owner @holder]
      ;; Do not discard a scope if cleanup fails.
      (swap! (:networks m) conj (WeakReference. (session/network (host/current-session owner))))
      (host/close! owner)
      (reset! holder nil)
      (swap! (:scopes m) dissoc key)
      (count! m :retired)))
  {:status :closed})

(defn start-task! [m clock module task context emit!]
  (when-not (and (= clock (:clock-id m)) (= module :watch-task))
    (throw (ex-info "Unknown clock or task module" {:module module})))
  (when-not (and (string? (:id task)) (string? (:unit task)))
    (throw (ex-info "Task requires string id and run unit" {})))
  (let [key [(:id task) (:unit task)]
        scope-lock (:scopes m)]
    (locking scope-lock
      (cond
        (contains? @(:completed m) key)
        (do (emit! (get @(:completed m) key)) ((:complete! context)) nil)

        (contains? @(:scopes m) key)
        (do (emit! {:status "already-active" :task key}) ((:complete! context)) nil)

        :else
        (let [owner (host/open! (session/create-session))
              holder (atom owner) ready (promise) finishing (atom false)
              interpreter
              (reify ops/EffectInterpreter
                (interpret-effect! [_ _ child-context {:keys [op args]} child-emit!]
                  (case op
                    :subscribe
                    (let [id (random-uuid)
                          callback #(child-emit! (publication/active [:shared-clock id] % %))]
                      (swap! (:subscribers m) assoc id callback)
                      (count! m :subscriptions)
                      (when (pos? @(:tick m)) (callback @(:tick m)))
                      (fn [] (swap! (:subscribers m) dissoc id)
                        (count! m :unsubscriptions) {:status :closed}))

                    :finish
                    (do
                      (when (compare-and-set! finishing false true)
                        (.submit ^ExecutorService (:retirer m)
                                 ^Runnable
                                 (fn []
                                   @ready
                                   (try
                                     (locking scope-lock
                                       (close-scope! m key holder)
                                       (swap! (:completed m) assoc key (first args)))
                                     (emit! (first args))
                                     ((:complete! context))
                                     (catch Throwable e (swap! (:errors m) conj e))))))
                      nil)

                    (let [result ((:io! m) op args)]
                      (child-emit! result)
                      ((:complete! child-context))
                      nil))))]
          (swap! (:scopes m) assoc key holder)
          (swap! (:weak m) conj (WeakReference. owner))
          (count! m :created)
          (try
            (host/extend!
             owner
             (files/prepare-extension!
              {:id :scheduler/task-worker :root runtime-root
               :files {"worker.lain" (slurp (str directory "/worker.lain"))}
               :primitives {'field (primitive #(get %1 (keyword %2)))
                            'task:current (primitive (fn [] task))
                            'clock:subscribe (effect interpreter :subscribe)
                            'task:finish (effect interpreter :finish)
                            'queue:observe (effect interpreter :observe)
                            'codex:notify (effect interpreter :notify)
                            'queue:acknowledge (effect interpreter :acknowledge)}}))
            (catch Throwable e
              (close-scope! m key holder)
              (throw e))
            (finally (deliver ready true)))
          #(close-scope! m key holder))))))

(defn extension [m]
  (let [interpreter
        (reify ops/EffectInterpreter
          (interpret-effect! [_ _ context {:keys [op args]} emit!]
            (case op
              :run (apply start-task! m (concat args [context emit!]))
              :clock
              (let [timer (Executors/newSingleThreadScheduledExecutor)
                    pulse (fn []
                            (let [tick (swap! (:tick m) inc)]
                              (doseq [callback (vals @(:subscribers m))] (callback tick))))]
                (count! m :clocks)
                (.scheduleWithFixedDelay ^ScheduledExecutorService timer ^Runnable pulse
                                         0 (long (* 1000 (first args))) TimeUnit/MILLISECONDS)
                (emit! (:clock-id m))
                (fn [] (.shutdownNow timer) (count! m :clock-cleanups) {:status :closed})))))]
    (files/prepare-extension!
     {:id :scheduler/map-task :root runtime-root
      :files {"runtime.lain" (slurp (str directory "/runtime.lain"))}
      :primitives {'clock:shared (effect interpreter :clock)
                   'task:run (effect interpreter :run)
                   'task:record (primitive (fn [id unit deadline] {:id id :unit unit :deadline deadline}))}})))

(defn close-manager! [m]
  (doseq [[key holder] @(:scopes m)] (close-scope! m key holder))
  (.shutdown ^ExecutorService (:retirer m))
  (when-not (.awaitTermination ^ExecutorService (:retirer m) 10 TimeUnit/SECONDS)
    (throw (ex-info "Retirement executor still active" {}))))

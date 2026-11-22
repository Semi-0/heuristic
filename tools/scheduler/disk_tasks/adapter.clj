(ns scheduler.disk-tasks.adapter
  "Disk queue and disposable scopes; scheduling decisions remain in Lain workers."
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [propagators.compiler-2.session :as session]
            [propagators.compiler-2.session.files :as files]
            [propagators.compiler-2.session.host :as host]
            [propagators.compiler-2.session.operators :as ops]
            [propagators.stdlib.source-publication :as publication])
  (:import [java.lang.ref WeakReference]
           [java.nio.file Files StandardCopyOption StandardOpenOption]
           [java.nio.channels FileChannel]
           [java.security MessageDigest]
           [java.util.concurrent Executors ScheduledExecutorService ExecutorService TimeUnit]))

(def directory "/home/lain/projects/lain-workspace/tools/scheduler/disk_tasks")
(def runtime-root "/home/lain/reports/xiaobao-takeover/20261114-r3/source-3c3f643")
(defn primitive [f] (ops/prim-op (fn [make] (make f))))
(defn effect [interpreter op]
  (ops/async-op interpreter (fn [make] (make (fn [& args] (ops/effect-output {:op op :args args}))))))

(defn key-of [task] [(:id task) (:unit task)])
(defn filename [key]
  (str (apply str (map #(format "%02x" (bit-and % 255))
                       (.digest (MessageDigest/getInstance "SHA-256") (.getBytes (json/write-str key) "UTF-8")))) ".json"))
(defn path [m area key] (io/file (:root m) area (filename key)))
(defn read-record [file] (when (.exists file) (json/read-str (slurp file) :key-fn keyword)))
(defn sync-directory! [directory]
  (with-open [dir (FileChannel/open (.toPath directory)
                                    (into-array java.nio.file.OpenOption [StandardOpenOption/READ]))]
    (.force dir true)))
(defn atomic-write! [file value]
  (let [tmp (io/file (.getParentFile file) (str "." (.getName file) "." (random-uuid)))]
    (try
      (with-open [out (java.io.FileOutputStream. tmp)]
        (.write out (.getBytes (json/write-str value) "UTF-8"))
        (.sync (.getFD out)))
      (Files/move (.toPath tmp) (.toPath file)
                  (into-array java.nio.file.CopyOption [StandardCopyOption/ATOMIC_MOVE StandardCopyOption/REPLACE_EXISTING]))
      (sync-directory! (.getParentFile file))
      (finally (Files/deleteIfExists (.toPath tmp))))))
(defn sample! [a value] (swap! a #(vec (take-last 16 (conj % value)))))
(defn task-hash [task] (filename (into (sorted-map) task)))
(defn accept! [m task]
  (when-not (and (string? (:id task)) (string? (:unit task)) (number? (:deadline task))
                 (number? (get task :priority 0)))
    (throw (ex-info "Task needs id, run unit, numeric deadline and priority" {})))
  (let [key (key-of task)]
    (if-let [old (or (read-record (path m "completed" key))
                     (read-record (path m "blocked" key))
                     (read-record (path m "active" key))
                     (read-record (path m "queued" key)))]
      (if (or (= task (:task old)) (= (task-hash task) (:task-hash old))) :duplicate
          (throw (ex-info "Task identity reused with changed payload" {:key key})))
      (do (atomic-write! (path m "queued" key) {:task task :queued-at (System/currentTimeMillis)}) :queued))))
(defn enqueue! [m task]
  ;; Producers write immutable commands only. The managed effect owns queue state.
  (let [id (str (random-uuid)) command (io/file (:root m) "inbox" (str id ".json"))
        reply (io/file (:root m) "replies" (str id ".json"))
        end (+ (System/currentTimeMillis) 30000)]
    (atomic-write! command {:op "enqueue" :task task})
    (loop []
      (if-let [result (read-record reply)]
        (do (Files/deleteIfExists (.toPath reply))
            (if (:error result) (throw (ex-info (:error result) {})) (keyword (:status result))))
        (if (> (System/currentTimeMillis) end)
          (throw (ex-info "Queue acknowledgment pending; do not resubmit with a new identity" {:command id}))
          (do (Thread/sleep 20) (recur)))))))

(defn manager
  "IO is injected; no poll/alert policy here. Weak references are diagnostics only."
  [root io!]
  (doseq [sub ["queued" "active" "completed" "archive" "blocked" "inbox" "replies"]] (.mkdirs (io/file root sub)))
  {:root root :max-active 4 :renew-seconds 900 :recover? false :io! io! :scopes (atom {}) :subscribers (atom {})
   :clock-id (str (random-uuid)) :tick (atom 0) :metrics (atom {}) :weak (atom [])
   :networks (atom []) :persist! atomic-write! :errors (atom []) :retirer (Executors/newSingleThreadScheduledExecutor)})
(defn count! [m key] (swap! (:metrics m) update key (fnil inc 0)))

(defn close-scope! [m key holder]
  (locking holder
    (when-let [owner @holder]
      ;; Do not discard a scope if cleanup fails.
      (sample! (:networks m) (WeakReference. (session/network (host/current-session owner))))
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
        (.exists (path m "completed" key))
        (do (emit! (read-record (path m "completed" key))) ((:complete! context)) nil)

        (contains? @(:scopes m) key)
        (do (emit! {:status "already-active" :task key}) ((:complete! context)) nil)

        :else
        (let [owner (host/open! (session/create-session))
              holder (atom owner) ready (promise) finishing (atom false)
              expires (+ (/ (System/currentTimeMillis) 1000.0) (:renew-seconds m))
              interpreter
              (reify ops/EffectInterpreter
                (interpret-effect! [_ _ child-context {:keys [op args]} child-emit!]
                  (case op
                    :subscribe
                    (let [id (random-uuid)
                          callback #(child-emit! (publication/active [:shared-clock id] % (/ (System/currentTimeMillis) 1000.0)))]
                      (swap! (:subscribers m) assoc id callback)
                      (count! m :subscriptions)
                      (when (pos? @(:tick m)) (callback @(:tick m)))
                      (fn [] (swap! (:subscribers m) dissoc id)
                        (count! m :unsubscriptions) {:status :closed}))

                    (:finish :renew)
                    (do
                      (when (compare-and-set! finishing false true)
                        (.submit ^ExecutorService (:retirer m)
                                 ^Runnable
                                 (fn []
                                   @ready
                                   (try
                                     (locking scope-lock
                                       (if (= op :renew)
                                         (do
                                           (close-scope! m key holder)
                                           (atomic-write! (path m "queued" key)
                                                          {:task task :queued-at (System/currentTimeMillis)})
                                           (count! m :renewed))
                                         (do
                                           (when-not (= true (:acknowledged (first args)))
                                             (throw (ex-info "Completion requires acknowledged receipt" {})))
                                           ((:persist! m) (path m "archive" key)
                                                          {:task task :receipt (first args) :status :completed})
                                           ((:persist! m) (path m "completed" key)
                                                          {:task-hash (task-hash task) :status :completed
                                                           :archive (.getName (path m "archive" key))})
                                           (close-scope! m key holder)))
                                       (Files/deleteIfExists (.toPath (path m "active" key))))
                                     (emit! (first args))
                                     ((:complete! context))
                                     (catch Throwable e (sample! (:errors m) (Throwable->map e)))))))
                      nil)

                    (let [result ((:io! m) op args)]
                      (child-emit! result)
                      ((:complete! child-context))
                      nil))))]
          (swap! (:scopes m) assoc key holder)
          (sample! (:weak m) (WeakReference. owner))
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
                            'task:renew (effect interpreter :renew)
                            'task:expires (primitive (fn [] expires))
                            'queue:observe (effect interpreter :observe)
                            'codex:notify (effect interpreter :notify)
                            'queue:acknowledge (effect interpreter :acknowledge)}}))
            (catch Throwable e
              (close-scope! m key holder)
              (throw e))
            (finally (deliver ready true)))
          #(close-scope! m key holder))))))

(defn each-file! [m area f]
  (with-open [entries (Files/newDirectoryStream (.toPath (io/file (:root m) area)) "*.json")]
    (doseq [entry (iterator-seq (.iterator entries))] (f (.toFile entry)))))
(defn move! [m from to key]
  (Files/move (.toPath (path m from key)) (.toPath (path m to key))
              (into-array java.nio.file.CopyOption [StandardCopyOption/ATOMIC_MOVE StandardCopyOption/REPLACE_EXISTING]))
  (sync-directory! (io/file (:root m) to))
  (sync-directory! (io/file (:root m) from)))
(defn recover! [m]
  (each-file! m "active"
              (fn [file]
                (let [record (read-record file) task (:task record) key (key-of task)]
                  (cond
                    (.exists (path m "completed" key)) (Files/deleteIfExists (.toPath file))
                    (.exists (path m "archive" key))
                    (let [saved (read-record (path m "archive" key))]
                      (when-not (= true (get-in saved [:receipt :acknowledged]))
                        (throw (ex-info "Archive without acknowledged receipt" {:key key})))
                      (atomic-write! (path m "completed" key)
                                     {:task-hash (task-hash task) :status :completed
                                      :archive (.getName (path m "archive" key))})
                      (Files/deleteIfExists (.toPath file)))
                    (:safe ((:io! m) :reconcile [task])) (move! m "active" "queued" key)
                    :else (move! m "active" "blocked" key))))))
(defn inbox! [m]
  (each-file! m "inbox"
              (fn [file]
                (let [reply (io/file (:root m) "replies" (.getName file))
                      result (try
                               (let [{:keys [op task confirmed-message-id]} (read-record file)]
                                 (case op
                                   "enqueue" {:status (accept! m task)}
                                   "reconcile" (let [key (key-of task)]
                                                 (when-not (= task (:task (read-record (path m "blocked" key))))
                                                   (throw (ex-info "Blocked task identity mismatch" {})))
                                                 (when confirmed-message-id
                                                   ((:io! m) :confirm [task confirmed-message-id]))
                                                 (when-not (:safe ((:io! m) :reconcile [task]))
                                                   (throw (ex-info "Delivery still ambiguous; verify submission first" {})))
                                                 (move! m "blocked" "queued" key)
                                                 {:status :reconciled})
                                   (throw (ex-info "Unknown queue command" {}))))
                               (catch Throwable e {:error (ex-message e)}))]
                  (atomic-write! reply result)
                  (Files/deleteIfExists (.toPath file))))))
(defn next-task [m]
  (with-open [entries (Files/newDirectoryStream (.toPath (io/file (:root m) "queued")) "*.json")]
    (reduce (fn [best entry]
              (let [r (read-record (.toFile entry))
                    rank [(- (get-in r [:task :priority] 0)) (:queued-at r) (str entry)]]
                (if (or (nil? best) (neg? (compare rank (:rank best))))
                  {:task (:task r) :rank rank} best)))
            nil (iterator-seq (.iterator entries)))))
(defn quarantine-failures! [m]
  (doseq [[key holder] @(:scopes m)]
    (when-let [owner @holder]
      (when-let [error (first (host/failures owner))]
        (sample! (:errors m) (Throwable->map error))
        (close-scope! m key holder)
        (move! m "active" "blocked" key)))))
(defn start-runner! [m clock module queue emit!]
  (when-not (= queue (:root m)) (throw (ex-info "Unknown queue capability" {})))
  (let [channel (FileChannel/open (.toPath (io/file (:root m) "runner.lock"))
                                  (into-array java.nio.file.OpenOption [StandardOpenOption/CREATE StandardOpenOption/WRITE]))
        lease (try (.tryLock channel) (catch Throwable e (.close channel) (throw e)))]
    (when-not lease (.close channel) (throw (ex-info "Queue already owned" {})))
    (let [timer (:retirer m) initialized (atom false)
          poll (fn []
                 (try
                   (when-not @initialized
                     (when (:recover? m) (recover! m))
                     (reset! initialized true))
                   (inbox! m)
                   (quarantine-failures! m)
                   (when (< (count @(:scopes m)) (:max-active m))
                     (when-let [{:keys [task]} (next-task m)]
                       (let [key (key-of task)]
                         (cond
                           (.exists (path m "completed" key)) (Files/deleteIfExists (.toPath (path m "queued" key)))
                           (.exists (path m "active" key)) (move! m "queued" "blocked" key)
                           :else (do (move! m "queued" "active" key)
                                     (start-task! m clock module task {:complete! (fn [])} (fn [_])))))))
                   (catch Throwable e (sample! (:errors m) (Throwable->map e)))))]
      (.scheduleWithFixedDelay ^ScheduledExecutorService timer ^Runnable poll 0 100 TimeUnit/MILLISECONDS)
      (emit! {:status "running" :queue queue})
      (fn []
        (.shutdown ^ExecutorService timer)
        (when-not (.awaitTermination ^ExecutorService timer 60 TimeUnit/SECONDS)
          (throw (ex-info "Queue writer still running" {})))
        (doseq [[key holder] @(:scopes m)] (close-scope! m key holder))
        (.release lease)
        (.close channel)
        {:status :closed}))))

(defn extension [m]
  (let [interpreter
        (reify ops/EffectInterpreter
          (interpret-effect! [_ _ _ {:keys [op args]} emit!]
            (case op
              :run (apply start-runner! m (concat args [emit!]))
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
     {:id :scheduler/disk-tasks :root runtime-root
      :files {"runtime.lain" (slurp (str directory "/runtime.lain"))}
      :primitives {'clock:shared (effect interpreter :clock)
                   'queue:open (primitive (fn [] (:root m)))
                   'map-tasks (effect interpreter :run)}})))

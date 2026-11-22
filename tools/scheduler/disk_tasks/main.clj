(ns scheduler.disk-tasks.main)
(load-file "/home/lain/projects/lain-workspace/tools/scheduler/disk_tasks/adapter.clj")
(require '[scheduler.disk-tasks.adapter :as a]
         '[clojure.data.json :as json]
         '[clojure.java.io :as io]
         '[propagators.compiler-2.session :as s]
         '[propagators.compiler-2.session.host :as host])
(import '[java.util.concurrent TimeUnit])
(def config (json/read-str (slurp (first *command-line-args*)) :key-fn keyword))
(defn execute! [op args]
  (let [process (.start (ProcessBuilder. ^java.util.List
                         ["python3" (str a/directory "/io.py") (:io-root config)]))]
    (with-open [writer (io/writer (.getOutputStream process))]
      (.write writer (json/write-str {:op op :args args})))
    (when-not (.waitFor process 75 TimeUnit/SECONDS)
      (.destroyForcibly process)
      (throw (ex-info "IO timeout: inspect persisted delivery claim" {:op op})))
    (let [out (slurp (.getInputStream process)) err (slurp (.getErrorStream process))]
      (when-not (zero? (.exitValue process)) (throw (ex-info "Task IO failed" {:op op :stderr err})))
      (json/read-str out :key-fn keyword))))
(def m (assoc (a/manager (:queue-root config) execute!)
              :recover? true :renew-seconds (get config :renew-seconds 900)
              :max-active (get config :max-active 4)))
(def owner (host/open! (s/create-session) {:cleanup-timeout-ms 90000}))
(def stop (promise))
(.addShutdownHook (Runtime/getRuntime) (Thread. ^Runnable #(do (deliver stop true) (host/close! owner))))
(try
  (host/extend! owner (a/extension m))
  (host/extend! owner (s/session-extension
                       {:id :scheduler/start
                        :files {"start.lain" (str "(define clock (clock:shared " (get config :poll-seconds 15)
                                                  ")) (define scheduler (runtime clock (queue:open)))")}}))
  (loop []
    (when-let [e (first (host/failures owner))] (throw e))
    ;; Diagnostic snapshots only. All queue writes and policy execute elsewhere.
    (a/atomic-write! (io/file (:queue-root config) "status.json")
                     {:at (System/currentTimeMillis) :status :running
                      :parent-nodes (count (:graph (s/network (host/current-session owner))))
                      :parent-receipts (count (host/receipts owner))
                      :active (count @(:scopes m)) :subscribers (count @(:subscribers m))
                      :ticks @(:tick m) :metrics @(:metrics m) :errors @(:errors m)})
    (when (= ::waiting (deref stop 1000 ::waiting)) (recur)))
  (finally (host/close! owner)))
(shutdown-agents)

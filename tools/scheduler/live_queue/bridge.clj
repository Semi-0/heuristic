(ns scheduler.live-queue.bridge
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [propagators.compiler-2.session.files :as files]
            [propagators.compiler-2.session.operators :as ops]
            [propagators.stdlib.source-publication :as publication])
  (:import [java.util.concurrent Executors ScheduledExecutorService TimeUnit]))

(def directory "/home/lain/projects/lain-workspace/tools/scheduler/live_queue")
(def runtime-root "/home/lain/reports/xiaobao-takeover/20261114-r3/source-3c3f643")
(defn primitive [f] (ops/prim-op (fn [make] (make f))))
(defn async-op [interpreter op]
  (ops/async-op interpreter
                (fn [make] (make (fn [& args] (ops/effect-output {:op op :args args}))))))

(defn execute! [root op args]
  (let [process (.start (ProcessBuilder. ^java.util.List
                         ["python3" (str directory "/io.py") root]))]
    (with-open [writer (io/writer (.getOutputStream process))]
      (.write writer (json/write-str {:op op :args args})))
    (when-not (.waitFor process 75 TimeUnit/SECONDS)
      (.destroy process)
      (throw (ex-info "IO timed out; inspect saved claim before retry" {:op op})))
    (let [output (slurp (.getInputStream process)) error (slurp (.getErrorStream process))]
      (when-not (zero? (.exitValue process))
        (throw (ex-info "IO failed" {:op op :stderr error})))
      (json/read-str output :key-fn keyword))))

(defn extension [root metrics last-callback]
  (let [interpreter
        (reify ops/EffectInterpreter
          (interpret-effect! [_ _ context {:keys [op args]} emit!]
            (swap! metrics update op (fnil inc 0))
            (if (= op :clock)
              (let [timer (Executors/newSingleThreadScheduledExecutor)
                    revision (atom 0)
                    pulse (fn []
                            (let [n (swap! revision inc)]
                              (swap! metrics update :ticks (fnil inc 0))
                              (emit! (publication/active [:clock (:id context)] n
                                                         (/ (System/currentTimeMillis) 1000.0)))))]
                (reset! last-callback pulse)
                (.scheduleWithFixedDelay ^ScheduledExecutorService timer ^Runnable pulse
                                         0 (long (* 1000 (first args))) TimeUnit/MILLISECONDS)
                (fn []
                  (.shutdownNow timer)
                  (swap! metrics update :clock-cleanup (fnil inc 0))
                  {:status :closed}))
              (let [result (execute! root (name op) args)]
                (emit! result)
                ((:complete! context))
                nil))))]
    (files/prepare-extension!
     {:id :scheduler/real-capabilities :root runtime-root
      :files {"runtime.lain" (slurp (str directory "/runtime.lain"))}
      :primitives {'field (primitive (fn [record key] (get record (keyword key))))
                   'task:record (primitive (fn [id unit deadline thread]
                                             {:id id :unit unit :deadline deadline :thread thread}))
                   'clock:every (async-op interpreter :clock)
                   'queue:observe (async-op interpreter :observe)
                   'codex:notify (async-op interpreter :notify)
                   'queue:acknowledge (async-op interpreter :acknowledge)}})))

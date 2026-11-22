(ns scheduler.managed.main
  (:refer-clojure :exclude [run!])
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [propagators.compiler-2.session :as session]
            [propagators.compiler-2.session.host :as host]
            [propagators.compiler-2.session.operators :as ops]
            [propagators.stdlib.source-publication :as publication])
  (:import [java.util.concurrent Executors ScheduledExecutorService TimeUnit]))

(defn primitive [f] (ops/prim-op (fn [make] (make f))))
(defn capability [interpreter op]
  (ops/async-op interpreter (fn [make] (make (fn [& args] (ops/effect-output {:op op :args args}))))))

(defn io-effect [directory op args]
  (let [[job input] args
        process (.start (ProcessBuilder. ^java.util.List ["python3" (str directory "/effects.py") op]))]
    (with-open [writer (io/writer (.getOutputStream process))]
      (.write writer (json/write-str {:job job :input input})))
    (when-not (.waitFor process 75 TimeUnit/SECONDS)
      (.destroy process)
      (throw (ex-info "Effect timed out; inspect durable claim before retry" {:op op})))
    (let [out (slurp (.getInputStream process)) err (slurp (.getErrorStream process))]
      (when-not (zero? (.exitValue process))
        (throw (ex-info "IO capability failed" {:op op :stderr err})))
      (json/read-str out :key-fn keyword))))

(defn extension
  "Pure declaration plus explicitly injected managed capabilities. No preseeded cells."
  [directory job effect-fn clock-factory stopped events]
  (let [timer (atom nil)
        interpreter
        (reify ops/EffectInterpreter
          (interpret-effect! [_ _ context {:keys [op args]} emit!]
            (swap! events conj {:op op :request-id (:id context)})
            (case op
              :clock
              (let [[seconds maximum] args
                    close (clock-factory
                           seconds maximum
                           (fn [n]
                             (when-not ((:cancelled? context))
                               (emit! (publication/active [:scheduler/clock (:id context)] n n)))))]
                (reset! timer close)
                (fn [] (close) {:status :closed}))
              :stop
              (do
                (when-let [close @timer] (close))
                (emit! {:status :closed})
                ((:complete! context))
                (deliver stopped {:status :closed :receipt (first args)})
                nil)
              (let [result (try (effect-fn (name op) args)
                                (catch Exception error
                                  (deliver stopped {:status :failed :error (ex-message error) :details (ex-data error)})
                                  (throw error)))]
                (emit! result)
                ((:complete! context))
                nil))))]
    (session/session-extension
     {:id :scheduler/managed-v2
      :primitives {'job:configuration (primitive (fn [] job))
                   'field (primitive (fn [record key] (get record (keyword key))))
                   'clock:every (capability interpreter :clock)
                   'clock:stop (capability interpreter :stop)
                   'queue:observe (capability interpreter :observe)
                   'queue:start (capability interpreter :start)
                   'codex:wake (capability interpreter :wake)}
      :files {"runtime.lain" (slurp (str directory "/runtime.lain"))}})))

(defn clock! [seconds maximum emit!]
  (let [executor (Executors/newSingleThreadScheduledExecutor)
        sequence (atom 0)
        task (fn [] (let [n (swap! sequence inc)]
                      (when (<= n maximum) (emit! n))))]
    (.scheduleWithFixedDelay ^ScheduledExecutorService executor ^Runnable task
                             0 (long (* 1000 seconds)) TimeUnit/MILLISECONDS)
    (fn [] (.shutdownNow executor))))

(defn run! [directory job effect-fn clock-factory timeout-ms]
  (let [stopped (promise) events (atom [])
        owner (host/open! (session/create-session {:id (random-uuid) :run-id (random-uuid) :propagation-options {:max-steps 100000 :timeout-ms 30000}}))]
    (try
      (host/extend! owner (extension directory job effect-fn clock-factory stopped events))
      (let [result (deref stopped timeout-ms {:status :timeout})]
        {:result result :events @events
         :failures (mapv ex-message (host/failures owner))})
      (finally (host/close! owner)))))

(when (= "main" (second *command-line-args*))
  (let [[config-path _ directory] *command-line-args*
        job (json/read-str (slurp config-path) :key-fn keyword)
        outcome (run! directory job #(io-effect directory %1 %2) clock!
                      (long (* 1000 (+ 120 (* (:poll_seconds job) (:max_ticks job))))))]
    (spit (str (:state_dir job) "/managed-result.json") (json/write-str outcome))
    (shutdown-agents)
    (System/exit (if (= :closed (get-in outcome [:result :status])) 0 1))))

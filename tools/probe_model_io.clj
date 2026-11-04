(load-file (str (System/getenv "LAIN_HARNESS_ROOT") "/tools/offline_uuid.clj"))
(require '[lain-harness.model-io-session :as setup]
         '[propagators.compiler-2.session :as session]
         '[propagators.compiler-2.session.connected :as connected]
         '[propagators.compiler-2.session.host :as host])

(when-not (= ["--live"] (vec *command-line-args*))
  (throw (ex-info "Pass --live to send one bounded greeting to the local Blazux endpoint" {})))

(let [owner (setup/open! (System/getenv "LAIN_HARNESS_ROOT") "model-refined.lain"
                         {:timeout-ms 30000 :max-tokens 128})
      started (System/nanoTime)]
  (try
    ;; Exercise the same source-extension entry point used by the editor.
    (host/extend! owner
                  (session/session-extension
                   {:id :model-io-live-prompt
                    :files {"model-io-live-prompt.lain"
                            "(define live-output (model:output m))
                             (-> \"Hi. Reply with one short greeting.\" (model:input m))"}}))
    (let [deadline (+ (System/currentTimeMillis) 45000)]
      (loop []
        (when-let [error (first (host/failures owner))] (throw error))
        (let [output (connected/payload (session/binding-value (host/current-session owner) 'live-output))]
          (cond
            (= :reply (:kind output))
            (do (assert (and (string? (:text output)) (seq (:text output))) "Expected a nonempty reply")
                (prn {:status :passed :model "qwen3.8-flash-next"
                      :elapsed-ms (/ (- (System/nanoTime) started) 1000000.0)
                      :output output}))
            (= :error (:kind output)) (throw (ex-info "Live model failed" output))
            (> (System/currentTimeMillis) deadline) (throw (ex-info "Live model timed out" {}))
            :else (do (Thread/sleep 20) (recur))))))
    (finally (prn {:cleanup (host/close! owner)}) (shutdown-agents))))

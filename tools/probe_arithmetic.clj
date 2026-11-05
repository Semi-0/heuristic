(load-file (str (System/getenv "LAIN_HARNESS_ROOT") "/tools/offline_uuid.clj"))
(require '[lain-harness.soul-session :as soul]
         '[lain-harness.blazux :as blazux]
         '[propagators.compiler-2.session :as session]
         '[propagators.compiler-2.session.host :as host]
         '[propagators.compiler-2.session.connected :as connected]
         '[propagators.network :as net])
(let [root (System/getenv "LAIN_HARNESS_ROOT")
      evidence "/home/lain/projects/lain-workspace/runtime/arithmetic-attempt-1/"
      _ (.mkdirs (java.io.File. evidence))
      provider (blazux/completion-transport {:timeout-ms 600000 :max-tokens 4096 :enable-thinking true :reasoning-effort "medium" :thinking-token-budget 2048})
      {:keys [owner journal]} (soul/open! root {:transport (fn [request]
                                                           (spit (str evidence "request.edn") (pr-str (dissoc request :cancelled?)))
                                                           (spit (str evidence "context.txt") (:text request))
                                                           (let [response (provider request)]
                                                             (spit (str evidence "response.edn") (pr-str response))
                                                             (spit (str evidence "generated.lain") (:text response))
                                                             response))})]
  (try
    (let [deadline (+ (System/currentTimeMillis) 660000)]
      (loop []
        (when-let [error (first (host/failures owner))] (throw error))
        (let [current (host/current-session owner)
              diagnostic (connected/payload (session/binding-value current 'observe-cell))]
          (cond
            (= :rejected (:status diagnostic)) (throw (ex-info "Arithmetic source rejected" diagnostic))
            (= :admitted (:status diagnostic))
            (let [target (:id (connected/payload (session/binding-value current 'generated-environment)))
                  cell (session/scoped-binding-cell current target 'arithmetic-result)
                  value (connected/payload (net/network-cell-strongest (session/network current) cell))
                  result {:status (if (= 126 value) :passed :failed) :value value :expected 126
                          :edge (soul/edge-view (session/network current) cell) :admissions @journal}]
              (spit (str evidence "result.edn") (pr-str result))
              (prn result)
              (assert (= 126 value)))
            (> (System/currentTimeMillis) deadline) (throw (ex-info "Arithmetic probe timed out" {}))
            :else (do (Thread/sleep 30) (recur))))))
    (finally (spit (str evidence "soul-source.lain") (slurp (str root "/soul-refined.lain")))
             (prn {:cleanup (host/close! owner)})
             (shutdown-agents))))

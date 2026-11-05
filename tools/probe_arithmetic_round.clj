(load-file (str (System/getenv "LAIN_HARNESS_ROOT") "/tools/offline_uuid.clj"))
(require '[clojure.string :as str]
         '[lain-harness.soul-session :as soul]
         '[lain-harness.model-io-session :as model]
         '[lain-harness.blazux :as blazux]
         '[propagators.compiler-2.session :as session]
         '[propagators.compiler-2.session.host :as host]
         '[propagators.compiler-2.session.connected :as connected])
(let [root (System/getenv "LAIN_HARNESS_ROOT")
      controlled? (= "1" (System/getenv "LAIN_CONTROLLED"))
      evidence (str "/home/lain/projects/lain-workspace/runtime/arithmetic-round-" (if controlled? "control" "1") "/")
      _ (.mkdirs (java.io.File. evidence))
      journal (atom []) requests (atom [])
      owner (host/open! (model/compiler))
      provider (blazux/completion-transport {:timeout-ms 600000 :max-tokens 4096 :enable-thinking true :reasoning-effort "medium" :thinking-token-budget 2048})
      read-value #(connected/payload (session/binding-value (host/current-session owner) %))
      publish (fn [name v] (host/publish-input! owner (fn [current] [(session/publication-input (session/binding-cell current name) v)])) (host/step! owner))
      ask "How much is (- 20 (* 100 30))?"
      expected (if controlled? 7 -2980)]
  (try
    (host/extend! owner (soul/extension owner journal))
    (host/extend! owner (model/declaration root "soul-refined.lain"
                                         {:transport (fn [request]
                                                       (swap! requests conj (dissoc request :cancelled?))
                                                       (spit (str evidence "context.txt") (:text request))
                                                       (let [reply (if controlled?
                                                                     {:kind :reply :text "(-> 7 ((slot :result) observed)) (-> \"7\" user-output)" :thinking "" :finish-reason "stop" :truncated? false}
                                                                     (provider request))]
                                                         (spit (str evidence "response.edn") (pr-str reply))
                                                         (spit (str evidence "generated.lain") (:text reply)) reply))}))
    (publish 'ask ask)
    (assert (= [{:role "user" :content ask}] (read-value 'conversation-history)))
    (publish 'evaluate-trigger true)
    (let [deadline (+ (System/currentTimeMillis) 660000)]
      (loop []
        (when-let [error (first (host/failures owner))] (throw error))
        (let [admission (read-value 'admission-result)]
          (cond
            (= :rejected (:status admission)) (throw (ex-info "Source rejected" admission))
            (and (= :admitted (:status admission)) (= 2 (count (read-value 'conversation-history))))
            (let [result {:status :passed :controlled? controlled? :ask ask :result (read-value 'result-cell)
                          :reply (read-value 'user-output) :history (read-value 'conversation-history) :admissions @journal :requests @requests}]
              (assert (= expected (:result result)))
              (assert (str/includes? (:reply result) (str expected)))
              (assert (= [{:role "user" :content ask} {:role "assistant" :content (:reply result)}] (:history result)))
              (assert (= 1 (count @requests)))
              (spit (str evidence "result.edn") (pr-str result))
              (prn (dissoc result :requests :admissions)))
            (> (System/currentTimeMillis) deadline) (throw (ex-info "Round timed out" {:admission admission}))
            :else (do (Thread/sleep 30) (recur))))))
    (finally (spit (str evidence "source.lain") (slurp (str root "/soul-refined.lain")))
             (prn {:cleanup (host/close! owner)}) (shutdown-agents))))

(load-file (str (System/getenv "LAIN_HARNESS_ROOT") "/tools/offline_uuid.clj"))
(ns probe-soul
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [lain-harness.environment :as environment]
            [lain-harness.value-metadata :as metadata]
            [lain-harness.soul-session :as soul]
            [lain-harness.blazux :as blazux]
            [propagators.compiler-2.session :as session]
            [propagators.compiler-2.session.connected :as connected]
            [propagators.compiler-2.session.host :as host]))

(def root (System/getenv "LAIN_HARNESS_ROOT"))
(def evidence-dir (or (System/getenv "LAIN_SOUL_EVIDENCE_DIR") (str root "/runtime")))
(.mkdirs (io/file evidence-dir))
(def requests (atom []))
(def responses (atom []))
(def provider (blazux/completion-transport {:timeout-ms 600000 :max-tokens 16384 :enable-thinking (not= "0" (System/getenv "LAIN_SOUL_THINKING")) :reasoning-effort "medium" :thinking-token-budget 2048}))
(defn observed [owner binding]
  (connected/payload (session/binding-value (host/current-session owner) binding)))
(defn await!
  ([owner predicate] (await! owner predicate 90000))
  ([owner predicate timeout-ms]
   (let [deadline (+ (System/currentTimeMillis) timeout-ms)]
     (loop []
       (when-let [error (first (host/failures owner))] (throw error))
       (cond (predicate) true
             (> (System/currentTimeMillis) deadline) (throw (ex-info "Soul probe timed out" {}))
             :else (do (Thread/sleep 30) (recur)))))))
(let [{:keys [owner journal deliveries]} (soul/open! root {:transport (fn [request]
                                                                        (swap! requests conj (dissoc request :cancelled?))
                                                                        (spit (str evidence-dir "/soul-requests.edn") (pr-str @requests))
                                                                        (let [reply (try (provider request)
                                                                                         (catch Exception error
                                                                                           (spit (str evidence-dir "/soul-provider-error.edn")
                                                                                                 (pr-str {:type (.getName (class error)) :message (ex-message error)}))
                                                                                           (throw error)))]
                                                                          (swap! responses conj (assoc reply :request-id (:id request)))
                                                                          (spit (str evidence-dir "/soul-last-response.edn") (pr-str reply))
                                                                          (spit (str evidence-dir "/soul-response-" (count @responses) ".lain") (:text reply))
                                                                          reply))})]
  (try
    (await! owner #(or (seq @journal) (= :rejected (:status (observed owner 'observe-cell)))) 660000)
    (await! owner #(#{:admitted :rejected} (:status (observed owner 'observe-cell))))
    (spit (str evidence-dir "/soul-generated-orientation.lain") (apply str (map :source @journal)))
    (prn {:setup (observed owner 'observe-cell) :journal @journal})
    (when-not (= :admitted (:status (observed owner 'observe-cell)))
      (throw (ex-info "Construction failed; see environment diagnostics" {:code-history (observed owner 'code-history-cell)
                                                                          :diagnostics (observed owner 'observe-cell)})))
    (await! owner #(let [result (observed owner 'orientation-result-cell)]
                     (and (map? result) (number? (:value result)) (= (:value result) (get-in result [:edge :value]))
                          (contains? (:edge result) :inputs) (contains? (:edge result) :outputs))) 10000)
    (prn {:orientation (observed owner 'orientation-result-cell)})
    (soul/advance-context! owner)
    (await! owner #(= "construction" (:response (observed owner 'observe-cell))) 660000)
    (when-not (= :admitted (:status (observed owner 'observe-cell)))
      (throw (ex-info "Construction stage failed" {:diagnostics (observed owner 'observe-cell)})))
    (spit (str evidence-dir "/soul-generated-setup.lain") (apply str (map :source @journal)))
    (let [context-requests (count @requests)]
      (doseq [[index text] (map-indexed vector ["hi" "how are you?" "do you remember my last sentence?"])]
        (let [message {:id (str "user-" (inc index)) :time (inc (* 2 index)) :role "user" :content text}
              before (count @deliveries)
              before-requests (count @requests)]
          (host/publish-user-message!
           owner {:interface [:user "conversation"]
                  :run-id (:run-id (session/session-identity (host/current-session owner)))
                  :id (:id message) :text text})
          (await! owner #(> (count @requests) before-requests) 10000)
          (await! owner #(> (count @deliveries) before))
          (await! owner #(and (= :admitted (:status (observed owner 'observe-cell)))
                              (= (:id (last @requests)) (:request-id (observed owner 'model-result))
                                 (:response (observed owner 'observe-cell)))))
          (prn {:round (inc index) :delivery (last @deliveries)})))
      (assert (str/includes? (str/lower-case (:content (last @deliveries))) "how are you"))
      (let [conversation (drop context-requests @requests)]
        (assert (= 3 (count conversation)))
        (doseq [round (range 3)]
          (let [messages (remove #(= "system" (:role %)) (:messages (nth conversation round)))
                expected (concat
                          (mapcat (fn [i]
                                    [{:role "user" :content (nth ["hi" "how are you?" "do you remember my last sentence?"] i)}
                                     (select-keys (nth @deliveries i) [:role :content])]) (range round))
                          [{:role "user" :content (nth ["hi" "how are you?" "do you remember my last sentence?"] round)}])]
            (assert (= (vec expected) (mapv #(select-keys % [:role :content]) messages))))))
      (assert (= 1 (count (filter #(= 'io-model:receive (nth (key %) 2)) (host/receipts owner)))))
      (prn {:status :passed}))
    (catch Exception error
      (host/publish-input! owner
                           (fn [current]
                             [(session/publication-input
                               (session/binding-cell current 'experiment-diagnostic-cell)
                               {:status :failed :message (ex-message error)})]))
      (throw error))
    (finally
      (let [current (host/current-session owner)
            network (session/network current)
            target (:id (observed owner 'generated-environment))]
        (spit (str evidence-dir "/soul-network.edn")
              (pr-str {:bindings (environment/local-bindings network target)
                       :edges (into {} (map (fn [[name reference]]
                                              [name (soul/edge-view network reference)]))
                                    (merge (environment/local-bindings network target)
                                           (into {} (map (fn [name] [name (session/binding-cell current name)]))
                                                 '[user-ingress model-result code-history-cell observe-cell])))
                       :memory (metadata/slots network [(connected/payload (session/binding-value current 'memory-cell))])})))
      (spit (str evidence-dir "/soul-experiment.edn")
            (pr-str {:sources (select-keys (session/source-files (host/current-session owner))
                                           ["soul-refined.lain" "examples/lain/conversation.lain" "environments/conversation.lain" "experiments/conversation-bootstrap.lain" "lib/compiler.lain" "model-refined.lain" "lib/behavior-core.lain" "lib/language-context.lain"])
                     :requests @requests :responses @responses :admissions @journal :delivered @deliveries
                     :code-history (observed owner 'code-history-cell)
                     :diagnostics (observed owner 'observe-cell)}))
      (prn {:cleanup (host/close! owner)})
      (shutdown-agents))))

(load-file (str (System/getenv "LAIN_HARNESS_ROOT") "/tools/offline_uuid.clj"))
(require '[clojure.edn :as edn] '[clojure.string :as str]
         '[lain-harness.soul-session :as soul] '[lain-harness.model-io-session :as model]
         '[lain-harness.blazux :as blazux] '[propagators.compiler-2.session :as session]
         '[propagators.compiler-2.session.host :as host]
         '[propagators.compiler-2.session.connected :as connected] '[propagators.network :as net])
(defn input-ancestors [network start]
  (loop [pending [start] seen #{}]
    (if-let [id (peek pending)]
      (if (contains? seen id) (recur (pop pending) seen)
          (recur (into (pop pending) (:inputs (get (net/net-graph network) id))) (conj seen id)))
      seen)))
(let [root (System/getenv "LAIN_HARNESS_ROOT")
      evidence (str (or (System/getenv "LAIN_EVIDENCE_DIR") (str root "/runtime/arithmetic-reuse-1")) "/")
      _ (.mkdirs (java.io.File. evidence))
      journal (atom []) requests (atom []) responses (atom []) rounds (atom [])
      owner (host/open! (model/compiler))
      provider (blazux/completion-transport {:timeout-ms 600000 :max-tokens 4096 :enable-thinking true :reasoning-effort "medium" :thinking-token-budget 2048})
      read-value #(connected/payload (session/binding-value (host/current-session owner) %))
      publish (fn [name v] (host/publish-input! owner (fn [current] [(session/publication-input (session/binding-cell current name) v)])) (host/step! owner))]
  (try
    (host/extend! owner (soul/extension owner journal))
    (host/extend! owner (model/declaration root "soul-refined.lain"
                                           {:transport (fn [request]
                                                         (swap! requests conj (dissoc request :cancelled?))
                                                         (spit (str evidence (:id request) "-context.txt") (:text request))
                                                         (let [reply (provider request)]
                                                           (swap! responses conj reply)
                                                           (spit (str evidence (:id request) ".lain") (:text reply)) reply))}))
    (let [identity (session/session-identity (host/current-session owner))
          target (:id (read-value 'generated-environment))]
      (doseq [[i ask expected] [[1 "How much is (- 20 (* 100 30))?" -2980]
                                [2 "Divide your previous result by 20." -149]
                                [3 "Add 150 to that." 1]]]
        (publish 'ask-event {:id (str "user-" i) :round i :time (dec (* 2 i)) :content ask})
        (assert (= (dec (* 2 i)) (count (read-value 'conversation-history))))
        (publish (symbol (str "trigger-" i)) true)
        (let [deadline (+ (System/currentTimeMillis) 660000)]
          (loop []
            (when-let [error (first (host/failures owner))] (throw error))
            (let [admission (read-value 'admission-result)]
              (cond
                (and (= (str "round-" i) (:response admission)) (= :rejected (:status admission)))
                (throw (ex-info "Generated source rejected" admission))
                (and (= (str "round-" i) (:response admission)) (= :admitted (:status admission))
                     (= (* 2 i) (count (read-value 'conversation-history))))
                (let [current (host/current-session owner)
                      network (session/network current)
                      result-name (symbol (str "result-" i))
                      ref (session/scoped-binding-cell current target result-name)
                      actual (connected/payload (net/network-cell-strongest network ref))
                      observed (read-value (symbol (str "observed-value-" i)))
                      reply (read-value (symbol (str "user-output-" i)))
                      forms (edn/read-string (str "[" (:text (last @responses)) "]"))
                      definition (first (filter #(and (seq? %) (= 'define (first %)) (= result-name (second %))) forms))
                      previous (symbol (str "result-" (dec i)))
                      source-reuse (or (= i 1) (some #{previous} (tree-seq coll? seq (nth definition 2 nil))))
                      graph-reuse (or (= i 1) (contains? (input-ancestors network ref) (session/scoped-binding-cell current target previous)))
                      result {:round i :ask ask :value actual :observed observed :reply reply
                              :source-reuses-previous (boolean source-reuse) :graph-reuses-previous graph-reuse
                              :edge (soul/edge-view network ref) :history (read-value 'conversation-history)}]
                  (swap! rounds conj result)
                  (prn result) (flush)
                  (assert (= expected actual observed))
                  (assert (str/includes? reply (str expected)))
                  (assert source-reuse "Generated expression must reference the previous result cell")
                  (assert graph-reuse "Compiled inputs must connect to the previous result cell")
                  (assert (= identity (session/session-identity current)))
                  (assert (= target (:id (read-value 'generated-environment))))
                  (assert (= i (count @requests))))
                (> (System/currentTimeMillis) deadline) (throw (ex-info "Round timed out" {:round i :admission admission}))
                :else (do (Thread/sleep 30) (recur)))))))
      (prn {:status :passed :rounds 3}))
    (finally
      (spit (str evidence "evidence.edn") (pr-str {:rounds @rounds :requests @requests :responses @responses :admissions @journal
                                                   :history (read-value 'conversation-history) :written-code (read-value 'written-code)}))
      (spit (str evidence "source.lain") (slurp (str root "/soul-refined.lain")))
      (prn {:cleanup (host/close! owner)}) (shutdown-agents))))

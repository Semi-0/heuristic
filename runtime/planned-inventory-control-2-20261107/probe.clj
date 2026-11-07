(load-file (str (System/getenv "LAIN_HARNESS_ROOT") "/tools/offline_uuid.clj"))
(require '[clojure.edn :as edn] '[clojure.string :as str]
         '[lain-harness.soul-session :as soul] '[lain-harness.premise-experiment :as premise]
         '[lain-harness.model-io-session :as model] '[lain-harness.blazux :as blazux]
         '[lain-harness.value-metadata :as metadata]
         '[propagators.compiler-2.runtime.application :as application]
         '[propagators.compiler-2.session :as session] '[propagators.compiler-2.session.host :as host]
         '[propagators.compiler-2.session.connected :as connected]
         '[propagators.datastructures.support-collection :as collection] '[propagators.network :as net])

(def questions
  ["A shipment contains 20 boxes of 12 items. We need to fulfill an order for 200 items. The damaged percentage has not been reported yet. What can we determine now, and what remains unresolved about the usable stock and whether we can fulfill the order?"
   "A different shipment contains 15 boxes of 24 items. Inspection confirms that 5% are damaged. Can this shipment fulfill an order for 350 items? What is the usable stock and the surplus or shortage?"])
(def quantities ["total" "usable" "margin" "fulfillment"])
(defn port [q i] (symbol (str "observed-" q "-" i)))
(defn input-ancestors [graph start]
  (loop [pending [start] seen #{}]
    (if-let [id (peek pending)]
      (if (contains? seen id) (recur (pop pending) seen)
          (recur (into (pop pending) (:inputs (get graph id))) (conj seen id))) seen)))
(defn forms [source] (edn/read-string (str "[" source "]")))
(defn definitions [source]
  (map second (filter #(and (seq? %) (= 'define (first %))) (forms source))))

;; Controlled sources exercise the same admission, runtime, graph and support evaluator.
;; They are never supplied to a real-model request.
(defn control-source [mode i]
  (let [f (if (and (= i 2) (not= mode "reuse")) "stock2" "stock1")
        q (if (and (= mode "cross") (= i 2)) "question-1" (str "question-" i))
        make-f? (or (= i 1) (not= mode "reuse"))
        partial? (not (and (= i 1) (= mode "disconnected")))]
    (str
     (when (and (= mode "copied") (= i 2))
       "(define unused-call (stock1 boxes-2 items-per-box-2 damage-percent-2))\n")
     (when make-f? (str "(define " f " (ttms-premise-closure (network [b n d] (/ (* (* b n) (- 100 d)) 100)) shared-rule))\n"))
     "(define total" i " ((ttms-premise-closure (ttms-premise-closure (network [b n] (* b n)) shared-rule) " q ") boxes-" i " items-per-box-" i "))\n"
     "(-> total" i " observed-total-" i ")\n"
     (if partial?
       (str "(define usable" i " ((ttms-premise-closure " f " " q ") boxes-" i " items-per-box-" i " damage-percent-" i "))\n"
            "(define margin" i " (- usable" i " order-quantity-" i "))\n"
            "(define fill" i " (>= margin" i " 0))\n"
            "(-> usable" i " observed-usable-" i ")\n(-> margin" i " observed-margin-" i ")\n(-> fill" i " observed-fulfillment-" i ")\n")
       "(define open-usable)\n(-> open-usable observed-usable-1)\n")
     (if (= i 1)
       "(-> (str \"Total=\" total1 \"; damage unknown; usable and fulfillment unresolved.\") user-output-1)\n"
       "(-> (str \"Usable=\" usable2 \"; margin=\" margin2 \"; fulfillment=\" fill2) user-output-2)\n"))))

(defn trial! [root evidence control]
  (.mkdirs (java.io.File. evidence))
  (let [base (model/compiler)
        compiler (session/create-session
                  (assoc (session/session-identity base)
                         :native-extensions (:bootstrap (session/session-declarations base))
                         :runtime-options (:options (session/session-declarations base))
                         :propagation-options {:max-steps 300000 :timeout-ms 180000}))
        owner (host/open! compiler) journal (atom []) requests (atom []) responses (atom []) phase-responses (atom []) analyses (atom [])
        snapshots (atom []) rounds (atom []) input-names (atom {}) outcome (atom {})
        provider (blazux/completion-transport {:timeout-ms 600000 :max-tokens 4096 :enable-thinking true :reasoning-effort "medium" :thinking-token-budget 2048})
        read-value #(connected/payload (session/binding-value (host/current-session owner) %))
        publish (fn [name epoch status base]
                  (let [ref (session/binding-cell (host/current-session owner) name)]
                    (host/submit! owner (session/cell-input ref (collection/content
                                                                 {:base base :support #{{:source ref :timestamp epoch :premises-status status}}})))
                    (host/step! owner)))
        plain (fn [name v]
                (host/publish-input! owner (fn [current] [(session/publication-input (session/binding-cell current name) v)]))
                (host/step! owner))]
    (try
      (host/extend! owner (soul/extension owner journal))
      (host/extend! owner (premise/facilities))
      (when (= "1" (System/getenv "LAIN_INSPECTION"))
        (host/extend! owner ((requiring-resolve 'lain-harness.inspection-experiment/facilities))))
      (host/extend! owner (model/declaration root "soul-refined.lain"
                                             {:transport (fn [request]
                                                           (let [i (count (swap! requests conj (dissoc request :cancelled?)))
                                                                 analysis? (str/starts-with? (:id request) "analysis-")
                                                                 round-number (Integer/parseInt (last (str/split (:id request) #"-")))]
                                                             (spit (str evidence "/context-" i ".txt") (:text request))
                                                             (let [reply (if control {:kind :reply :text (if analysis?
                                                                                                           (pr-str {:outputs "Inventory observations and reply" :facts "Current shipment inputs" :unknowns "Damage may be pending" :units "items and percent"
                                                                                                                    :relations "Stock and order relationships" :operators (if (= round-number 1) "No earlier callable" "stock1 is available")
                                                                                                                    :operator "stock1" :status "Controlled candidate tested separately" :decision (if (= round-number 1) :construct :reuse)
                                                                                                                    :steps "Connect computation and output ports" :tests "Independent known arithmetic cases"})
                                                                                                           (control-source "reuse" round-number)) :thinking "" :finish-reason "stop" :truncated? false}
                                                                             (provider request))]
                                                               (swap! phase-responses conj reply)
                                                               (when-not analysis? (swap! responses conj reply))
                                                               (spit (str evidence "/" (if analysis? "analysis-" "generated-") round-number (if analysis? ".edn" ".lain")) (:text reply))
                                                               (spit (str evidence "/response-" i ".edn") (pr-str reply)) reply)))}))
      (let [identity (session/session-identity (host/current-session owner))
            target (:id (read-value 'generated-environment))
            inputs (concat '[shared-rule delivery-note] (for [i [1 2] n ["boxes" "items-per-box" "damage-percent" "order-quantity" "question"]] (symbol (str n "-" i))))
            by-id (into {} (for [n inputs] [(session/binding-cell (host/current-session owner) n) n]))
            _ (reset! input-names by-id)
            snapshot (fn [stage]
                       (when-let [e (first (host/failures owner))] (throw e))
                       (let [current (host/current-session owner) network (session/network current) graph (net/net-graph network)
                             results (into {} (for [i [1 2] n (conj (mapv #(port % i) quantities) (symbol (str "user-output-" i)))]
                                                (let [ref (session/binding-cell current n) view (premise/inspect-value network ref)]
                                                  [n (assoc view :reference ref :inputs (:inputs (get graph ref))
                                                            :support-names (set (map #(get by-id (:source %) (:source %)) (:support view)))
                                                            :input-ancestors (set (filter (set (concat (keys by-id) (map :cell (soul/visible-facts network (read-value 'generated-environment))))) (input-ancestors graph ref))))])))
                             entry {:stage stage :identity (session/session-identity current) :child (:id (read-value 'generated-environment))
                                    :worker (into {} (read-value 'experiment-worker)) :request-count (count @requests) :results results
                                    :child-bindings (into {} (for [n (distinct (mapcat #(keys (:bindings %)) @rounds))]
                                                               (let [ref (session/scoped-binding-cell current target n)
                                                                     v (connected/payload (net/network-cell-strongest network ref))
                                                                     view (premise/inspect-value network ref)]
                                                                 [n (assoc view :reference ref :value (if (= :callable (metadata/kind v)) :callable (:value view))
                                                                           :kind (metadata/kind v)
                                                                           :support-names (set (map #(get by-id (:source %) (:source %)) (:support view))))])))}]
                         (swap! snapshots conj entry)
                         (spit (str evidence "/snapshots.edn") (pr-str @snapshots))
                         (println stage (into {} (map (fn [[k v]] [k (:value v)]) results))) (flush)
                         entry))
            request-round (fn [i]
                            (plain 'ask-event {:id (str "user-" i) :round i :time (dec (* 2 i)) :content (nth questions (dec i))})
                            (plain 'implementation-id (str "round-" i))
                            (plain 'phase "analysis")
                            (plain 'analysis-record "No analysis has been submitted for this question yet.")
                            (plain (symbol (str "analysis-trigger-" i)) true)
                            (let [deadline (+ (System/currentTimeMillis) 660000)]
                              (loop []
                                (when-let [e (first (host/failures owner))] (throw e))
                                (cond
                                  (= (str "analysis-" i) (:request-id (read-value 'raw-model-result))) true
                                  (> (System/currentTimeMillis) deadline) (throw (ex-info "Analysis timed out" {:round i}))
                                  :else (do (Thread/sleep 30) (recur)))))
                            (let [reply (last @phase-responses)
                                  _ (when-not (and (= :reply (:kind reply)) (= "stop" (:finish-reason reply)) (not (:truncated? reply)))
                                      (throw (ex-info "Incomplete analysis response" {:round i :finish-reason (:finish-reason reply)})))
                                  raw (:text (last @phase-responses))
                                  record (edn/read-string raw)
                                  _ (when-not (and (map? record)
                                                   (every? #(and (string? (get record %)) (not (str/blank? (get record %))))
                                                           [:outputs :facts :unknowns :units :relations :operators :operator :status :steps :tests])
                                                   (#{:reuse :adapt :construct} (:decision record)))
                                      (throw (ex-info "Incomplete analysis record" {:round i :record record})))
                                  current (host/current-session owner)
                                  network (session/network current)
                                  candidate (first (filter #(= (:name %) (:operator record))
                                                           (soul/visible-facts network (read-value 'generated-environment))))
                                  callable? (= :callable (metadata/kind (:value candidate)))
                                  entry {:round i :record record :selected-reference (:cell candidate)
                                         :selected-callable callable? :request-count (count @requests)
                                         :identity (session/session-identity current) :child target}]
                              (swap! analyses conj entry)
                              (spit (str evidence "/analyses.edn") (pr-str @analyses))
                              (when (and (#{:reuse :adapt} (:decision record)) (not callable?))
                                (throw (ex-info "Selected existing operator is not an available callable" entry)))
                              (plain 'analysis-record raw))
                            ;; Generic phase transition, with no evaluator or task-specific feedback.
                            (plain 'phase "implementation")
                            (plain (symbol (str "trigger-" i)) true)
                            (let [deadline (+ (System/currentTimeMillis) 660000)]
                              (loop []
                                (when-let [e (first (host/failures owner))] (throw e))
                                (let [a (read-value 'admission-result)]
                                  (cond
                                    (and (= (str "round-" i) (:response a)) (= :rejected (:status a))) (throw (ex-info "Source rejected" a))
                                    (and (= (str "round-" i) (:response a)) (= :admitted (:status a))) true
                                    (> (System/currentTimeMillis) deadline) (throw (ex-info "Timed out" {:round i :admission a}))
                                    :else (do (Thread/sleep 30) (recur))))))
                            (let [current (host/current-session owner) network (session/network current)
                                  bindings (into {} (for [n (definitions (:text (last @responses)))]
                                                      (let [ref (session/scoped-binding-cell current target n)
                                                            v (connected/payload (net/network-cell-strongest network ref))]
                                                        [n {:reference ref :kind (metadata/kind v)
                                                            :declaration (get v application/declaration-id-key)
                                                            :captured-environment (get v application/captured-environment-key)
                                                            :edge (select-keys (soul/edge-view network ref) [:inputs :outputs])}])))]
                              (swap! rounds conj {:round i :bindings bindings :history (read-value 'conversation-history)
                                                  :applications (mapv (fn [a] (let [v (connected/payload (net/network-cell-strongest network (:operator-id a)))]
                                                                                (assoc a :declaration (get v application/declaration-id-key)
                                                                                       :captured-environment (get v application/captured-environment-key))))
                                                                      (application/application-topologies network))
                                                  :written-code (read-value 'written-code) :admission (read-value 'admission-result)})
                              ;; Store the actual graph topology; no executable JVM objects are serialized.
                              (spit (str evidence "/graph-" i ".edn")
                                    (pr-str (into {} (map (fn [[k v]] [k (select-keys v [:inputs :outputs])]) (net/net-graph network)))))))]
        (publish 'shared-rule 1 :active "Validity premise for generated mathematical rules")
        (publish 'delivery-note 1 :active "Tuesday")
        (doseq [[n v] {'boxes-1 20 'items-per-box-1 12 'order-quantity-1 200 'question-1 (first questions)}] (publish n 1 :active v))
        (request-round 1)
        (snapshot :before-damage)
        (publish 'damage-percent-1 1 :active 10)
        (snapshot :damage-published)
        (doseq [[n v] {'boxes-2 15 'items-per-box-2 24 'damage-percent-2 5 'order-quantity-2 350 'question-2 (second questions)}] (publish n 1 :active v))
        (request-round 2)
        (snapshot :question-2)
        (publish 'damage-percent-2 2 :active 2.5)
        (snapshot :corrected)
        (publish 'question-1 2 :retracted (first questions))
        (snapshot :question-1-withdrawn)
        (publish 'question-1 3 :active (first questions))
        (snapshot :question-1-restored)
        (publish 'damage-percent-2 3 :retracted 2.5)
        (snapshot :damage-2-withdrawn)
        (publish 'damage-percent-2 4 :active 2.5)
        (snapshot :damage-2-restored)
        (publish 'delivery-note 2 :active "Friday")
        (snapshot :unrelated-change)
        (let [current (host/current-session owner)
              callable-checks (into {} (for [[n b] (:bindings (first @rounds)) :when (= :callable (:kind b))]
                                         (let [ref (session/scoped-binding-cell current target n)
                                               v (connected/payload (net/network-cell-strongest (session/network current) ref))
                                               calls (filter #(and (seq? %) (= n (first %))) (tree-seq coll? seq (forms (:text (second @responses)))))
                                       ;; A wrapper invocation also uses the original callable; require runtime result ancestry as well.
                                               mentions (some #{n} (tree-seq coll? seq (forms (:text (second @responses)))))
                                               connected-ports (vec (for [q quantities :let [p (port q 2)]
                                                                          :when (contains? (get-in (nth @snapshots 2) [:results p :input-ancestors]) ref)] p))]
                                           [n {:same-reference (= ref (:reference b))
                                               :same-declaration (= (:declaration b) (get v application/declaration-id-key))
                                               :same-captured-environment (= (:captured-environment b) (get v application/captured-environment-key))
                                               :direct-calls (vec calls) :source-reference (boolean mentions)
                                               :connected-answer-ports connected-ports
                                               :new-invocations (vec (filter (fn [a]
                                                                               (and (some? (:declaration b))
                                                                                    (= (:declaration b) (:declaration a))
                                                                                    (= (:captured-environment b) (:captured-environment a))
                                                                                    (not (contains? (set (map :application-id (:applications (first @rounds)))) (:application-id a)))))
                                                                             (:applications (second @rounds))))}])))]
          (reset! outcome {:operational-status :completed :callable-checks callable-checks
                           :stable-identity (every? #(and (= identity (:identity %)) (= target (:child %))) @snapshots)})))
      (catch Throwable e
        (swap! outcome assoc :operational-status :failed :error (.getMessage e) :data (ex-data e))
        (println "Operational failure:" (.getMessage e)))
      (finally
        (spit (str evidence "/evidence.edn") (pr-str {:controlled control :replay-source-dir (System/getenv "LAIN_REPLAY_SOURCE_DIR") :outcome @outcome :requests @requests :responses @responses :phase-responses @phase-responses :analyses @analyses
                                                      :input-names @input-names :admissions @journal :snapshots @snapshots :rounds @rounds
                                                      :history (try (read-value 'conversation-history) (catch Throwable _ :unavailable))
                                                      :written-code (try (read-value 'written-code) (catch Throwable _ :unavailable))}))
        (spit (str evidence "/parent.lain") (slurp (str root "/soul-refined.lain")))
        (prn {:cleanup (host/close! owner)})))
    @outcome))

(let [root (System/getenv "LAIN_HARNESS_ROOT") evidence (System/getenv "LAIN_EVIDENCE_DIR") control (not-empty (System/getenv "LAIN_CONTROL"))]
  (prn (trial! root evidence control))
  (shutdown-agents))

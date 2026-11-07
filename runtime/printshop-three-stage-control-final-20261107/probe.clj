(load-file (str (System/getenv "LAIN_HARNESS_ROOT") "/tools/offline_uuid.clj"))
(require '[clojure.edn :as edn] '[clojure.string :as str]
         '[lain-harness.soul-session :as soul] '[lain-harness.premise-experiment :as premise]
         '[lain-harness.model-io-session :as model] '[lain-harness.blazux :as blazux]
         '[lain-harness.value-metadata :as metadata]
         '[propagators.compiler-2.runtime.application :as application]
         '[propagators.compiler-2.session :as session] '[propagators.compiler-2.session.host :as host]
         '[propagators.compiler-2.session.connected :as connected]
         '[propagators.cells.value :as value] '[propagators.experimental.ttms-primitives :as primitives] '[propagators.propagator :as prop]
         '[propagators.datastructures.support-collection :as collection] '[propagators.network :as net])

(def questions
  ["Quote 80 copies for a nonmember using standard service. Show the charges and final total."
   "Quote 240 copies for a member using rush service. Show the charges and final total."
   "Quote 720 copies for a member using standard service. Show the charges and final total."
   "Quote 90 copies for a member using rush service. Show the charges and final total."
   "For this order only, a promotion raises the member discount cap to $75. Quote 720 copies for a member using rush service. All other rules stay unchanged. Show the charges and final total."])
(def quantities ["production" "rush" "discount" "subtotal" "delivery" "tax" "total"])
(def orders [[80 false false 40] [240 true true 40] [720 false true 40] [90 true true 40] [720 true true 75]])
(def pricing '{tier-one-limit 100 tier-two-count 400 tier-one-price 2 tier-two-price 1.60M tier-three-price 1.30M rush-rate 0.20M member-rate 0.10M setup-fee 25 delivery-fee 12 free-delivery-threshold 250 tax-rate 0.08M})
(defn port [q i] (symbol (str "observed-" q "-" i)))
(defn generic-facilities []
  (session/session-extension {:id ::print-generics
                              :primitives (into {} (for [[n f] {'print:min min 'print:max max 'print:round-money
                                                                (fn [x] (.setScale (java.math.BigDecimal/valueOf (double x)) 2 java.math.RoundingMode/HALF_UP))}]
                                                     [n (primitives/scalar-operator n (prop/primitive-propagator n (fn [& xs] (if-let [bad (first (filter value/unusable? xs))] bad (apply f xs)))))]))}))
(defn input-ancestors [graph start]
  (loop [pending [start] seen #{}]
    (if-let [id (peek pending)]
      (if (contains? seen id) (recur (pop pending) seen)
          (recur (into (pop pending) (:inputs (get graph id))) (conj seen id))) seen)))
(defn forms [source] (edn/read-string (str "[" source "]")))
(defn definitions [source]
  (map second (filter #(and (seq? %) (= 'define (first %))) (forms source))))

(defn control-source [_ i]
  (str (when (= i 1) (slurp (str (System/getenv "LAIN_HARNESS_ROOT") "/test/fixtures/printshop/quote.lain")))
       "(define q" i " ((ttms-premise-closure quote-fn question-" i ") order-" i " pricing))\n"
       (apply str (for [q quantities] (str "(-> ((slot :" q ") q" i ") ((slot :" q ") quote-" i "))\n")))
       "(-> (str \"Total: \" ((slot :total) q" i ")) user-output-" i ")\n"
       "(-> (= ((slot :total) q" i ") (round-money (+ ((slot :subtotal) q" i ") ((slot :delivery) q" i ") ((slot :tax) q" i ")))) self-check-" i ")\n"))

(defn trial! [root evidence control]
  (.mkdirs (java.io.File. evidence))
  (let [base (model/compiler)
        compiler (session/create-session
                  (assoc (session/session-identity base)
                         :native-extensions (:bootstrap (session/session-declarations base))
                         :runtime-options (:options (session/session-declarations base))
                         :propagation-options {:max-steps 300000 :timeout-ms 180000}))
        owner (host/open! compiler) journal (atom []) requests (atom []) responses (atom []) analyses (atom []) generalizations (atom []) implementations (atom [])
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
      (host/extend! owner (generic-facilities))
      (host/extend! owner (model/declaration root "soul-refined.lain"
                                             {:transport (fn [request]
                                                           (let [i (count (swap! requests conj (dissoc request :cancelled?)))
                                                                 planning? (not (str/starts-with? (:id request) "round-"))
                                                                 stage (first (str/split (:id request) #"-"))]
                                                             (spit (str evidence "/context-" i ".txt") (:text request))
                                                             (let [reply (if control {:kind :reply :text
                                                                                      (let [round-number (Integer/parseInt (last (str/split (:id request) #"-")))]
                                                                                        (if planning?
                                                                                          (pr-str {:outputs "Quote breakdown" :facts "Current order and pricing" :unknowns "none" :units "copies and currency" :relations "Stated progressive pricing and charges"
                                                                                                   :operators "Available arithmetic and retained quote callable" :operator (if (and (= stage "analysis") (= round-number 2)) "none" "quote-fn")
                                                                                                   :decision (cond (= round-number 1) :construct (and (= stage "analysis") (= round-number 2)) :compose :else :reuse)
                                                                                                   :status "Known controlled interface" :steps "Apply interface and connect outputs" :tests "Host verifies independent known cases"
                                                                                                   :problem-class "Quotes under varying orders and pricing" :parameters "order and pricing structs" :captured "Shared rule validity premise" :interface "quote-fn takes order and pricing and returns a breakdown struct"})
                                                                                          (control-source control round-number))) :thinking "" :finish-reason "stop" :truncated? false}
                                                                             (provider request))]
                                                               (swap! responses conj reply)
                                                               (spit (str evidence "/" (if planning? (str stage "-") "generated-")
                                                                          (last (str/split (:id request) #"-"))
                                                                          (if planning? ".edn" ".lain")) (:text reply))
                                                               (spit (str evidence "/response-" i ".edn") (pr-str reply)) reply)))}))
      (let [identity (session/session-identity (host/current-session owner))
            target (:id (read-value 'generated-environment))
            inputs (concat (keys pricing) '[shared-rule delivery-note] (for [i (range 1 6) n ["quantity" "rush" "member" "discount-cap" "question"]] (symbol (str n "-" i))))
            by-id (into {} (for [n inputs] [(session/binding-cell (host/current-session owner) n) n]))
            _ (reset! input-names by-id)
            snapshot (fn [stage]
                       (when-let [e (first (host/failures owner))] (throw e))
                       (let [current (host/current-session owner) network (session/network current) graph (net/net-graph network)
                             results (into {} (for [i (range 1 6) n (into (mapv #(port % i) quantities) [(symbol (str "user-output-" i)) (symbol (str "self-check-" i))])]
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
                                                                 [n (assoc view :reference ref :value (if (#{:callable :compound} (metadata/kind v)) (metadata/kind v) (:value view))
                                                                           :kind (metadata/kind v)
                                                                           :support-names (set (map #(get by-id (:source %) (:source %)) (:support view))))])))}]
                         (swap! snapshots conj entry)
                         (spit (str evidence "/snapshots.edn") (pr-str @snapshots))
                         (println stage (into {} (map (fn [[k v]] [k (:value v)]) results))) (flush)
                         entry))
            request-round (fn [i]
                            (plain 'ask-event {:id (str "user-" i) :round i :time (dec (* 2 i)) :content (nth questions (dec i))})
                            (plain 'implementation-id (str "round-" i))
                            (plain 'analysis-record "No analysis submitted for this question yet.")
                            (plain 'generalization-record "No interface decision submitted for this question yet.")
                            (doseq [stage ["analysis" "generalization"]]
                              (plain 'phase stage)
                              (plain (symbol (str stage "-trigger-" i)) true)
                              (let [deadline (+ (System/currentTimeMillis) 660000)]
                                (loop []
                                  (when-let [e (first (host/failures owner))] (throw e))
                                  (cond
                                    (= (str stage "-" i) (:request-id (read-value 'raw-model-result))) true
                                    (> (System/currentTimeMillis) deadline) (throw (ex-info "Analysis timed out" {:round i}))
                                    :else (do (Thread/sleep 30) (recur)))))
                              (let [reply (last @responses)
                                    _ (when-not (and (= :reply (:kind reply)) (= "stop" (:finish-reason reply)) (not (:truncated? reply)))
                                        (throw (ex-info "Incomplete analysis response" {:round i :finish-reason (:finish-reason reply)})))
                                    raw (:text (last @responses))
                                    record (edn/read-string raw)
                                    _ (when-not (and (map? record)
                                                     (every? #(and (string? (get record %)) (not (str/blank? (get record %))))
                                                             (concat [:outputs :facts :unknowns :units :relations :operators :operator :status :steps :tests]
                                                                     (when (= stage "generalization") [:problem-class :parameters :captured :interface])))
                                                     (#{:reuse :adapt :compose :construct} (:decision record)))
                                        (throw (ex-info "Incomplete analysis record" {:round i :record record})))
                                    current (host/current-session owner)
                                    network (session/network current)
                                    candidate (first (filter #(= (:name %) (:operator record))
                                                             (soul/visible-facts network (read-value 'generated-environment))))
                                    callable? (= :callable (metadata/kind (:value candidate)))
                                    entry {:round i :record record :selected-reference (:cell candidate)
                                           :selected-callable callable?
                                           :selection-validation (cond callable? :available-callable
                                                                       (= "none" (:operator record)) :no-single-callable-selected
                                                                       :else :unavailable-or-not-callable)
                                           :request-count (count @requests)
                                           :identity (session/session-identity current) :child target}]
                                (swap! (if (= stage "analysis") analyses generalizations) conj entry)
                                (spit (str evidence "/" stage "-records.edn") (pr-str @(if (= stage "analysis") analyses generalizations)))
                                (plain (if (= stage "analysis") 'analysis-record 'generalization-record) raw)))
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
                            (swap! implementations conj (last @responses))
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
        (doseq [[n v] pricing] (publish n 1 :active v))
        (publish 'shared-rule 1 :active "Fictional print-shop rules")
        (publish 'delivery-note 1 :active "Tuesday")
        (doseq [i (range 1 6)]
          (doseq [[n v] (map vector ["quantity" "rush" "member" "discount-cap"] (nth orders (dec i)))]
            (publish (symbol (str n "-" i)) 1 :active v))
          (publish (symbol (str "question-" i)) 1 :active (nth questions (dec i)))
          (request-round i)
          (snapshot (keyword (str "round-" i))))
        (publish 'quantity-1 2 :active 120)
        (snapshot :quantity-corrected)
        (publish 'discount-cap-5 2 :active 40)
        (snapshot :promotion-removed)
        (publish 'discount-cap-5 3 :active 75)
        (snapshot :promotion-restored)
        (publish 'quantity-2 2 :retracted 240)
        (snapshot :quantity-withdrawn)
        (publish 'quantity-2 3 :active 240)
        (snapshot :quantity-restored)
        (publish 'question-1 2 :retracted (first questions))
        (snapshot :question-withdrawn)
        (publish 'question-1 3 :active (first questions))
        (snapshot :question-restored)
        (publish 'delivery-note 2 :active "Friday")
        (snapshot :unrelated-change)
        (reset! outcome {:operational-status :completed
                         :stable-identity (every? #(and (= identity (:identity %)) (= target (:child %))) @snapshots)}))
      (catch Throwable e
        (swap! outcome assoc :operational-status :failed :error (.getMessage e) :data (ex-data e))
        (println "Operational failure:" (.getMessage e)))
      (finally
        (spit (str evidence "/evidence.edn") (pr-str {:controlled control :replay-source-dir (System/getenv "LAIN_REPLAY_SOURCE_DIR") :outcome @outcome :requests @requests :responses @implementations :phase-responses @responses :analyses @analyses :generalizations @generalizations
                                                      :input-names @input-names :admissions @journal :snapshots @snapshots :rounds @rounds
                                                      :history (try (read-value 'conversation-history) (catch Throwable _ :unavailable))
                                                      :written-code (try (read-value 'written-code) (catch Throwable _ :unavailable))}))
        (spit (str evidence "/parent.lain") (slurp (str root "/soul-refined.lain")))
        (prn {:cleanup (host/close! owner)})))
    @outcome))

(let [root (System/getenv "LAIN_HARNESS_ROOT") evidence (System/getenv "LAIN_EVIDENCE_DIR") control (not-empty (System/getenv "LAIN_CONTROL"))]
  (prn (trial! root evidence control))
  (shutdown-agents))

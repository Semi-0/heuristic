(load-file (str (System/getenv "LAIN_HARNESS_ROOT") "/tools/offline_uuid.clj"))
(require '[clojure.edn :as edn] '[clojure.string :as str]
         '[lain-harness.soul-session :as soul] '[lain-harness.premise-experiment :as premise]
         '[lain-harness.model-io-session :as model] '[lain-harness.blazux :as blazux]
         '[lain-harness.value-metadata :as metadata]
         '[lain-harness.experiment-observation :as observation]
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
  (let [provider-options {:timeout-ms 600000
                          :max-tokens (Long/parseLong (or (System/getenv "LAIN_MODEL_MAX_TOKENS") "4096"))
                          :enable-thinking true :reasoning-effort "medium"
                          :thinking-token-budget (Long/parseLong (or (System/getenv "LAIN_MODEL_THINKING_TOKENS") "2048"))}
        provider (blazux/completion-transport provider-options)
        base (model/compiler)
        compiler (session/create-session
                  (assoc (session/session-identity base)
                         :native-extensions (:bootstrap (session/session-declarations base))
                         :runtime-options (:options (session/session-declarations base))
                         :propagation-options {:max-steps 300000
                                               :timeout-ms (Long/parseLong (or (System/getenv "LAIN_PROPAGATION_TIMEOUT_MS") "180000"))}))
        owner (host/open! compiler) journal (atom []) requests (atom []) responses (atom []) analyses (atom []) generalizations (atom []) implementations (atom [])
        snapshots (atom []) rounds (atom []) recovery-attempts (atom []) feedbacks (atom []) round-sources (atom []) input-names (atom {}) outcome (atom {})
        read-value #(connected/payload (session/binding-value (host/current-session owner) %))
        publish-many (fn [entries]
                       (host/publish-input! owner
                                            (fn [current]
                                              (mapv (fn [[name epoch status base]]
                                                      (let [ref (session/binding-cell current name)]
                                                        (session/cell-input ref
                                                                            (collection/content
                                                                             {:base base :support #{{:source ref :timestamp epoch :premises-status status}}}))))
                                                    entries)))
                       (host/step! owner))
        publish (fn [name epoch status base] (publish-many [[name epoch status base]]))
        publish-question! (fn [event]
                            (host/publish-input! owner (fn [current] [(session/publication-input (session/binding-cell current 'ask-event) event)]))
                            (host/step! owner))]
    (try
      (spit (str evidence "/provider-options.edn") (pr-str provider-options))
      (add-watch journal ::record-admissions
                 (fn [_ _ _ entries]
                   (spit (str evidence "/admission-journal.edn") (pr-str entries))))
      (host/extend! owner (soul/extension owner journal))
      (host/extend! owner (premise/facilities))
      (host/extend! owner (generic-facilities))
      (host/extend! owner (observation/facilities))
      (host/extend! owner (model/declaration root (or (System/getenv "LAIN_EXPERIMENT_ENTRY") "soul-refined.lain")
                                             {:transport (fn [request]
                                                           (let [current (host/current-session owner)
                                                                 directory (mapv (fn [fact] {:title (:name fact) :reference (:cell fact)
                                                                                             :kind (if (:available fact) (metadata/kind (:value fact)) :unknown)})
                                                                                 (soul/visible-facts (session/network current) (read-value 'generated-environment)))
                                                                 i (count (swap! requests conj (assoc (dissoc request :cancelled?) :binding-directory directory)))
                                                                 planning? (not (str/starts-with? (:id request) "round-"))
                                                                 stage (first (str/split (:id request) #"-"))]
                                                             (spit (str evidence "/context-" i ".txt") (:text request))
                                                             (let [reply (if (= control "replay")
                                                                           (edn/read-string {:default tagged-literal} (slurp (str (System/getenv "LAIN_REPLAY_SOURCE_DIR") "/response-" i ".edn")))
                                                                           (if control {:kind :reply :text
                                                                                        (let [round-number (Integer/parseInt (second (str/split (:id request) #"-")))]
                                                                                          (if planning?
                                                                                            (pr-str {:outputs "Quote breakdown" :facts "Current order and pricing" :unknowns "none" :units "copies and currency" :relations "Stated progressive pricing and charges"
                                                                                                     :operators "Available arithmetic and retained quote callable" :operator (if (and (= stage "analysis") (= round-number 2)) "none" "quote-fn")
                                                                                                     :decision (cond (= round-number 1) :construct (and (= stage "analysis") (= round-number 2)) :compose :else :reuse)
                                                                                                     :status "Known controlled interface" :steps "Apply interface and connect outputs" :tests "Host verifies independent known cases"
                                                                                                     :problem-class "Quotes under varying orders and pricing" :parameters "order and pricing structs" :captured "Shared rule validity premise" :interface "quote-fn takes order and pricing and returns a breakdown struct"})
                                                                                            (if (and (= control "recovery-control") (= "round-1" (:id request))) "(define control-retained 7))" (control-source control round-number)))) :thinking "" :finish-reason "stop" :truncated? false}
                                                                               (provider request)))]
                                                               (spit (str evidence "/transport-" i ".edn")
                                                                     (pr-str {:request-id (:id request) :cancelled-before-return? ((:cancelled? request))}))
                                                               (swap! responses conj reply)
                                                               (spit (str evidence "/" (if planning? (str stage "-") "generated-")
                                                                          (if planning? (second (str/split (:id request) #"-")) (subs (:id request) 6))
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
            observe-round (fn [i]
                            (println :submit-question i) (flush)
                            (publish-question! {:id (str "user-" i) :round i :time (dec (* 2 i)) :content (nth questions (dec i))})
                            (let [deadline (+ (System/currentTimeMillis) (Long/parseLong (or (System/getenv "LAIN_OUTCOME_TIMEOUT_MS") "660000")))
                                  outcome (loop []
                                            (when-let [error (first (host/failures owner))] (throw error))
                                            (let [value (read-value (symbol (str "round-outcome-" i)))]
                                              (cond (#{:done :failed} (:status value)) value
                                                    (> (System/currentTimeMillis) deadline) (throw (ex-info "Lain outcome timed out" {:round i :observed value :raw-reply (let [v (read-value 'raw-model-result)] (if (map? v) (select-keys v [:request-id :kind :finish-reason :truncated?]) v)) :request (let [v (read-value 'request-input)] (if (map? v) (select-keys v [:id]) v)) :admission (read-value 'admission-result) :policy-edges (observation/binding-views (session/network (host/current-session owner)) (quote #{snapshot outgoing packet-ready construction-ready request-id a a-ok g g-ok r0 r1 r2 t0 t1 t2 reply raw parsed plan matching packet construction worker required trigger selected number requests outcome-target reply-target raw-reference receipt-reference environment-reference time context}))}))
                                                    :else (do (Thread/sleep 30) (recur)))))
                                  attempts (mapv #(assoc % :round i :observation (assoc (:observation %) :ready? (:ready? %))) (:attempts outcome))]
                              ;; Everything below records Lain's completed decisions; it schedules no stage or retry.
                              (doseq [[stage reply] [["analysis" (:analysis outcome)] ["generalization" (:generalization outcome)]] :when reply]
                                (let [record (edn/read-string (:text reply))
                                      candidate (first (filter #(= (:title %) (:operator record)) (:binding-directory (nth @requests (dec (inc (.indexOf (mapv :id @requests) (str stage "-" i))))))))
                                      callable? (= :callable (:kind candidate))
                                      entry {:round i :record record :selected-reference (:reference candidate) :selected-callable callable?
                                             :selection-validation (cond callable? :available-callable
                                                                         (= "none" (:operator record)) :no-single-callable-selected
                                                                         :else :unavailable-or-not-callable)
                                             :request-count (inc (.indexOf (mapv :id @requests) (str stage "-" i)))
                                             :identity (session/session-identity (host/current-session owner)) :child target}]
                                  (swap! (if (= stage "analysis") analyses generalizations) conj entry)))
                              (swap! recovery-attempts into attempts)
                              (swap! feedbacks into (:feedbacks outcome))
                              (doseq [[file value] [["analysis-records.edn" @analyses] ["generalization-records.edn" @generalizations]
                                                    ["recovery-attempts.edn" @recovery-attempts] ["recovery-feedback.edn" @feedbacks]]]
                                (spit (str evidence "/" file) (pr-str value)))
                              (when-not (= :done (:status outcome)) (throw (ex-info "Lain policy stopped the experiment" outcome)))
                              (swap! implementations conj (last @responses))
                              (let [current (host/current-session owner) network (session/network current)
                                    admitted-source (str/join "\n" (map :source (mapcat :accepted-forms attempts)))
                                    _ (swap! round-sources conj admitted-source)
                                    bindings (into {} (for [n (definitions admitted-source)]
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
                                                    :written-code (read-value 'written-code) :admission (read-value 'admission-result) :policy-edges (observation/binding-views (session/network (host/current-session owner)) (quote #{snapshot outgoing packet-ready construction-ready request-id a a-ok g g-ok r0 r1 r2 t0 t1 t2 reply raw parsed plan matching packet construction worker required trigger selected number requests outcome-target reply-target raw-reference receipt-reference environment-reference time context}))})
                                (spit (str evidence "/graph-" i ".edn")
                                      (pr-str (into {} (map (fn [[k v]] [k (select-keys v [:inputs :outputs])]) (net/net-graph network))))))))]
        (publish-many (concat (for [[n v] pricing] [n 1 :active v])
                              [['shared-rule 1 :active "Fictional print-shop rules"]
                               ['delivery-note 1 :active "Tuesday"]]))
        (println :pricing-inputs-published) (flush)
        (doseq [i (range 1 6)]
          (publish-many (concat (for [[n v] (map vector ["quantity" "rush" "member" "discount-cap"] (nth orders (dec i)))]
                                  [(symbol (str n "-" i)) 1 :active v])
                                [[(symbol (str "question-" i)) 1 :active (nth questions (dec i))]]))
          (observe-round i)
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
        (spit (str evidence "/evidence.edn") (pr-str {:controlled control :provider-options provider-options :replay-source-dir (System/getenv "LAIN_REPLAY_SOURCE_DIR") :outcome @outcome :requests @requests :responses @implementations :phase-responses @responses :analyses @analyses :generalizations @generalizations
                                                      :recovery-attempts @recovery-attempts :recovery-feedback @feedbacks :round-sources @round-sources
                                                      :input-names @input-names :admissions @journal :snapshots @snapshots :rounds @rounds
                                                      :history (try (read-value 'conversation-history) (catch Throwable _ :unavailable))
                                                      :written-code (try (read-value 'written-code) (catch Throwable _ :unavailable))}))
        (spit (str evidence "/parent.lain") (slurp (str root "/soul-refined.lain")))
        (prn {:cleanup (host/close! owner)})))
    @outcome))

(let [root (System/getenv "LAIN_HARNESS_ROOT") evidence (System/getenv "LAIN_EVIDENCE_DIR") control (not-empty (System/getenv "LAIN_CONTROL"))]
  (prn (trial! root evidence control))
  (shutdown-agents))

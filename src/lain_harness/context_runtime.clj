(ns lain-harness.context-runtime
  "Context IO and measurement boundaries. Stage selection and retries live in Lain."
  (:require [lain-harness.connected-data :as cd]
            [lain-harness.context-store :as store]
            [propagators.compiler-2.session :as s]
            [propagators.compiler-2.session.host :as h]
            [propagators.compiler-2.session.operators :as op]
            [propagators.compiler-2.session.connected :as connected]
            [propagators.cells.value :as value]
            [propagators.network :as net]))

(defn facilities [catalog count-tokens validate-result]
  (let [interpreter
        (reify op/EffectInterpreter
          (interpret-effect! [_ owner context {:keys [operation arguments]} emit!]
            (let [cancelled (atom false)
                  finished (atom false)
                  job (future
                        (try
                          (let [result
                                (case operation
                                  :tokens (let [n (count-tokens (first arguments))]
                                            (when-not (nat-int? n)
                                              (throw (ex-info "Tokenizer returned no valid count" {})))
                                            {:status :counted :tokens n})
                                  :validate (let [result (apply validate-result arguments)
                                                  phase (second arguments)
                                                  receipts (get-in (nth arguments 2) [:result :receipts])]
                                              (if (and (= phase :index) (= :valid (:status result))
                                                       (not (every? #(and (keyword? (:id %))
                                                                          (:catalog/claim-chunk (store/entity catalog (:id %))))
                                                                    receipts)))
                                                (assoc result :status :invalid :diagnostic "Index receipts must identify stored evidence-backed claims.")
                                                result))
                                  :archive (let [[id kind v] arguments]
                                             (store/archive! catalog id kind (pr-str v)))
                                  :observe
                                  (let [[reference receipt] arguments
                                        deadline (+ (System/currentTimeMillis) 3000)]
                                    (loop []
                                      (when (or @cancelled ((:cancelled? context)))
                                        (throw (InterruptedException. "Observation cancelled")))
                                      (let [v (net/network-cell-strongest
                                               (s/network (h/current-session owner)) reference)]
                                        (cond
                                          (not= :admitted (:status receipt))
                                          {:status :rejected :diagnostic receipt}
                                          (not (value/unusable? v))
                                          {:status :observed :reference reference :result (cd/unpack v)}
                                          (> (System/currentTimeMillis) deadline)
                                          {:status :missing-output :diagnostic "stage-result remained unresolved after admission"}
                                          :else (do (Thread/sleep 10) (recur)))))))]
                            (when-not (= :accepted (emit! result))
                              (throw (ex-info "Context IO result was not accepted" {:operation operation}))))
                          (catch Exception e
                            (emit! {:status :error :diagnostic (ex-message e)}))
                          (finally (reset! finished true) ((:complete! context)))))]
              (fn [] (reset! cancelled true) (when-not @finished (future-cancel job)) {:status :closed}))))]
    (s/session-extension
     {:id ::facilities
      :primitives
      (into {'round-money (connected/primitive 'round-money (fn [n] (.setScale (bigdec n) 2 java.math.RoundingMode/HALF_UP)))
             'min (connected/primitive 'min min) 'max (connected/primitive 'max max)
             'context:id (connected/primitive 'context:id (fn [id suffix] (keyword "evidence" (str id "/" suffix))))
             'context:text (connected/primitive 'context:text pr-str)}
            (for [[name operation] {'context:tokens :tokens 'context:validate :validate
                                    'context:archive-value :archive 'context:observe :observe}]
              [name (op/async-op interpreter
                                 (fn [make]
                                   (make (fn [& arguments]
                                           (op/effect-output {:operation operation :arguments arguments})))))]))})))

(defn validator
  "An independent numeric oracle, never exposed in a model environment."
  [expected]
  (fn [task phase observed]
    (let [r (:result observed)
          decision (:decision r)
          valid (and (= :observed (:status observed))
                     (if (= phase :index)
                       (and (= decision :indexed) (seq (:receipts r))
                            (every? #(= :indexed (:status %)) (:receipts r)))
                       (case decision
                         :retrieve (contains? r :evidence)
                         :unresolved (string? (:reason r))
                         :done (and (number? (:answer r)) (string? (:reply r))
                                    (if-let [n (get expected task)]
                                      (< (abs (- (double n) (double (:answer r)))) 0.00001)
                                      true))
                         false)))]
      {:status (if valid :valid :invalid)
       :diagnostic (when-not valid
                     (if (= decision :done) "Delivered answer failed independent validation; recheck rules and computation."
                         "Stage output is missing or violates the documented contract."))
       :observation observed})))

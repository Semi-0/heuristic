(load-file (str (System/getenv "LAIN_HARNESS_ROOT") "/tools/offline_uuid.clj"))
(require '[clojure.string :as str] '[lain-harness.soul-session :as soul]
         '[lain-harness.premise-experiment :as premise] '[lain-harness.model-io-session :as model]
         '[lain-harness.blazux :as blazux] '[propagators.compiler-2.session :as session]
         '[propagators.compiler-2.session.host :as host] '[propagators.compiler-2.session.connected :as connected]
         '[propagators.datastructures.support-collection :as collection])
(let [root (System/getenv "LAIN_HARNESS_ROOT")
      control? (= "1" (System/getenv "LAIN_CONTROLLED"))
      evidence (str (or (System/getenv "LAIN_EVIDENCE_DIR")
                        (str root "/runtime/premise-inventory-"
                             (if control? "control" (or (System/getenv "LAIN_ATTEMPT") "live")))) "/")
      _ (.mkdirs (java.io.File. evidence))
      owner (host/open! (model/compiler)) journal (atom []) requests (atom []) snapshots (atom [])
      provider (blazux/completion-transport {:timeout-ms 600000 :max-tokens 4096 :enable-thinking true :reasoning-effort "medium" :thinking-token-budget 2048})
      read-value #(connected/payload (session/binding-value (host/current-session owner) %))
      publish (fn [name epoch status base]
                (let [current (host/current-session owner)
                      reference (session/binding-cell current name)
                      source (session/binding-cell current (if (= name 'damage-status) 'damage-percent name))]
                  (host/submit! owner (session/cell-input reference (collection/content
                                                                     {:base base :support #{{:source source :timestamp epoch :premises-status status}}})))
                  (host/step! owner)))
      source "(define stock-fn (ttms-premise-closure (ttms-premise-closure (network [b n r] (/ (* (* b n) (- 100 r)) 100)) definition-stock) question-stock))
              (define usable (stock-fn boxes items-per-box damage-percent))
              (define order-fn (ttms-premise-closure (ttms-premise-closure (network [stock quantity] (- stock quantity)) definition-order) question-order))
              (define margin (order-fn usable order-quantity))
              (define can-fill (>= margin 0))
              (-> (str damage-status \"; usable=\" usable \"; margin=\" margin) user-output)"]
  (try
    (host/extend! owner (soul/extension owner journal))
    (host/extend! owner (premise/facilities))
    (host/extend! owner (model/declaration root "soul-refined.lain"
                                           {:transport (fn [request]
                                                         (swap! requests conj (dissoc request :cancelled?))
                                                         (spit (str evidence "context.txt") (:text request))
                                                         (let [reply (if control? {:kind :reply :text source :thinking "" :finish-reason "stop" :truncated? false} (provider request))]
                                                           (spit (str evidence "response.edn") (pr-str reply))
                                                           (spit (str evidence "generated.lain") (:text reply)) reply))}))
    (doseq [[name v] {'boxes 20 'items-per-box 12 'damage-percent 10 'damage-status "unverified estimate"
                      'order-quantity 200 'question-stock "How many usable items are in 20 boxes of 12, with an estimated 10 percent damaged?"
                      'question-order "Can we fulfill an order for 200 using that usable stock?"
                      'definition-stock "agent-generated stock formula" 'definition-order "agent-generated order formula"
                      'delivery-note "Tuesday"}]
      (publish name 1 :active v))
    (publish 'trigger 1 :active true)
    (let [deadline (+ (System/currentTimeMillis) 660000)]
      (loop []
        (when-let [e (first (host/failures owner))] (throw e))
        (let [a (read-value 'admission-result)]
          (cond (= :rejected (:status a)) (throw (ex-info "Admission failed" a))
                (= :admitted (:status a)) true
                (> (System/currentTimeMillis) deadline) (throw (ex-info "Timed out" {:admission a}))
                :else (do (Thread/sleep 30) (recur))))))
    (let [target (:id (read-value 'generated-environment))
          names '[boxes items-per-box damage-percent order-quantity question-stock question-order definition-stock definition-order delivery-note]
          by-id (into {} (map (fn [name] [(session/binding-cell (host/current-session owner) name) name])) names)
          snapshot (fn [label]
                     (let [current (host/current-session owner) network (session/network current)
                           results (into {} (map (fn [name]
                                                   (let [reference (if (= name 'user-output) (session/binding-cell current name)
                                                                       (session/scoped-binding-cell current target name))
                                                         view (premise/inspect-value network reference)]
                                                     [name (assoc view :support-names (set (map #(get by-id (:source %) (:source %)) (:support view))))]))
                                                 '[usable margin can-fill user-output]))
                           entry {:stage label :results results}]
                       (swap! snapshots conj entry)
                       (prn entry) (flush) results))
          stock-support '#{boxes items-per-box damage-percent question-stock definition-stock}
          order-support (into stock-support '[order-quantity question-order definition-order])
          check (fn [state stock margin fill]
                  (assert (= [stock margin fill] (mapv #(get-in state [% :value]) '[usable margin can-fill]))))
          initial (snapshot :initial)]
      (check initial 216 16 true)
      (assert (= stock-support (get-in initial ['usable :support-names])))
      (assert (= order-support (get-in initial ['margin :support-names])))
      (assert (= order-support (get-in initial ['can-fill :support-names])))
      (assert (str/includes? (get-in initial ['user-output :value]) "unverified"))
      (publish 'damage-percent 2 :active 25)
      (publish 'damage-status 2 :active "confirmed inspection")
      (let [corrected (snapshot :corrected)]
        (check corrected 180 -20 false)
        (assert (str/includes? (get-in corrected ['user-output :value]) "confirmed")))
      (publish 'delivery-note 2 :active "Friday")
      (check (snapshot :unrelated-change) 180 -20 false)
      (publish 'damage-percent 3 :retracted 25)
      (publish 'damage-status 3 :retracted "withdrawn")
      (check (snapshot :damage-withdrawn) :unknown :unknown :unknown)
      (publish 'damage-percent 4 :active 25)
      (publish 'damage-status 4 :active "confirmed inspection")
      (check (snapshot :recovered) 180 -20 false)
      (publish 'question-order 2 :retracted "order question withdrawn")
      (check (snapshot :order-question-withdrawn) 180 :unknown :unknown)
      (publish 'question-order 3 :active "Can we fulfill an order for 200?")
      (check (snapshot :order-question-restored) 180 -20 false)
      (assert (= 1 (count @requests)))
      (prn {:status :passed :controlled? control? :provider-calls (count @requests)}))
    (finally
      (spit (str evidence "evidence.edn") (pr-str {:requests @requests :admissions @journal :snapshots @snapshots}))
      (spit (str evidence "source.lain") (slurp (str root "/soul-refined.lain")))
      (prn {:cleanup (host/close! owner)}) (shutdown-agents))))

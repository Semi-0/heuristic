(ns lain-harness.context-policy-test
  (:require [clojure.test :refer [deftest is]]
            [lain-harness.context-store-test :as fixtures]
            [lain-harness.context-store :as store]
            [lain-harness.context-runtime :as runtime]
            [lain-harness.soul-session :as soul]
            [lain-harness.experiment-observation :as observation]
            [lain-harness.model-io-session :as model]
            [propagators.compiler-2.session :as s]
            [propagators.compiler-2.session.host :as h]
            [propagators.compiler-2.session.connected :as connected]))

(def ^:dynamic *entry* :task)

(defn run-policy [transport token-count]
  (let [catalog (fixtures/store) base (model/compiler)
        owner (h/open! (s/create-session
                        (assoc (s/session-identity base)
                               :native-extensions (:bootstrap (s/session-declarations base))
                               :propagation-options {:max-steps 300000 :timeout-ms 60000}))) journal (atom [])
        calls (atom [])]
    (try
      (add-watch (:journal catalog) ::progress
                 (fn [_ _ _ entries] (prn :catalog-operation (select-keys (last entries) [:operation :id :status])) (flush)))
      (h/extend! owner (soul/extension owner journal))
      (h/extend! owner (observation/facilities))
      (h/extend! owner (store/facilities catalog))
      (h/extend! owner (runtime/facilities catalog (fn [request] (prn :controlled-count (:id request)) (flush) token-count) (runtime/validator {"Test task" 5})))
      (h/extend! owner
                 (model/declaration (System/getenv "LAIN_HARNESS_ROOT") "experiments/context.lain"
                                    {:transport (fn [request]
                                                  (println :controlled-request (:id request)) (flush)
                                                  (swap! calls conj request)
                                                  (merge {:kind :reply :finish-reason "stop" :truncated? false :thinking ""}
                                                         (transport request)))}))
      (println :loaded-context) (flush)
      (h/extend! owner (s/session-extension
                        {:id :task :files {"task.lain"
                          (cond (= *entry* :measure)
                            "(define stage-result (let-cell [port] port)) (define outcome (context:request (cell:reference context-seed)
                              \"q0-work\" \"Test task\" :work \"No history\" \"No diagnostic\" (cell:reference stage-result)))"
                            (= *entry* :attempt)
                            "(define outcome (context:attempt (cell:reference context-seed) (cell:reference (model:input context-model))
                              (cell:reference context-response) (cell:reference context-requests)
                              \"q0-work\" 0 \"Test task\" :work \"No history\" \"No diagnostic\"))"
                          :else "(define outcome (context:task (cell:reference context-seed) (cell:reference (model:input context-model))
                            (cell:reference context-response) (cell:reference context-requests)
                            \"q0\" 0 \"Test task\" \"\"))")}}))
      (println :installed-task) (flush)
      (let [deadline (+ (System/currentTimeMillis) 180000)]
        (loop []
          (let [v (connected/payload (s/binding-value (h/current-session owner) 'outcome))]
            (cond
              (and (map? v) (:status v)) {:outcome v :calls @calls :journal @journal :catalog catalog}
              (seq (h/failures owner)) (throw (ex-info "Host failed" {:failures (h/failures owner)}))
              (> (System/currentTimeMillis) deadline)
              (throw (ex-info "Policy timed out" {:calls (mapv :id @calls) :value v :journal @journal :catalog @(:journal catalog) :effects (mapv #(assoc (select-keys % [:status :cleanup]) :operation (get-in % [:request :boundary/payload :effect-input :operation])) (vals (h/receipts owner)))
                  :top (into {} (for [n '[context-seed context:task context:stage context-model context-requests context:archive context:bind]]
                                  [n (let [v (connected/payload (s/binding-value (h/current-session owner) n))]
                                       (if (map? v) (select-keys v [:id :compiler-2/callable?]) (str v)))]))
                  :bindings (observation/binding-views (s/network (h/current-session owner))
                              '#{packet count request-archive allowed response receipt observed checked attempt first result work indexed})}))
              :else (do (Thread/sleep 20) (recur))))))
      (catch Exception e (prn :policy-diagnostic (ex-data e)) (flush) (throw e))
      (finally (h/close! owner)))))

(def index-source
  "(define claim-receipt (context:index \"{:catalog/id :task/test-v1 :catalog/description \\\"Controlled example\\\" :catalog/evidence [[:catalog/id :evidence/q0/task]]}\"))
   (-> (data:record :decision :indexed :receipts (seq:of claim-receipt)) stage-result)")

(deftest compiled-context-cycle
  (let [r (run-policy (fn [{:keys [id]}]
                        {:text (if (= id "q0-index") index-source
                                 "(define add-one (network [x] (+ x 1)))
                                  (define result (add-one 4))
                                  (-> (data:record :decision :done :answer result :reply (str \"Result: \" result)) stage-result)")}) 100)]
    (is (= :delivered (get-in r [:outcome :status])))
    (is (= :valid (get-in r [:outcome :index :status])))
    (is (= ["q0-work" "q0-index"] (mapv :id (:calls r))))
    (is (= 5 (get-in r [:outcome :work :attempt :result :answer])))
    (is (= :source (:catalog/evidence-kind (store/entity (:catalog r) :evidence/q0-work/source))))))

(deftest incomplete-response-and-one-correction
  (let [r (run-policy (fn [{:keys [id]}]
                        (if (= id "q0-index") {:text index-source}
                            {:text "(define forbidden 5)" :finish-reason "length" :truncated? true})) 100)]
    (is (= :unresolved (get-in r [:outcome :status])))
    (is (= ["q0-work" "q0-work-correction" "q0-index"] (mapv :id (:calls r))))
    (is (empty? (filter #(= :admitted (get-in % [:receipt :status]))
                       (filter #(#{"q0-work" "q0-work-correction"} (:response %)) (:journal r)))))))

(deftest overflow-never-sends
  (let [r (run-policy (fn [_] (throw (ex-info "Must not send" {}))) 12001)]
    (is (= :unresolved (get-in r [:outcome :status])))
    (is (empty? (:calls r)))))

(deftest isolated-attempt
  (binding [*entry* :attempt]
    (let [r (run-policy (fn [_] {:text "(-> (data:record :decision :done :answer (+ 2 3) :reply \"5\") stage-result)"}) 100)]
      (is (= :valid (get-in r [:outcome :status])))
      (is (= 1 (count (:calls r)))))))

(deftest isolated-request
  (binding [*entry* :measure]
    (let [r (run-policy (fn [_] (throw (ex-info "No provider expected" {}))) 100)]
      (is (= :counted (get-in r [:outcome :status])))
      (is (empty? (:calls r))))))

(deftest retrieved-callable-with-evicted-source
  (let [r (run-policy
            (fn [{:keys [id]}]
              {:text
               (case id
                 "q0-index" index-source
                 "q0-work"
                 "(define increment (network [x] (+ x 1)))
                  (define registration (cell:first (context:register \"{:catalog/id :op/increment-v1 :catalog/description \\\"Controlled increment\\\" :catalog/interface \\\"[x] -> number\\\" :catalog/assumptions \\\"integer input\\\"}\" (cell:reference increment))))
                  (define discovered (if (= (data:get :status registration) :registered)
                    (cell:first (context:query \"[:find ?id ?description :in $ % :where [?e :catalog/id ?id] [?e :catalog/description ?description]]\" \"[]\" nil 8))
                    (data:record :error true)))
                  (-> (data:record :decision :retrieve :evidence discovered) stage-result)"
                 "q0-work-retrieve"
                 "(define chosen (data:get 0 (data:get 0 (data:get :rows working-evidence))))
                  (define resolved (cell:first (context:resolve chosen)))
                  (define answer ((cell:value resolved) 4))
                  (-> (data:record :decision :done :answer answer :reply (str answer)) stage-result)")}) 100)
        second-prompt (:text (second (:calls r)))]
    (is (= :delivered (get-in r [:outcome :status])))
    (is (= 5 (get-in r [:outcome :work :attempt :result :answer])))
    (is (.contains second-prompt "Controlled increment"))
    (is (not (.contains second-prompt "(define increment")))))

(deftest retrieval-budget-stops-with-unresolved-evidence
  (let [r (run-policy
            (fn [{:keys [id]}]
              {:text (if (= id "q0-index") index-source
                         "(define page (cell:first (context:query \"[:find ?id :in $ % :where [?e :catalog/id ?id]]\" \"[]\" nil 1)))
                          (-> (data:record :decision :retrieve :evidence page) stage-result)")}) 100)]
    (is (= :unresolved (get-in r [:outcome :status])))
    (is (= :retrieval-limit (get-in r [:outcome :work :reason])))
    ;; Four retrieval continuations; a further request cannot be scheduled.
    (is (= 6 (count (:calls r))))
    (is (= "q0-index" (:id (last (:calls r)))))))

(deftest fabricated-index-receipts-are-not-observations
  (let [r (run-policy
            (fn [{:keys [id]}]
              {:text (if (= id "q0-work")
                         "(-> (data:record :decision :done :answer 5 :reply \"5\") stage-result)"
                         "(-> (data:record :decision :indexed :receipts (seq:of (data:record :status :indexed :id :fake/claim))) stage-result)")}) 100)]
    (is (= :invalid (get-in r [:outcome :index :status])))
    (is (= ["q0-work" "q0-index" "q0-index-correction"] (mapv :id (:calls r))))
    (is (nil? (:catalog/id (store/entity (:catalog r) :fake/claim))))))

(deftest documented-retrieval-example-round-trip
  ;; Execute the exact example supplied to Qwen, not a duplicated approximation.
  (let [source (slurp (str (System/getenv "LAIN_HARNESS_ROOT") "/environments/context.lain"))
        forms (with-open [r (java.io.PushbackReader. (java.io.StringReader. source))]
                (binding [*read-eval* false]
                  (loop [forms []]
                    (let [form (read {:eof ::end} r)]
                      (if (= ::end form) forms (recur (conj forms form)))))))
        example (nth (first (filter #(and (seq? %) (= 'retrieval-example-cell (second %))) forms)) 2)
        executable (subs example (inc (.indexOf example "\n")))
        r (run-policy (fn [{:keys [id]}]
                        {:text (case id
                                 "q0-work" executable
                                 "q0-work-retrieve"
                                 "(-> (data:record :decision :unresolved :reason (context:text working-evidence)) stage-result)"
                                 "q0-index" index-source)}) 100)]
    (is (= ["q0-work" "q0-work-retrieve" "q0-index"] (mapv :id (:calls r))))
    (is (.contains (get-in r [:outcome :work :attempt :result :reason]) ":rows"))
    (is (.contains (:text (first (:calls r))) "Its :evidence value becomes working-evidence"))
    (is (not (.contains (:text (first (:calls r))) "previous task details are deliberately omitted")))))

(require '[clojure.edn :as edn] '[clojure.set :as set] '[clojure.string :as str])
(def quantities ["total" "usable" "margin" "fulfillment"])
(defn port [q i] (symbol (str "observed-" q "-" i)))
(defn numeric= [a b] (if (and (number? a) (number? b)) (== a b) (= a b)))
(defn source-dataflow [sources]
  (reduce (fn [g form]
            (cond
              (and (seq? form) (= 'define (first form)))
              (assoc g (second form) (set (filter symbol? (tree-seq coll? seq (nth form 2 nil)))))
              (and (seq? form) (= '-> (first form)) (symbol? (last form)))
              (update g (last form) (fnil into #{}) (filter symbol? (tree-seq coll? seq (second form))))
              :else g)) {} (mapcat #(edn/read-string (str "[" % "]")) sources)))
(defn source-ancestors [g start]
  (loop [pending [start] seen #{}]
    (if-let [n (peek pending)]
      (if (contains? seen n) (recur (pop pending) seen)
          (recur (into (pop pending) (get g n)) (conj seen n))) seen)))
(defn evaluate [e]
  (let [stages (into {} (map (juxt :stage identity) (:snapshots e)))
        result (fn [stage q i] (get-in stages [stage :results (port q i)]))
        values (fn [stage i] (mapv #(:value (result stage % i)) quantities))
        tests (atom [])
        check (fn [name expected actual]
                (swap! tests conj {:check name :expected expected :actual actual :pass (= expected actual)}))
        check-values (fn [stage i expected]
                       (doseq [[q v] (map vector quantities expected)]
                         (let [actual (:value (result stage q i))]
                           (swap! tests conj {:check [stage i q] :expected v :actual actual :pass (numeric= v actual)}))))
        before (values :before-damage 1) after (values :damage-published 1)
        damage-ref (some (fn [[ref n]] (when (= n 'damage-percent-1) ref))
                        ;; Input references are recorded explicitly by the probe.
                        (:input-names e))
        admitted-sources (map :text (take (count (:rounds e)) (:responses e)))
        source1 (source-dataflow (take 1 admitted-sources))
        source2 (source-dataflow admitted-sources)
        partial-ports (vec (for [q (rest quantities)
                                :let [r (result :before-damage q 1)]
                                :when (and (= :unknown (:value r)) damage-ref
                                           (contains? (:input-ancestors r) damage-ref)
                                           (contains? (source-ancestors source1 (port q 1)) 'damage-percent-1))] q))
        completion (every? true? (map numeric= [240 216 16 true] after))
        reuse (boolean (some (fn [[name c]] (and (:same-reference c) (:same-declaration c)
                                             (:same-captured-environment c) (:source-reference c)
                                             (seq (:new-invocations c))
                                             (some #(contains? (source-ancestors source2 %) name) (:connected-answer-ports c))))
                              (get-in e [:outcome :callable-checks])))]
    (check :request-count 2 (count (:requests e)))
    (check :source-history-preserved (mapv :text (:responses e)) (:written-code e))
    (check :reply-history-preserved (:history (last (:rounds e))) (:history e))
    (check :stable-session-and-child true (get-in e [:outcome :stable-identity]))
    (when (some :worker (:snapshots e))
      (check :one-persistent-worker 1 (count (set (map :worker (:snapshots e))))))
    (doseq [[stage entry] stages]
      (check [:request-count-at stage] (if (#{:before-damage :damage-published} stage) 1 2) (:request-count entry)))
    (doseq [[before after i] [[:corrected :question-1-withdrawn 2]
                            [:question-1-restored :damage-2-withdrawn 1]
                            [:question-1-restored :damage-2-restored 1]] q quantities]
      (check [:independent-support before after i q]
             (select-keys (result before q i) [:value :support])
             (select-keys (result after q i) [:value :support])))
    (check-values :before-damage 1 [240 :unknown :unknown :unknown])
    (check-values :damage-published 1 [240 216 16 true])
    (check-values :question-2 2 [360 342 -8 false])
    (check-values :corrected 2 [360 351 1 true])
    (check-values :question-1-withdrawn 1 [:unknown :unknown :unknown :unknown])
    (check-values :question-1-withdrawn 2 [360 351 1 true])
    (check-values :question-1-restored 1 [240 216 16 true])
    (check-values :damage-2-withdrawn 2 [360 :unknown :unknown :unknown])
    (check-values :damage-2-restored 2 [360 351 1 true])
    (doseq [i [1 2] q quantities]
      (check [:unrelated-value-and-support i q]
             (select-keys (result :damage-2-restored q i) [:value :support :premise-states])
             (select-keys (result :unrelated-change q i) [:value :support :premise-states])))
    ;; Every numeric/boolean output needs its own question, rule, and relevant facts.
    ;; Compare exact semantic support sets while allowing no silent host completions.
    (doseq [[stage i] [[:damage-published 1] [:question-2 2]] q quantities]
      (let [required (set (map #(symbol (str % "-" i))
                              (concat ["boxes" "items-per-box" "question"]
                                      (when (not= q "total") ["damage-percent"])
                                      (when (#{"margin" "fulfillment"} q) ["order-quantity"]))))
            expected (conj required 'shared-rule)]
        (check [:support stage q] expected (:support-names (result stage q i)))))
    {:operational-status (get-in e [:outcome :operational-status])
     :operational-error (get-in e [:outcome :error])
     :request-count (count (:requests e)) :provider-request-count (if (:controlled e) 0 (count (:requests e)))
     :numerical-correctness {:before-damage (every? true? (map numeric= [240 :unknown :unknown :unknown] before))
                             :question-2 (every? true? (map numeric= [360 342 -8 false] (values :question-2 2)))}
     :partial-network {:incrementally-completed-child-bindings
                       (vec (for [[name b] (get-in stages [:before-damage :child-bindings])
                                  :let [after (get-in stages [:damage-published :child-bindings name])]
                                  :when (and (= :unknown (:value b))
                                             (contains? (source-ancestors source1 name) 'damage-percent-1)
                                             (contains? (:support-names after) 'damage-percent-1)
                                             (not (#{:unknown :callable nil} (:value after))))]
                              {:name name :value (:value after) :support-names (:support-names after)}))
                       :connected-waiting-ports partial-ports :incremental-completion (and (seq partial-ports) completion)}
     :completion-without-inference (and completion (= 1 (get-in stages [:damage-published :request-count])))
     :callable-reuse reuse :callable-evidence (get-in e [:outcome :callable-checks])
     :reply-updated-after-publication (not= (get-in stages [:before-damage :results 'user-output-1 :value])
                                          (get-in stages [:damage-published :results 'user-output-1 :value]))
     :missing-observations (vec (for [[stage i] [[:before-damage 1] [:damage-published 1] [:question-2 2]] q quantities
                                      :when (= :unknown (:value (result stage q i)))] [stage i q]))
     :checks @tests}))
(let [root (first *command-line-args*)
      dirs (sort-by #(.getName %) (filter #(and (.isDirectory %) (re-find #"^(control-|trial-)" (.getName %))) (.listFiles (java.io.File. root))))
      reports (into {} (for [dir dirs :let [e (if (.exists (java.io.File. dir "evidence.edn"))
                                                   (edn/read-string {:default tagged-literal} (slurp (java.io.File. dir "evidence.edn")))
                                                   {:outcome {:operational-status :failed :error "Process ended without evidence.edn; inspect run.log"}}) report (evaluate e)]]
                        (do (spit (java.io.File. dir "evaluation.edn") (pr-str report)) [(.getName dir) report])))]
  (spit (str root "/evaluation.edn") (pr-str reports))
  (doseq [[name r] reports]
    (prn name (dissoc r :checks :callable-evidence :missing-observations))
    (println "Failed checks:" (mapv :check (remove :pass (:checks r)))))
  (when (every? #(str/starts-with? % "control-") (keys reports))
    (assert (= 5 (count reports)) "All five controls must be present")
    (doseq [[name r] reports] (assert (= :completed (:operational-status r)) name))
    (doseq [name ["control-connected" "control-reuse" "control-copied"]]
      (assert (true? (get-in reports [name :partial-network :incremental-completion])) name)
      (assert (every? :pass (get-in reports [name :checks])) name))
    (assert (empty? (get-in reports ["control-disconnected" :partial-network :connected-waiting-ports])))
    (assert (false? (get-in reports ["control-disconnected" :completion-without-inference])))
    (assert (true? (get-in reports ["control-reuse" :callable-reuse])))
    (assert (false? (get-in reports ["control-copied" :callable-reuse])))
    (assert (some #(and (= [:support :question-2 "usable"] (:check %)) (not (:pass %)))
                  (get-in reports ["control-cross" :checks])))
    (assert (some #(and (= [:question-1-withdrawn 2 "usable"] (:check %)) (not (:pass %)))
                  (get-in reports ["control-cross" :checks])))
    (println "All evaluator controls passed.")))

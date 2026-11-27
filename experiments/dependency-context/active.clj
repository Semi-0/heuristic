(def encouragement
  (str " When your next decision depends on a fact you can look up or a calculation you can run, "
       "make the smallest read or Lain call that resolves it. Use the returned result to decide what to do next. "
       "If the result is surprising, check a concrete case that distinguishes the possible explanations. "
       "Once the evidence supports the answer, reply."))

(defn action-result [ctx id expr meter?]
  (let [form (form! expr (if meter? '#{meter rate-x rate-y + - *} '#{press-k tariff-b tariff-m + - *}))]
    (doseq [node (filter #(and (list? %) (= 'meter (first %))) (tree-seq sequential? seq form))]
      (when-not (and (= 2 (count node)) (integer? (second node)) (<= 0 (second node) 50))
        (throw (ex-info "meter accepts one integer literal from 0 to 50" {}))))
    {:form (pr-str form) :value (run-expr ctx id form)}))

(defn distinguishes? [{:keys [form value]}]
  (and (number? value)
       (some #(and (list? %) (= 'meter (first %)) (not= 10 (second %)))
             (tree-seq sequential? seq (:form (tools/read-draft form))))))

(defn resolve-order-expr [source events]
  (let [form (form! source '#{press-k tariff-b tariff-m + - * bundles d1 d2 d3})
        quantities (keep #(some-> (re-find #"(\d+) bundles" (:body %)) second parse-long) events)
        names (into {} (for [{:keys [id body]} events
                             :let [definition (:form (tools/read-draft body))]
                             :when (and (list? definition) (= 'define (first definition)))]
                         [(symbol id) (second definition)]))
        replacements (cond-> names (= 1 (count quantities)) (assoc 'bundles (first quantities)))]
    (pr-str (clojure.walk/postwalk-replace replacements form))))

(defn apply-action! [ctx id meter? reply events receipts mark!]
  (if-let [ids (:read reply)]
    (let [more (expand ctx ids)
          merged (sort-by :sequence (vals (into {} (map (juxt :id identity) (concat events more)))))]
      (mark! :read more)
      [merged receipts])
    (let [receipt (try (action-result ctx id (:run reply) meter?)
                       (catch Exception e {:error (ex-message e)}))]
      (mark! :run receipt)
      [events (conj receipts receipt)])))

(defn joined-final [ctx id plan receipts]
  (if-let [{:keys [equals yes no]} (:then plan)]
    (let [observed (:value (last receipts))
          _ (when-not (number? observed) (throw (ex-info "Expected a numeric probe receipt" {})))
          compared (:value (action-result ctx [id :compare] equals true))]
      (assoc plan :expr (if (= observed compared) yes no)))
    plan))

(def staged-role
  (str answer-role " For a trial return {\"run\":\"(function argument)\"}; you receive its execution result before deciding. "
       "After reading an order, bundles denotes its quantity and d1/d2/d3 can be invoked as function names if their definitions were read. "
       "Final expr is executed for scoring."))

(def joined-instruction
  (str " You may combine the action and answer in one JSON object. For an archive calculation supply read, expr and evidence together; "
       "the runtime resolves expr using the returned definitions and bundles. For a conditional probe supply "
       "{\"run\":\"probe expression\",\"then\":{\"equals\":\"comparison expression\",\"yes\":\"answer expression if equal\",\"no\":\"answer expression otherwise\"},\"evidence\":[]}. "
       "The runtime executes the probe, compares the returned number, and executes the selected branch. Separate actions are also available."))

(defn active-question! [ctx ep arm kind]
  (let [start (System/nanoTime) offset (count @(:calls ctx)) first-action (atom nil)
        meter? (= kind :meter) old? (even? ep) n (+ 80 ep)
        rule (if old? 'rate-y 'rate-x)
        order (if old? "i1" "r1") tariff (if old? 'tariff-b 'tariff-m)
        required #{order "d1" (if old? "d2" "d3")}
        bundles (+ ep (if old? 12 19)) sheets (+ 3 (* 8 bundles))
        expected (if meter? (if old? (+ 40 (* 10 n)) (+ 50 (* 9 n)))
                     (if old? (+ 40 (* 11 sheets)) (+ 95 (* 9 sheets))))
        question (if meter?
                   (str "Which stored rate does the shop meter use, and what will it charge at " n
                        " sheets? rate-x(n)=9*n+50 and rate-y(n)=10*n+40. Its only recorded sample is 140 at 10 sheets. "
                        "meter accepts a literal test quantity from 0 to 50. Final expr should use the applicable rate, evidence [].")
                   (if old? "Codex: What rush charge applied to the Aster order as originally invoiced?"
                       "Semi: What rush charge applies to the revised Aster order?"))
        joined? (#{:collapsed :combined} arm)
        role (str staged-role (when joined? joined-instruction)
                  (when (#{:encouraged :combined} arm) encouragement))
        idx (when-not meter? (mapv #(select-keys % [:id :label :parents]) (index-rows ctx)))
        mark! (fn [action evidence]
                (when (case action
                        :read (and (not meter?) (some #(= order (:id %)) evidence))
                        :run (and meter? (distinguishes? evidence)))
                  (compare-and-set! first-action nil {:action action :seconds (elapsed start)
                                                      :generated (measure (subvec @(:calls ctx) offset) :completion_tokens)})))
        result
        (try
          (loop [events [] receipts [] round 0]
            (if (= 8 round)
              {:error "turn limit"}
              (let [id [ep arm kind round]
                    payload (pr-str (cond-> {:question question :receipts receipts} idx (assoc :index idx :events (rows events))))
                    reply (json/read-json (call! ctx (str (name arm) "-" (name kind)) (str round) role payload nil))
                    action? (or (:read reply) (:run reply))
                    [events receipts] (if action? (apply-action! ctx [id :action] meter? reply events receipts mark!)
                                          [events receipts])
                    joined (and joined? action? (or (:expr reply) (:then reply)))]
                (if (and action? (not joined))
                  (recur events receipts (inc round))
                  (let [final (if joined (joined-final ctx id reply receipts) reply)
                        source (if meter? (:expr final) (resolve-order-expr (:expr final) events))
                        form (form! source (if meter? '#{rate-x rate-y + - *} '#{press-k tariff-b tariff-m + - *}))
                        value (run-expr ctx id form)
                        operators (set (map first (filter list? (tree-seq list? seq form))))
                        reuse (if meter? (and (operators rule) (not (operators (if old? 'rate-x 'rate-y))))
                                  (and (operators 'press-k) (operators tariff)))
                        supported (if meter? (boolean (some distinguishes? receipts))
                                      (and (= required (set (:evidence reply))) (every? (set (map :id events)) required)))]
                    {:correct (= value expected) :supported supported :reuse (boolean reuse)
                     :pass (boolean (and (= value expected) supported reuse)) :expr (pr-str form)
                     :joined (boolean joined) :value value :expected expected :trial-count (count receipts)})))))
          (catch Exception e {:error (ex-message e)}))]
    (merge {:episode ep :arm arm :question kind :first-action @first-action} result
           (metrics (subvec @(:calls ctx) offset) start))))

(defn install-meter! [ctx ep]
  (doseq [[sym body] [['rate-x "(+ (* 9 n) 50)"] ['rate-y "(+ (* 10 n) 40)"]
                      ['meter (if (even? ep) "(rate-y n)" "(rate-x n)")]]]
    (tools/extend-source! (:owner ctx) (binding! ctx 'repair-frame) [:install sym]
                          (str "(define " sym " (network [n] " body "))"))))

(defn active-questions! [ctx ep]
  (install-meter! ctx ep)
  ;; Each condition appears once in each position over the four paired blocks.
  (doseq [[kind arms] (if (odd? ep)
                        [[:order [:baseline :encouraged :combined :collapsed]]
                         [:meter [:encouraged :collapsed :baseline :combined]]]
                        [[:meter [:collapsed :combined :encouraged :baseline]]
                         [:order [:combined :baseline :collapsed :encouraged]]])
          arm arms
          :when (not (some #(and (= (name arm) (:arm %)) (= (name kind) (:question %))) (:previous-scores ctx)))]
    (let [r (active-question! ctx ep arm kind)]
      (spit (io/file (:dir ctx) "scores.jsonl") (str (json/write-json r) "\n") :append true)
      (println r) (flush))))

(defn check-continuations! [ctx ep]
  (install-meter! ctx ep)
  ;; Exercise the exact live scoring path with known valid plans and both meter branches.
  (let [old? (even? ep)
        scripted (fn [ctx _ _ _ payload _]
                   (let [meter? (str/includes? payload "shop meter")
                         plan (if meter?
                                {:run "(meter 0)" :then {:equals "(rate-x 0)"
                                                         :yes (str "(rate-x " (+ 80 ep) ")")
                                                         :no (str "(rate-y " (+ 80 ep) ")")} :evidence []}
                                (if old? {:read ["i1"] :expr "(d2 (d1 bundles))" :evidence ["i1" "d1" "d2"]}
                                    {:read ["r1"] :expr "(d3 (d1 bundles))" :evidence ["r1" "d1" "d3"]}))]
                     (swap! (:calls ctx) conj {:usage {:prompt_tokens 0 :completion_tokens 0}})
                     (json/write-json plan)))]
    (with-redefs [call! scripted]
      (doseq [kind [:order :meter]]
        (let [r (active-question! ctx ep :collapsed kind)]
          (assert (and (:pass r) (:joined r) (= 1 (:calls r))) (pr-str r))
          (println "scripted valid plan" ep kind (:value r)))))))

(doseq [ep [1 2]]
  (run-reuse! (second *command-line-args*) ep
              (if (= "check" (nth *command-line-args* 3 nil)) check-continuations! active-questions!)))

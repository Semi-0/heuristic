(load-file (str (System/getenv "LAIN_HARNESS_ROOT") "/tools/offline_uuid.clj"))
(load-file (str (System/getenv "LAIN_HARNESS_ROOT") "/experiments/attempt-cli/tools.clj"))
(require '[attempt-cli.tools :as tools] '[clojure.java.io :as io] '[clojure.string :as str]
         '[datomic.client.api :as d] '[graph.json :as json]
         '[lain-harness.soul-session :as soul] '[lain-harness.catalog :as catalog] '[lain-harness.blazux :as qwen]
         '[lain-harness.model-io-session :as model] '[lain-harness.session-publication :as pub]
         '[lain-harness.premise-experiment :as ttms] '[propagators.compiler-2.session :as s]
         '[propagators.compiler-2.session.connected :as c] '[propagators.compiler-2.session.host :as h])
(def root (System/getenv "LAIN_HARNESS_ROOT"))
(def settings {:model "qwen3.8-flash-next" :temperature 0.0 :enable-thinking false :max-tokens 1024 :timeout-ms 90000})
(def questions
  {:price "Shop p1: an 80-copy invoice was 16000 cents. Flat costs 175 cents/copy. Marginal charges 200 each through 100 and 150 on the excess. Whole charges 200 each through 100 and 150 on ALL copies for larger orders. Quote 140 copies under the current policy using a justified existing callable and the identifying invoice."
   :repair (str "Shop p1: verified 120-copy invoice invoice-120 is 23000 cents; the marginal rule is established. Repair and submit the supplied executable Lain price function. Its current draft is:\n" tools/broken)
   :asof "What quote was actually returned before the quantity revision, and what does the same callable return after it?"
   :a1 "How much is (- 20 (* 100 30))?" :a2 "Divide your previous result by 20." :a3 "Add 150 to that."})
(def specs {:price ["invoice120" "policy" "target"] :repair ["diagnostic1" "draft1"]
            :asof ["price-answer" "revised-quote"] :a1 ["start"] :a2 ["result1"] :a3 ["result2"]})
(def formats
  {:price "Emit exactly three whitespace-separated fields: the literal uppercase word QUOTE, one of flat/marginal/whole, and the invoice identifier printed in the receipt body. The archive lookup ID and the printed invoice identifier can differ."
   :repair "Return only the complete corrected (define price (network [n] ...)) in Lain."
   :asof "Return only a JSON array of two integer cents amounts in historical then revised order. If unavailable return []."
   :a1 "Return only a Lain arithmetic expression using start-value for the initial 20."
   :a2 "Return only a Lain arithmetic expression referencing the appropriate previous result binding."
   :a3 "Return only a Lain arithmetic expression referencing the appropriate previous result binding."})
(defn save! [dir name x] (spit (io/file dir name) (pr-str x)))
(defn snapshot [ctx] (d/db (:connection (:store ctx))))
(defn entry [ctx id]
  (let [e (d/pull (snapshot ctx) '[:catalog/id :catalog/description :catalog/interface :event/seq :event/time
                                   {:catalog/depends-on [:catalog/id]}] [:catalog/id (keyword id)])]
    (when (:catalog/id e)
      {:id id :label (:catalog/interface e) :body (:catalog/description e) :sequence (:event/seq e) :utc (str (.toInstant (:event/time e)))
       :parents (sort (map (comp name :catalog/id) (:catalog/depends-on e)))})))
(defn put! [ctx id label body parents]
  (assert (nil? (entry ctx id)))
  (catalog/seed! (:store ctx) [(cond-> {:catalog/id (keyword id) :catalog/interface label :catalog/description body
                                       :event/seq (swap! (:seq ctx) inc) :event/time (java.util.Date.)}
                                 (seq parents) (assoc :catalog/depends-on (mapv #(vector :catalog/id (keyword %)) parents)))])
  id)
(defn entries [ctx]
  (sort-by :sequence (map #(entry ctx (name (first %))) (d/q '[:find ?id :where [_ :catalog/id ?id]] (snapshot ctx)))))
(defn index-rows [ctx]
  (let [rows (d/q '[:find (pull ?e [:catalog/id :catalog/interface :event/seq
                                     {:catalog/depends-on [:catalog/id]}])
                    :where [?e :catalog/id]] (snapshot ctx))]
    (sort-by :sequence
             (for [[e] rows]
               {:id (name (:catalog/id e)) :label (:catalog/interface e) :sequence (:event/seq e)
                :parents (sort (map (comp name :catalog/id) (:catalog/depends-on e)))}))))
(defn expand [ctx ids]
  (loop [remaining (seq ids) seen {}]
    (if-let [id (first remaining)]
      (if (contains? seen id) (recur (next remaining) seen)
        (let [e (or (entry ctx id) (throw (ex-info "Unknown selection" {:id id})))]
          (recur (concat (next remaining) (:parents e)) (assoc seen id e))))
      (sort-by :sequence (vals seen)))))
(defn binding! [ctx sym]
  (c/payload (s/binding-value (h/current-session (:owner ctx)) sym)))
(defn publish! [ctx sym epoch value]
  (let [owner (:owner ctx)]
    (h/extend! owner (pub/declaration (h/current-session owner) sym epoch :active value))))
(defn call! [{:keys [owner dir calls] :as ctx} q stage role payload scripted]
  (let [id (str (name q) "-" (name stage))
        packet {:id id :messages [{:role "system" :content role} {:role "user" :content payload}]}
        start (System/nanoTime)]
    (save! dir (str id "-packet.edn") packet)
    (reset! (:script ctx) scripted)
    (h/publish-input! owner (fn [cs] [(s/publication-input (s/binding-cell cs 'request) packet)]))
    (h/step! owner)
    (loop []
      (when-let [err (first (h/failures owner))] (throw err))
      (let [reply (binding! ctx 'response)]
        (if (= (:request-id reply) id)
          (let [record (assoc (select-keys reply [:kind :text :usage :finish-reason]) :id id
                              :seconds (/ (- (System/nanoTime) start) 1e9))]
            (swap! calls conj record)
            (save! dir (str id "-reply.edn") record)
            (if (= "stop" (:finish-reason reply)) (str/trim (:text reply)) ""))
          (do (when (> (- (System/nanoTime) start) 100000000000) (throw (ex-info "Model deadline" {:id id})))
              (Thread/sleep 20) (recur)))))))
(defn seed! [ctx]
  (doseq [[id label body parents]
          [["policy" "current p1 pricing policy" "p1; use the invoice to distinguish the candidate callables." []]
           ["target" "p1 requested quantity revision 1" "140 copies" []]
           ["invoice90" "p1 invoice for 90 copies" "invoice-90: 18000 cents" ["policy"]]
           ["invoice120" "obtain p1 invoice for 120 copies" "pending invoice lookup" ["policy"]]
           ["draft1" "p1 draft at repair request revision 1" tools/broken []]
           ["diagnostic1" "request native reader check of p1 revision 1" "pending draft check" ["draft1"]]
           ["draft2" "unrelated revision 2 draft" "(define price 0))" []]
           ["diagnostic2" "reader diagnostic for revision 2" (str (:error (tools/read-draft "(define price 0))"))) ["draft2"]]
           ["start" "arithmetic initial operand" "start-value = 20" []]]]
    (put! ctx id label body parents))
  (doseq [[sym val] [['policy "p1"] ['target-count 140] ['start-value 20]]] (publish! ctx sym 1 val)))
(defn open-question! [ctx q parents]
  (let [id (name q)]
    (put! ctx id (str (if (= q :repair) "Codex" "Semi") " question") (questions q) parents)
    (swap! (:opened ctx) assoc q (System/nanoTime))))
(defn recognize! [ctx q]
  (let [index (vec (index-rows ctx))
        reply (call! ctx q :recognize
                 "You are Lain. Recognize the evidence needed to answer this exact question. Select archive IDs, including any needed pending tool request. Return only a JSON array of ID strings. Prefer precise revisions and the previously computed binding. Do not select the question itself."
                 (str (questions q) "\nRoot " (name q) "\nArchive index " (json/write-json index))
                 (json/write-json (specs q)))
        ids (try (json/read-json reply) (catch Exception _ nil))]
    (if (and (vector? ids) (every? string? ids) (every? #(entry ctx %) ids))
      (vec (distinct ids)) [])))
(defn request-tools! [ctx q ids]
  (let [tasks (filter #{"invoice120" "diagnostic1"} ids)]
    (doseq [task tasks]
      (let [id (str (name q) "-" task "-call")]
        (put! ctx id "tool call" task [(name q) task])
        (swap! (:pending ctx) conj id)))))
(defn call-owner [ctx call]
  (let [{:keys [body parents]} (entry ctx call)]
    (keyword (first (remove #{body} parents)))))
(defn tool-observation! [ctx task]
  (case task
    "invoice120" (do (when (compare-and-set! (:invoiced ctx) false true)
                       (publish! ctx 'invoice120 1 23000))
                     "invoice-120: 120 copies cost 23000 cents, p1.")
    "diagnostic1" (str (:error (tools/read-draft (:body (entry ctx "draft1")))))
    "diagnostic2" (:body (entry ctx task))))
(defn deliver! [ctx call task result]
  (let [q (call-owner ctx call)
        expected (:body (entry ctx call))]
    (if (= expected task)
      (let [id (put! ctx (str call "-receipt") "tool receipt" result [call task])]
        (swap! (:wake ctx) conj {:root q :call call :receipt id})
        (swap! (:receipts ctx) update q (fnil conj []) id)
        q)
      (do (put! ctx (str call "-stale") "rejected wrong-revision receipt" result [call task])
          (swap! (:stale ctx) conj {:call call :expected expected :observed task})
          nil))))
(def scripts {:asof "[26000,26150]" :price "QUOTE marginal invoice-120" :repair tools/good :a1 "(- start-value (* 100 30))"
              :a2 "(/ result1 20)" :a3 "(+ result2 150)"})
(defn inspect-cell [ctx cell]
  (ttms/inspect-value (s/network (h/current-session (:owner ctx))) cell))
(defn evaluate! [ctx q reply]
  (let [owner (:owner ctx)]
    (case q
      :asof {:verified (= [26000 26150] (json/read-json reply))}
      :price (let [[tag rule ref] (str/split reply #"\s+")]
               (if (= tag "QUOTE") (tools/price-result owner rule ref (when @(:invoiced ctx) 120)) {:verified false}))
      :repair (tools/submit! owner (binding! ctx 'repair-frame) :repair reply)
      (let [n ({:a1 1 :a2 2 :a3 3} q) sym (str "result" n)
            expr (:form (tools/read-draft reply))
            allowed '#{+ - * / start-value result1 result2}
            valid? (every? #(or (list? %) (number? %) (allowed %)) (tree-seq list? seq expr))]
        (when-not (and (list? expr) valid?) (throw (ex-info "Expected arithmetic expression" {})))
        (let [frame (binding! ctx 'math-frame)
              _ (tools/extend-source! owner frame [q :define] (str "(define " sym " " reply ")"))
              cell (tools/extend-source! owner frame [q :observe] sym)
              {:keys [value support]} (inspect-cell ctx cell)
              supported (boolean (some #(= (:source %) (s/binding-cell (h/current-session owner) 'start-value)) support))]
          (swap! (:cells ctx) assoc q cell)
          {:value (str value) :start-supported supported
           :verified (and supported (= value (nth [-2980 -149 1] (dec n)))
                          (boolean (some #{(nth '[start-value result1 result2] (dec n))} (flatten expr))))})))))
(defn quote-check! [ctx q previous receipts]
  (let [owner (:owner ctx)
        cs (h/current-session owner)
        amount (fn [sym] (str (:value (inspect-cell ctx (s/binding-cell cs sym)))))
        invoice (some #(when (= "invoice120" (:body (entry ctx (:call %)))) (:receipt %))
                      (filter #(= q (:root %)) @(:wake ctx)))
        readings (if invoice
                   {:invoice-id "invoice-120" :observed-cents (amount 'invoice120)
                    :predictions-at-120 (into {} (for [[rule port] [["flat" 'flat-at-invoice]
                                                                     ["whole" 'whole-at-invoice]
                                                                     ["marginal" 'marginal-at-invoice]]]
                                                   [rule (amount port)]))}
                   {:error "No identifying invoice receipt for this question"})]
    (put! ctx "quote-check" "Runtime quote verification receipt"
          (json/write-json (assoc readings :rejected-action previous))
          (concat [(name q) "policy"] receipts))))
(defn answer! [ctx q ids]
  (let [receipts (get @(:receipts ctx) q [])
        evidence (if (= :full (:arm ctx)) (entries ctx) (expand ctx (concat ids receipts)))
        role (str "You are Lain. Answer the current root using supplied evidence. " (formats q))
        packet (str (questions q) "\nEvidence in journal order:\n" (json/write-json (vec evidence)))
        initial (call! ctx q :answer role packet (if (= q :price) "QUOTE whole invoice-120" (scripts q)))
        evaluate (fn [source] (try (evaluate! ctx q source) (catch Exception e {:verified false :error (ex-message e)})))
        first-result (evaluate initial)
        retry? (and (= q :price) (not (:verified first-result)))
        check-id (when retry? (quote-check! ctx q initial receipts))
        reply (if retry?
                (call! ctx q :repair-action role
                       (str packet "\nRuntime check: " (:body (entry ctx check-id))
                            "\nSubmit the callable whose predicted invoice matches the observed cents. Copy the printed invoice ID.")
                       (scripts q))
                initial)
        evaluated (if retry? (evaluate reply) first-result)
        id (case q :a1 "result1" :a2 "result2" :a3 "result3" (str (name q) "-answer"))
        parents (distinct (concat [(name q)] ids receipts (when check-id [check-id])))
        supported (and (every? (set (map :id evidence)) (specs q))
                       (case q :asof (get-in @(:results ctx) [:price :pass])
                               :price (some #(= "invoice120" (:body (entry ctx (:call %)))) (filter #(= q (:root %)) @(:wake ctx)))
                               :repair (some #(= "diagnostic1" (:body (entry ctx (:call %)))) (filter #(= q (:root %)) @(:wake ctx)))
                               true))]
    (put! ctx id (str "Lain answer / binding " id) (pr-str {:source reply :observed (select-keys evaluated [:value :values :sources :admitted :error])}) parents)
    (swap! (:results ctx) assoc q {:first-verified (boolean (:verified first-result)) :repaired retry? :selected ids :read (mapv :id evidence) :receipts receipts :result evaluated
                                  :supported (boolean supported) :pass (boolean (and supported (:verified evaluated)))
                                  :seconds (/ (- (System/nanoTime) (get @(:opened ctx) q)) 1e9)})))
(defn batch! [ctx qs]
  (let [selected (into {} (for [q qs]
                            (let [ids (recognize! ctx q)] (request-tools! ctx q ids) [q ids])))
        ;; The fixture releases completed calls in reverse submission order.
        ;; Delivery looks up the owning question from the persisted call edges.
        calls (reverse @(:pending ctx))]
    (reset! (:pending ctx) [])
    (let [outstanding (atom (frequencies (map #(call-owner ctx %) calls)))]
      (doseq [call calls]
        (let [task (:body (entry ctx call))]
          (when (and (:interleaved ctx) (= task "diagnostic1"))
            (assert (nil? (deliver! ctx call "diagnostic2" (tool-observation! ctx "diagnostic2")))))
          (let [q (deliver! ctx call task (tool-observation! ctx task))]
            (when (zero? (get (swap! outstanding update q dec) q))
              (answer! ctx q (selected q))))))
      (doseq [q qs :when (nil? (get @outstanding q))] (answer! ctx q (selected q))))))
(defn run-arm! [dir mode arm interleaved]
  (when-not (.mkdirs dir) (throw (ex-info "Use a fresh evidence directory" {:dir dir})))
  (let [owner (h/open! (model/compiler)) script (atom nil) transport (qwen/completion-transport settings)
        ctx {:owner owner :dir dir :arm arm :interleaved interleaved :stale (atom []) :store (catalog/open! (.getAbsolutePath (io/file dir "db")))
             :seq (atom 0) :calls (atom []) :script script :cells (atom {}) :invoiced (atom false)
             :results (atom {}) :opened (atom {}) :receipts (atom {}) :wake (atom []) :pending (atom [])}]
    (try
      (h/extend! owner (soul/extension owner (atom [])))
      (h/extend! owner (ttms/facilities))
      (h/extend! owner (model/declaration root "experiments/dependency-context/runtime.lain"
                         {:transport (if (= mode "control")
                                       (fn [_] {:kind :reply :text @script :finish-reason "stop" :truncated? false})
                                       transport)}))
      (d/transact (:connection (:store ctx)) {:tx-data [{:db/ident :event/seq :db/valueType :db.type/long :db/cardinality :db.cardinality/one}
                                                        {:db/ident :event/time :db/valueType :db.type/instant :db/cardinality :db.cardinality/one}]})
      (seed! ctx)
      (if interleaved
        (do (doseq [q [:price :repair :a1]] (open-question! ctx q []))
            (batch! ctx [:price :repair :a1]))
        (doseq [q [:price :repair :a1]] (open-question! ctx q []) (batch! ctx [q])))
      (doseq [[q prev] [[:a2 "result1"] [:a3 "result2"]]]
        (open-question! ctx q [prev])
        (when interleaved
          (let [other (put! ctx (str "codex-" (name q)) "Codex question in independent thread" "What is 30 squared?" [])]
            (put! ctx (str "other-" (name q)) "Fixture arithmetic result for Codex" "Other thread result = 900" [other]))) (batch! ctx [q]))
      (publish! ctx 'target-count 2 141)
      (let [cs (h/current-session owner)
            revised (:value (inspect-cell ctx (s/binding-cell cs 'marginal-result)))
            unchanged (some->> (get @(:cells ctx) :a3) (inspect-cell ctx) :value)
            _ (put! ctx "target-v2" "target quantity revision 2 supersedes revision 1" "141 copies" ["target"])
            _ (put! ctx "revised-quote" "quote observed after target revision 2"
                    (if (get-in @(:results ctx) [:price :result :verified]) (str revised) "original callable unavailable")
                    ["target-v2" "price-answer"])
            _ (open-question! ctx :asof ["target-v2"])
            _ (batch! ctx [:asof])
            summary {:mode mode :arm arm :interleaved interleaved :settings settings
                     :results @(:results ctx) :wake @(:wake ctx) :rejected-stale @(:stale ctx)
                     :mutation {:price (str revised) :unrelated (str unchanged) :pass (and (= revised 26150) (= unchanged 1))}
                     :calls (mapv #(dissoc % :text) @(:calls ctx))}]
        (save! dir "events.edn" (vec (entries ctx)))
        (save! dir "summary.edn" summary)
        (spit (io/file dir "summary.json") (json/write-json summary))
        (println (.getName dir) (mapv (fn [[q r]] [q (:pass r)]) (:results summary)) (:mutation summary)) (flush)
        (when (= "control" mode) (assert (and (every? :pass (vals (:results summary))) (get-in summary [:mutation :pass])))))
      (finally (h/close! owner)))))
(let [[mode path] *command-line-args*]
  (assert (#{"control" "live"} mode))
  (assert (.isAbsolute (io/file path)) "Supply an absolute evidence path")
  (try (doseq [[scenario arms] [["serial" [:full :selected]] ["overlap" [:selected :full]]] arm arms]
         (run-arm! (io/file path (str scenario "-" (name arm))) mode arm (= scenario "overlap")))
       (finally (shutdown-agents))))

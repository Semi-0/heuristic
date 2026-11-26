;; Loaded by probe.clj to share its Datomic graph, Soul worker and compiler.
(defn elapsed [start] (/ (- (System/nanoTime) start) 1e9))
(defn measure [calls k] (reduce + (map #(or (get-in % [:usage k]) (throw (ex-info "Missing usage" {:call (:id %)}))) calls)))
(defn metrics [calls start]
  {:seconds (elapsed start) :calls (count calls) :prompt-tokens (measure calls :prompt_tokens)
   :generated-tokens (measure calls :completion_tokens)})
(defn form! [source allowed]
  (let [form (:form (tools/read-draft source))]
    (when-not (and (list? form) (every? #(or (list? %) (vector? %) (integer? %) (contains? allowed %))
                                          (tree-seq sequential? seq form)))
      (throw (ex-info "Invalid Lain expression" {:source source})))
    form))
(defn run-expr [ctx id form]
  (let [cell (tools/extend-source! (:owner ctx) (binding! ctx 'repair-frame) id (pr-str form))]
    (:value (inspect-cell ctx cell))))
(defn author! [ctx]
  (let [reply (call! ctx :setup :author
                     "You are Lain. Write executable Lain. Return only a JSON array of three strings, each one (define NAME (network [n] EXPR)). Use integer literals and prefix + and *. No markdown."
                     "For this print shop define press-k: sheets for n bundles, 8 sheets per bundle plus 3 setup sheets. Define tariff-b: October rush cents for n sheets, 11 per sheet plus 40. Define tariff-m: November rush cents for n sheets, 9 per sheet plus 95." nil)
        sources (json/read-json reply)]
    (assert (= 3 (count sources)))
    (doseq [[sym source f] (map vector '[press-k tariff-b tariff-m] sources [#(+ 3 (* 8 %)) #(+ 40 (* 11 %)) #(+ 95 (* 9 %))])]
      (let [form (form! source (set ['define 'network sym 'n '+ '*]))]
        (assert (and (= ['define sym] (take 2 form)) (= 3 (count form))
                     (= '(network [n]) (take 2 (last form)))))
        (tools/extend-source! (:owner ctx) (binding! ctx 'repair-frame) [:authored sym] (pr-str form))
        (doseq [n [0 2 17]] (assert (= (f n) (run-expr ctx [:check sym n] (list sym n)))))))
    sources))
(defn seed-orders! [ctx ep sources]
  (doseq [[id label body parents]
          [["d1" "press setup" (first sources) []]
           ["d2" "October rush terms" (second sources) []]
           ["d3" "November rush terms" (last sources) []]
           ["i1" "Aster order as invoiced" (str "October 26. Original order: " (+ 12 ep) " bundles, rush.") ["d1" "d2"]]
           ["r1" "Aster revised order" (str "November 4. Revised order: " (+ 19 ep) " bundles, rush. Price at revision date.") ["d1" "d3"]]]]
    (put! ctx id label body parents))
  (doseq [n (range 18)]
    (put! ctx (str "shop-" n) (str "Other shop paperwork " n)
          (str "Birch job " n ", separate account. Supervisor recorded " (+ 13 n) " bundles for a preflight estimate. Its plate deposit and delivery terms are private to this other order. No revision to Aster.") [])))
(def answer-role
  (str "You are Lain handling overlapping print-shop requests. Use the provided archive. "
       "Return JSON only: {\"expr\":\"a Lain expression for the requested rush charge in cents\",\"evidence\":[\"supporting archive ID\"]}. "
       "Give all IDs supporting order, sheet calculation and applicable tariff. Available stored functions can be called directly by name. "
       "For missing evidence return {\"read\":[\"archive ID\"]} to retrieve it and its dependencies."))
(defn rows [es] (mapv #(select-keys % [:id :body :parents]) es))
(defn challenge! [ctx ep arm q]
  (let [start (System/nanoTime) offset (count @(:calls ctx))
        old? (= q :historical) n (+ ep (if old? 12 19)) ids [(if old? "i1" "r1") "d1" (if old? "d2" "d3")]
        tariff (if old? 'tariff-b 'tariff-m) expected ((if old? #(+ 40 (* 11 %)) #(+ 95 (* 9 %))) (+ 3 (* 8 n)))
        question (if old? "Codex: What rush charge applied to the Aster order as originally invoiced?" "Semi: What rush charge applies to the revised Aster order?")
        idx (when (= arm :selected) (mapv #(select-keys % [:id :label :parents]) (index-rows ctx)))
        initial (case arm :full (entries ctx) :oracle (expand ctx [(first ids)]) :selected [])
        result (try
                 (loop [events initial round 0]
                   (let [payload (pr-str (cond-> {:question question :events (rows events)} idx (assoc :index idx)))
                         _ (assert (< (count payload) (* 4 98304)))
                         reply (json/read-json (call! ctx (str (name arm) "-" (name q)) (str round) answer-role payload nil))
                         seen (set (map :id events))]
                     (if-let [requested (:read reply)]
                       (let [more (expand ctx requested)]
                         (if (and (vector? requested) (some (comp (complement seen) :id) more))
                           (recur (sort-by :sequence (vals (into {} (map (juxt :id identity) (concat events more))))) (inc round))
                           {:error "read added no evidence"}))
                       (let [form (form! (:expr reply) '#{press-k tariff-b tariff-m + - *})
                             value (run-expr ctx [arm q :application] form)
                             operators (set (map first (filter list? (tree-seq list? seq form))))
                             reuse (and (operators 'press-k) (boolean (operators tariff)))
                             evidence (= (set ids) (set (:evidence reply)))]
                         {:value (str value) :expected expected :correct (= value expected) :reuse (boolean reuse)
                          :version (and (boolean (operators tariff)) (not (operators (if old? 'tariff-m 'tariff-b))))
                          :supported (and evidence (every? seen ids)) :expr (pr-str form) :reads round}))))
                 (catch Exception e {:error (ex-message e)}))]
    (merge {:episode ep :arm arm :question q} result (metrics (subvec @(:calls ctx) offset) start))))
(defn run-reuse! [path ep]
  (let [dir (io/file path (str "episode-" ep)) _ (assert (.mkdirs dir)) owner (h/open! (model/compiler))
        ctx {:owner owner :dir dir :calls (atom []) :script (atom nil) :seq (atom 0)
             :store (catalog/open! (.getAbsolutePath (io/file dir "db")))}]
    (try
      (h/extend! owner (soul/extension owner (atom [])))
      (h/extend! owner (ttms/facilities))
      (h/extend! owner (model/declaration root "experiments/dependency-context/runtime.lain" {:transport (qwen/completion-transport settings)}))
      (d/transact (:connection (:store ctx)) {:tx-data [{:db/ident :event/seq :db/valueType :db.type/long :db/cardinality :db.cardinality/one}
                                                        {:db/ident :event/time :db/valueType :db.type/instant :db/cardinality :db.cardinality/one}]})
      (d/transact (:connection (:store ctx)) {:tx-data edge-schema})
      (let [start (System/nanoTime) sources (author! ctx)]
        (spit (io/file dir "setup.json") (json/write-json (metrics @(:calls ctx) start)))
        (seed-orders! ctx ep sources)
        (doseq [arm (nth [[:full :selected :oracle] [:selected :oracle :full] [:oracle :full :selected]] (mod (dec ep) 3))
                q (if (even? ep) [:historical :revision] [:revision :historical])]
          (let [r (challenge! ctx ep arm q)]
            (spit (io/file dir "scores.jsonl") (str (json/write-json r) "\n") :append true)
            (println r) (flush))))
      (finally (h/close! owner)))))
(let [[_ path] *command-line-args*]
  (try (doseq [ep [1 2 3]] (run-reuse! path ep)) (finally (shutdown-agents))))

;; Offline observations only: this file never starts a session or calls a model.
(require '[clojure.edn :as edn] '[clojure.string :as str] '[clojure.java.io :as io]
         '[graph.json :as json])
(def root (first *command-line-args*))
(defn read-artifact [dir file]
  (let [f (io/file root dir file)]
    (when (.exists f) (edn/read-string {:default tagged-literal} (slurp f)))))
(defn cost [rows]
  {:calls (count rows)
   :input-tokens (reduce + 0 (map #(or (get-in % [:usage :prompt_tokens]) 0) rows))
   :output-tokens (reduce + 0 (map #(or (get-in % [:usage :completion_tokens]) 0) rows))
   :latency-ms (reduce + 0 (map #(or (:elapsed-ms %) 0) rows))})
(defn summarize [dir]
  (when-let [r (or (read-artifact dir "final-result.edn") (read-artifact dir "recovered-result.edn"))]
    (let [initial (read-artifact dir "q0-topology.edn")
          costs (remove :replay? (:costs r))
          journal (read-artifact dir "final-catalog.edn")
          budgets (for [f (.listFiles (io/file root dir))
                        :when (str/ends-with? (.getName f) "-budget.edn")]
                    (assoc (read-artifact dir (.getName f)) :file (.getName f)))
          reads (second
                 (reduce (fn [[qid found] entry]
                           (cond
                             (and (= :archive (:operation entry)) (= :task (:kind entry)))
                             [(second (re-find #"q([0-9]+)/task" (str (:id entry)))) found]
                             (and (= :boundary (:operation entry)) (= :read (:capability entry)))
                             (let [result (:result entry)
                                   initial-id (:id (first (filter #(str/starts-with? (:id %) (str "q" qid "-")) (:calls r))))
                                   prompt (:text (read-artifact dir (str initial-id "-request.edn")))
                                   text (:text result)]
                               [qid (conj found {:task qid :id (:id result) :offset (:offset result)
                                                 :codepoints (:codepoints result) :initial-request initial-id
                                                 :absent-from-initial-prompt?
                                                 (when (and (string? text) (> (count text) 40) (string? prompt))
                                                   (not (str/includes? prompt text)))})])
                             :else [qid found])) [nil []] journal))
          reuse (into {}
                  (for [q (sort (remove zero? (keys (:correct r))))
                        :let [current (read-artifact dir (str "q" q "-topology.edn"))
                              prior (read-artifact dir (str "q" (dec q) "-topology.edn"))]]
                    [q (vec (for [[id first] initial
                                  :let [now (get current id)
                                        previous (set (map :application-id (:applications (get prior id))))]
                                  :when (and (= (:declaration first) (:declaration now))
                                             (= (:captured first) (:captured now)))
                                  a (:applications now)
                                  :when (and (not (previous (:application-id a))) (some #{q} (:delivers-to a)))]
                              {:operator id :application (:application-id a)
                               :arguments (:arguments a) :result (:result a)}))]))]
      {:condition dir :terminal (:terminal r) :unreached (:unreached r)
       :journal-complete? (not= false (:journal-complete? r)) :correct (:correct r) :indexing (:indexing r)
       :unresolved (vec (sort (for [[q outcome] (:outcomes r)
                                  :when (= :unresolved (:status outcome))] q)))
       :failed (if (= :host-failure (:terminal r))
                 (vec (sort (remove (into (set (keys (:outcomes r))) (:unreached r))
                                    (keys (:correct r))))) [])
       :within-budget? (:within-budget? r)
       :max-input-tokens (reduce max 0 (map :tokens (:calls r)))
       :max-measured-prompt-tokens (reduce max 0 (map :tokens budgets))
       :rejected-overflow-prompts (vec (filter #(> (:tokens %) (:limit %)) budgets))
       :cost (cost costs) :repairs (count (filter :repair? costs))
       :cost-by-stage (into {} (for [[stage rows] (group-by :stage costs)] [stage (cost rows)]))
       :cost-by-repair (into {} (for [[repair rows] (group-by :repair? costs)] [(str repair) (cost rows)]))
       :retrieved-evidence reads :new-applications-of-preparation-callables reuse})))
(let [summary (vec (keep summarize (or (seq (rest *command-line-args*)) ["preparation" "indexed" "full" "ablation-1" "ablation-4"])))]
  (spit (str root "/summary.edn") (pr-str summary))
  (spit (str root "/summary.json") (json/write-json summary))
  (prn (mapv #(dissoc % :new-applications-of-preparation-callables :retrieved-evidence) summary)))
(shutdown-agents)

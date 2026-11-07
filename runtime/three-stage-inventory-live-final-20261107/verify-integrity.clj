(require '[clojure.edn :as edn] '[clojure.string :as str])
(let [root (first *command-line-args*)
      runs (mapv #(edn/read-string {:default tagged-literal} (slurp (str root "/trial-" % "/evidence.edn"))) [1 2 3])
      expected ["analysis-1" "generalization-1" "round-1" "analysis-2" "generalization-2" "round-2"]
      reports (mapv
               (fn [e]
                 (let [requests (:requests e) replies (:phase-responses e)
                       pairs (map vector requests replies)
                       identities (keep :identity (concat (:analyses e) (:generalizations e) (:snapshots e)))
                       workers (map :worker (:snapshots e))]
                   {:operational-status (get-in e [:outcome :operational-status])
                    :request-count (count requests)
                    :phase-prefix-correct (= (vec (take (count requests) expected)) (mapv :id requests))
                    :observed-session-stable (= 1 (count (set identities)))
                    :observed-worker-stable (if (seq workers) (= 1 (count (set workers))) :not-observed)
                    :implementation-only-history (= (mapv :text (:responses e)) (:written-code e))
                    :unchanged-plans-in-implementation-context
                    (every? true? (for [[request _] pairs :when (str/starts-with? (:id request) "round-")
                                        :let [suffix (last (str/split (:id request) #"-"))]
                                        phase ["analysis" "generalization"]
                                        :let [reply (some (fn [[q r]] (when (= (:id q) (str phase "-" suffix)) r)) pairs)]]
                                    (and (string? (:text reply)) (str/includes? (:text request) (:text reply)))))
                    :generalizations (mapv #(select-keys (:record %) [:decision :operator :problem-class :parameters :captured :interface]) (:generalizations e))})) runs)
      summary {:trial-integrity reports
               :fresh-sessions (= 3 (count (set (map #(get-in % [:analyses 0 :identity]) runs))))
               :provider-requests (reduce + (map #(count (:requests %)) runs))}]
  (spit (str root "/integrity.edn") (pr-str summary))
  (prn summary))

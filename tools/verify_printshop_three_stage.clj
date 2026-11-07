(require '[clojure.edn :as edn] '[clojure.string :as str])
(let [root (first *command-line-args*)
      e (edn/read-string {:default tagged-literal} (slurp (str root "/evidence.edn")))
      pairs (map vector (:requests e) (:phase-responses e))
      expected (vec (for [i (range 1 6) stage ["analysis" "generalization" "round"]] (str stage "-" i)))
      identities (keep :identity (concat (:analyses e) (:generalizations e) (:snapshots e)))
      checks {:phase-order (= (vec (take (count (:requests e)) expected)) (mapv :id (:requests e)))
              :implementation-only-history (= (mapv (comp :text second) (filter #(str/starts-with? (:id (first %)) "round-") pairs)) (:written-code e))
              :observed-session-stable (= 1 (count (set identities)))
              :observed-worker-stable (if (seq (:snapshots e)) (= 1 (count (set (map :worker (:snapshots e))))) :not-observed)
              :unchanged-plans (every? true? (for [[request _] pairs :when (str/starts-with? (:id request) "round-")
                                                   :let [i (last (str/split (:id request) #"-"))]
                                                   stage ["analysis" "generalization"]
                                                   :let [reply (some (fn [[q r]] (when (= (:id q) (str stage "-" i)) r)) pairs)]]
                                               (and (string? (:text reply)) (str/includes? (:text request) (:text reply)))))}
      summary {:integrity checks :operational-status (get-in e [:outcome :operational-status])
               :requests (count (:requests e)) :implementation-responses (count (:responses e)) :written-source-responses (count (:written-code e))
               :interface-decisions (mapv #(select-keys (:record %) [:decision :operator :problem-class :parameters :captured :interface]) (:generalizations e))
               :selection-observations (mapv #(select-keys % [:round :selected-reference :selected-callable :selection-validation]) (:generalizations e))}]
  (spit (str root "/integrity.edn") (pr-str summary))
  (prn summary))

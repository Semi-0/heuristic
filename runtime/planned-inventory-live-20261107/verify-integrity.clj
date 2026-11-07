(require '[clojure.edn :as edn] '[clojure.string :as str])
(let [root (first *command-line-args*)
      read-edn #(edn/read-string {:default tagged-literal} (slurp %))
      runs (mapv #(read-edn (str root "/trial-" % "/evidence.edn")) [1 2 3])
      checks {:fresh-sessions (= 3 (count (set (map #(get-in % [:snapshots 0 :identity]) runs))))
              :four-requests-each (every? #(= 4 (count (:requests %))) runs)
              :phase-order (every? #(= ["analysis-1" "round-1" "analysis-2" "round-2"] (mapv :id (:requests %))) runs)
              :completed (every? #(= :completed (get-in % [:outcome :operational-status])) runs)
              :stable-identities (every? #(true? (get-in % [:outcome :stable-identity])) runs)
              :single-worker-each (every? #(= 1 (count (set (map :worker (:snapshots %))))) runs)
              :implementation-only-history (every? #(= (mapv :text (:responses %)) (:written-code %)) runs)
              :unchanged-plans-in-implementation-context
              (every? (fn [e] (and (= 4 (count (:requests e)))
                                   (every? true? (for [i [0 1]]
                                                   (str/includes? (:text (nth (:requests e) (inc (* 2 i))))
                                                                  (:text (nth (:phase-responses e) (* 2 i)))))))) runs)
              :corresponding-implementations-identical (apply = (map #(mapv :text (:responses %)) runs))}
      summaries (mapv (fn [e]
                        {:outcome (:outcome e)
                         :decisions (mapv #(select-keys (:record %) [:decision :operator :status]) (:analyses e))
                         :inspection-calls (mapv (fn [r] (vec (filter #(str/includes? (:text r) (str "(" %))
                                                                      ["inspect:cell" "inspect:premises" "inspect:errors" "environment:overview"]))) (:responses e))}) runs)]
  (spit (str root "/integrity.edn") (pr-str checks))
  (spit (str root "/plan-summary.edn") (pr-str summaries))
  (prn checks)
  (doseq [[i s] (map-indexed vector summaries)]
    (prn (inc i) (dissoc s :outcome))))

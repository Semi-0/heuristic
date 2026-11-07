(require '[clojure.edn :as edn] '[clojure.string :as str])
(let [root (first *command-line-args*)
      read-edn #(edn/read-string {:default tagged-literal} (slurp %))
      runs (mapv #(read-edn (str root "/trial-" % "/evidence.edn")) [1 2 3])
      checks {:fresh-sessions (= 3 (count (set (map #(get-in % [:snapshots 0 :identity]) runs))))
              :two-requests-each (every? #(= 2 (count (:requests %))) runs)
              :completed (every? #(= :completed (get-in % [:outcome :operational-status])) runs)
              :stable-identities (every? #(true? (get-in % [:outcome :stable-identity])) runs)
              :single-worker-each (every? #(= 1 (count (set (map :worker (:snapshots %))))) runs)
              :written-source-retained (every? #(= (mapv :text (:responses %)) (:written-code %)) runs)
              :corresponding-sources-identical (apply = (map #(mapv :text (:responses %)) runs))}
      inspections (mapv (fn [e]
                         (mapv (fn [r] (vec (filter #(str/includes? (:text r) (str "(" %))
                                                  ["inspect:cell" "inspect:premises" "inspect:errors" "environment:overview"])))
                               (:responses e))) runs)]
  (assert (every? true? (vals (dissoc checks :corresponding-sources-identical))))
  (spit (str root "/integrity.edn") (pr-str checks))
  (spit (str root "/inspection-usage.edn") (pr-str inspections))
  (prn checks) (prn {:inspection-calls inspections}))

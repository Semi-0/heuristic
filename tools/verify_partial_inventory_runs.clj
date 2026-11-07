;; Evidence integrity check; never invokes a model or changes recorded results.
(require '[clojure.edn :as edn])
(defn read-trials [root]
  (mapv #(edn/read-string {:default tagged-literal}
                         (slurp (str root "/trial-" % "/evidence.edn"))) [1 2 3]))
(defn behavior [e]
  (mapv (fn [s] {:stage (:stage s)
                 :results (into {} (map (fn [[n v]] [n (select-keys v [:value :support-names])]) (:results s)))})
        (:snapshots e)))
(let [[live-root replay-root] *command-line-args*
      live (read-trials live-root) replay (read-trials replay-root)
      checks {:six-provider-requests (= 6 (reduce + (map #(count (:requests %)) live)))
              :fresh-sessions (= 3 (count (set (map #(get-in % [:snapshots 0 :identity]) live))))
              :fresh-scoped-children (= 3 (count (set (map #(select-keys (first (:snapshots %)) [:identity :child]) live))))
              :persistent-workers (every? #(= 1 (count (set (map :worker (:snapshots %))))) live)
              :completed-real-trials (every? #(= :completed (get-in % [:outcome :operational-status])) live)
              :two-requests-per-real-trial (every? #(= 2 (count (:requests %))) live)
              :replay-only (every? #(= "replay" (:controlled %)) replay)
              :completed-replays (every? #(= :completed (get-in % [:outcome :operational-status])) replay)
              :unchanged-source (every? true? (map #(= (mapv :text (:responses %1)) (mapv :text (:responses %2))) live replay))
              :unchanged-values-and-semantic-supports (= (mapv behavior live) (mapv behavior replay))
              :source-reducers-preserved (every? #(= (mapv :text (:responses %)) (:written-code %)) replay)
              :reply-reducers-preserved (every? #(and (vector? (:history %)) (= (:history %) (:history (last (:rounds %))))) replay)}]
  (spit (str replay-root "/integrity.edn") (pr-str checks))
  (doseq [[check pass] checks] (prn check pass))
  (assert (every? true? (vals checks)) "Evidence integrity failure"))

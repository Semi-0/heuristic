(require '[clojure.edn :as edn] '[clojure.string :as str])
(defn worker-identity [worker]
  ;; Older evidence printed WorkerHandle records with an EDN tag; snapshots
  ;; printed the same identity fields as maps. Compare the fields in both cases.
  (into {} (if (instance? clojure.lang.TaggedLiteral worker) (:form worker) worker)))
(let [root (first *command-line-args*)
      e (edn/read-string {:default tagged-literal} (slurp (str root "/evidence.edn")))
      pairs (map vector (:requests e) (:phase-responses e))
      expected (vec (for [i (range 1 6) stage ["analysis" "generalization" "round"]] (str stage "-" i)))
      observations (concat (:snapshots e) (map :observation (:recovery-attempts e)))
      identities (keep :identity (concat (:analyses e) (:generalizations e) observations))
      checks {:phase-order (let [base (remove #(str/includes? (:id %) "-repair-") (:requests e))]
                             (= (vec (take (count base) expected)) (mapv :id base)))
              :correction-budget (every? #(<= (count %) 2) (vals (group-by #(second (str/split (:id %) #"-")) (filter #(str/includes? (:id %) "-repair-") (:requests e)))))
              :failure-observations-in-correction-context
              (every? true? (for [[index request] (map-indexed vector (:requests e))
                                  :when (str/includes? (:id request) "-repair-")
                                  :let [previous (:id (nth (:requests e) (dec index)))
                                        feedback (first (filter #(= previous (:failed-response %)) (:recovery-feedback e)))
                                        section (some-> (:text request)
                                                        (str/split #"## inspection-feedback\n" 2) second
                                                        (str/split #"\n\n## " 2) first)]]
                              (and feedback section
                                   (str/includes? section (pr-str (:failed-source feedback)))
                                   (str/includes? section ":accepted-forms")
                                   (str/includes? section ":corrections-remaining"))))
              :implementation-only-history (= (mapv (comp :text second) (filter #(str/starts-with? (:id (first %)) "round-") pairs)) (:written-code e))
              :observed-session-stable (= 1 (count (set identities)))
              :observed-child-stable (if (seq observations) (= 1 (count (set (map :child observations)))) :not-observed)
              :observed-worker-stable (if (seq observations) (= 1 (count (set (map (comp worker-identity :worker) observations)))) :not-observed)
              :unchanged-plans (every? true? (for [[request _] pairs :when (str/starts-with? (:id request) "round-")
                                                   :let [i (second (str/split (:id request) #"-"))]
                                                   stage ["analysis" "generalization"]
                                                   :let [reply (some (fn [[q r]] (when (= (:id q) (str stage "-" i)) r)) pairs)]]
                                               (and (string? (:text reply)) (str/includes? (:text request) (:text reply)))))}
      summary {:integrity checks :operational-status (get-in e [:outcome :operational-status])
               :requests (count (:requests e)) :implementation-responses (count (:responses e)) :written-source-responses (count (:written-code e))
               :correction-attempts (count (filter #(pos? (:correction %)) (:recovery-attempts e)))
               :interface-decisions (mapv #(select-keys (:record %) [:decision :operator :problem-class :parameters :captured :interface]) (:generalizations e))
               :selection-observations (mapv #(select-keys % [:round :selected-reference :selected-callable :selection-validation]) (:generalizations e))}]
  (spit (str root "/integrity.edn") (pr-str summary))
  (prn summary))

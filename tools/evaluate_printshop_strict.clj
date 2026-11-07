(require '[clojure.edn :as edn] '[clojure.string :as str])
(def fields ["production" "rush" "discount" "subtotal" "delivery" "tax" "total"])
(def expected [[160 0 0 185 12 15.76M 212.76M]
               [424 84.8M 40 493.8M 0 39.504M 533.30M]
               [1126 0 40 1111 0 88.88M 1199.88M]
               [180 36 21.6M 219.4M 12 18.512M 249.91M]
               [1126 225.2M 75 1301.2M 0 104.096M 1405.30M]])
(defn deps [sources]
 (reduce (fn [g f]
  (cond
   (and (seq? f) (= 'define (first f))) (assoc g (second f) (set (filter symbol? (tree-seq coll? seq (nth f 2 nil)))))
   (and (seq? f) (= '-> (first f)))
    (let [target (last f) n (if (symbol? target) target (last target))]
     (if (symbol? n) (update g n (fnil into #{}) (filter symbol? (tree-seq coll? seq (second f)))) g))
   :else g)) {} (mapcat #(edn/read-string (str "[" % "]")) sources)))
(defn ancestry [g n]
 (loop [pending [n] seen #{}]
  (if-let [x (peek pending)]
   (if (seen x) (recur (pop pending) seen) (recur (into (pop pending) (g x)) (conj seen x))) seen)))
(let [root (first *command-line-args*)
      e (edn/read-string {:default tagged-literal} (slurp (str root "/evidence.edn")))
      stages (into {} (map (juxt :stage identity) (:snapshots e)))
      checks (atom [])
      check (fn [label expected actual]
       (swap! checks conj {:check label :expected expected :actual actual
                          :pass (if (and (number? expected) (number? actual)) (< (Math/abs (- (double expected) (double actual))) 1e-7) (= expected actual))}))
      result (fn [stage field i] (get-in stages [stage :results (symbol (str "observed-" field "-" i))]))
      reuse (into {} (for [i (range 2 (inc (count (:rounds e))))
                           :let [prior (take (dec i) (:rounds e)) current (nth (:rounds e) (dec i))
                                 before-ids (set (map :application-id (mapcat :applications prior)))
                                 g (deps (map :text (take i (:responses e))))
                                 path (ancestry g (symbol (str "quote-" i)))
                                 candidates (for [r prior [n b] (:bindings r) :when (= :callable (:kind b))
                                                  :let [calls (filter #(and (= (:declaration b) (:declaration %))
                                                                            (some? (:declaration b))
                                                                            (= (:captured-environment b) (:captured-environment %))
                                                                            (not (before-ids (:application-id %)))) (:applications current))]
                                                  :when (and (seq calls) (path n)
                                                   (= (:reference b) (get-in stages [(keyword (str "round-" i)) :child-bindings n :reference])))]
                                              {:name n :defined-round (:round r) :invocations (vec calls)})]]
                      [i (vec candidates)]))]
 (doseq [i (range 1 6) [field exp] (map vector fields (nth expected (dec i)))]
   (check [:initial i field] exp (:value (result (keyword (str "round-" i)) field i))))
 (doseq [i (range 1 6) :let [r (result (keyword (str "round-" i)) "total" i)
                            supports (:support-names r)]]
   (check [:question-support i] true (contains? supports (symbol (str "question-" i))))
   (check [:rule-support i] true (contains? supports 'shared-rule))
   (check [:no-foreign-order-support i] #{} (set (filter #(and (symbol? %)
     (when-let [[_ suffix] (re-find #"-(\d)$" (str %))] (not= (str i) suffix))) supports))))
 (check :quantity-correction 277.56M (:value (result :quantity-corrected "total" 1)))
 (check :promotion-removal 1443.10M (:value (result :promotion-removed "total" 5)))
 (check :promotion-restoration 1405.30M (:value (result :promotion-restored "total" 5)))
 (check :quantity-withdrawal :unknown (:value (result :quantity-withdrawn "total" 2)))
 (check :quantity-restoration 533.30M (:value (result :quantity-restored "total" 2)))
 (check :question-withdrawal :unknown (:value (result :question-withdrawn "total" 1)))
 (check :question-restoration 277.56M (:value (result :question-restored "total" 1)))
 (doseq [i (range 1 5)]
   (check [:promotion-isolated i] (select-keys (result :quantity-corrected "total" i) [:value :support])
                                (select-keys (result :promotion-removed "total" i) [:value :support])))
 (doseq [i (range 1 6)]
   (check [:unrelated-invariance i] (select-keys (result :question-restored "total" i) [:value :support])
                                   (select-keys (result :unrelated-change "total" i) [:value :support])))
 (check :request-count 10 (count (:requests e)))
 (check :analysis-count 5 (count (:analyses e)))
 (check :phase-response-count 10 (count (:phase-responses e)))
 (doseq [[i a] (map-indexed vector (:analyses e))]
   (check [:analysis-before-implementation (inc i)] (inc (* 2 i)) (:request-count a)))
 (check :source-history (mapv :text (:responses e)) (:written-code e))
 (check :reply-history (:history (last (:rounds e))) (:history e))
 (check :stable-session true (get-in e [:outcome :stable-identity]))
 (check :persistent-worker 1 (count (set (map :worker (:snapshots e)))))
 (let [report {:operational-status (get-in e [:outcome :operational-status])
               :error (get-in e [:outcome :error]) :provider-requests (if (:controlled e) 0 (count (:requests e)))
               :analysis-records (:analyses e) :reuse reuse
               :self-checks (into {} (for [i (range 1 6)] [i (get-in stages [(keyword (str "round-" i)) :results (symbol (str "self-check-" i)) :value])]))
               :checks @checks}]
  (spit (str root "/evaluation.edn") (pr-str report))
  (prn (dissoc report :checks :reuse))
  (prn :reused (into {} (map (fn [[i xs]] [i (mapv :name xs)]) reuse)))
  (prn :failed (vec (remove :pass @checks)))
  (when (:controlled e)
   (assert (= :completed (:operational-status report)))
   ;; Baseline runtime issue already established in the one-phase control.
   (assert (= #{:quantity-correction :question-restoration}
              (set (map :check (remove :pass @checks)))) "Unexpected control failure set")
   (assert (every? seq (vals reuse)))
   (assert (every? true? (vals (:self-checks report))))
   (println "Strict phase gating, pricing and reuse checks passed; two known baseline mutation failures retained."))))

(require '[clojure.edn :as edn]
         '[clojure.string :as str])

(defn rendered-history [text]
  (when-let [section (second (re-find #"(?ms)^## history\n(.*?)(?=\n\n##|\z)" text))]
    (edn/read-string section)))

(let [root (first *command-line-args*)
      evidence (edn/read-string {:default tagged-literal} (slurp (str root "/evidence.edn")))
      requests (:requests evidence)
      snapshots (into {} (map (juxt :stage identity) (:snapshots evidence)))
      round-ids (mapv :round (:rounds evidence))
      question (fn [i] (some #(when (= (:id %) (str "analysis-" i))
                                (get-in % [:messages 1 :content])) requests))
      reply (fn [i] (get-in snapshots [(keyword (str "round-" i)) :results
                                       (symbol (str "user-output-" i)) :value]))
      expected (vec (mapcat (fn [i] [{:role "user" :content (question i)}
                                     {:role "assistant" :content (reply i)}]) (range 1 6)))
      checks {:completed-five-rounds (and (= :completed (get-in evidence [:outcome :operational-status]))
                                          (= [1 2 3 4 5] round-ids))
              :actual-replies (every? #(and (string? %) (not (str/blank? %))) (map reply (range 1 6)))
              :reply-total-amounts
              (every? (fn [i]
                        (let [amount (get-in snapshots [(keyword (str "round-" i)) :results
                                                        (symbol (str "observed-total-" i)) :value])
                              numbers (re-seq #"[-+]?\d[\d,]*(?:\.\d+)?" (or (reply i) ""))]
                          (and (number? amount)
                               (boolean (some #(== amount (bigdec (str/replace % "," ""))) numbers)))))
                      (range 1 6))
              :exact-delivered-history (= expected (:history evidence))
              :each-round-history (every? #(= (subvec expected 0 (* 2 (:round %))) (:history %)) (:rounds evidence))
              :explicit-roles (every? #(= ["system" "user"] (mapv :role (:messages %))) requests)
              :system-is-described-environment (every? #(= (:text %) (get-in % [:messages 0 :content])) requests)
              :history-in-every-request
              (every? (fn [request]
                        (let [i (Long/parseLong (second (str/split (:id request) #"-")))
                              prefix (subvec expected 0 (dec (* 2 i)))]
                          (and (= (question i) (get-in request [:messages 1 :content]))
                               (= prefix (rendered-history (:text request)))))) requests)}
      result {:checks checks :requests (count requests) :delivered-messages (count (:history evidence))}]
  (spit (str root "/history-verification.edn") (pr-str result))
  (prn result)
  (assert (every? true? (vals checks)) "Conversation history or request encoding did not match delivered messages"))

(require '[clojure.edn :as edn] '[clojure.string :as str]
         '[lain-harness.soul-session :as soul])
(let [root (System/getenv "LAIN_HARNESS_ROOT")
      read-edn #(edn/read-string {:default tagged-literal} %)
      requests (read-edn (slurp (str root "/runtime/soul-attempt-13/soul-requests.edn")))
      source (slurp (str root "/soul-refined.lain"))
      library (slurp (str root "/lib/language-context.lain"))
      literal (fn [text name]
                (let [prefix (str "(define " name "\n")
                      index (.indexOf text prefix)]
                  (when (>= index 0)
                    (let [tail (str/triml (subs text (+ index (count prefix))))]
                      (when (str/starts-with? tail "\"") (read-edn tail))))))]
  (doseq [[index phase] [[0 "orientation"] [1 "construction"]]]
    (let [entries (read-edn (:text (nth requests index)))
          selection-name (str phase "-selection")
          selection-text (second (str/split source (re-pattern (str "\\(define " selection-name)) 2))
          selection-text (first (str/split selection-text #"\(define " 2))
          selected (mapv second (re-seq #"\(context:include \"([^\"]+)\"\)" selection-text))
          entries (conj (mapv (fn [entry]
                                (let [name (:title entry)
                                      updated (or (when (= name "context-cell")
                                                    (literal source (if (zero? index) "orientation-context-cell" "conversation-context-cell")))
                                                  (literal source name) (literal library name))]
                                  (cond-> entry
                                    (= "unknown" (:headline entry)) (assoc :state :unknown)
                                    updated (assoc :content updated)))) entries)
                        {:title "role-cell" :state :known :content (literal source "role-cell")
                         :headline "Agent identity and operating responsibilities"})
          result (with-redefs [soul/overview (fn [_ _] entries)]
                   (soul/describe nil [nil selected]))]
      (assert (< (.indexOf result "## role-cell") (.indexOf result "## context-cell")))
      (assert (str/includes? result "## user-ingress\nState: unknown"))
      (assert (str/includes? result "You are the agent operating inside"))
      (when (= phase "construction")
        (assert (str/includes? result "Purpose: Append one element"))
        (assert (str/includes? result "doc:changing-reading")))
      (spit (str root "/runtime/context-redesign-" phase ".txt") result)
      (println phase (count result) "characters; checks passed"))))

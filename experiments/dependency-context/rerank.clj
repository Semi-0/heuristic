(let [python (or (System/getenv "LAIN_RERANK_PYTHON") "python3")
      model-path (or (System/getenv "LAIN_RERANK_MODEL") (throw (ex-info "Set LAIN_RERANK_MODEL" {})))
      process (-> (ProcessBuilder. ^java.util.List [python (str root "/experiments/dependency-context/reranker.py") model-path])
                  (.redirectError java.lang.ProcessBuilder$Redirect/INHERIT) (.start))
      out (io/file (second *command-line-args*))]
  (try
    (with-open [reader (io/reader (.getInputStream process)) writer (io/writer (.getOutputStream process))]
      (let [receive (fn [] (json/read-json (or (.readLine reader) (throw (ex-info "Reranker exited" {})))))
            ready (receive)
            score (fn [request]
                    (.write writer (str (json/write-json request) "\n"))
                    (.flush writer)
                    (receive))
            route (fn [question evidence]
                    (score {:instruction "Classify the reasoning effort this question requires given the evidence. Select the description that best fits the work needed for a correct grounded answer."
                            :question (str question "\nAvailable evidence: " (pr-str (rows evidence)))
                            :documents [{:id "low" :label "Low effort" :body "Direct lookup or straightforward application of an explicit rule; evidence and required inputs are unambiguous."}
                                        {:id "medium" :label "Medium effort" :body "Several linked records or calculations must be combined; a date, revision, or condition determines which rule applies."}
                                        {:id "xhigh" :label "Extra high effort" :body "Conflicting or incomplete evidence, novel planning or a complex derivation needs extended checking."}]}))
            rank (fn [ctx question]
                   (let [documents (mapv #(select-keys % [:id :label :body]) (entries ctx))]
                     (score {:question question :documents documents})))]
        (assert (:ready ready))
        (.mkdirs out)
        (spit (io/file out "worker.json") (json/write-json ready))
        (doseq [ep [1 2 3]]
          (run-reuse! (.getAbsolutePath out) ep
                      (fn [ctx _]
                        (doseq [q (if (even? ep) [:historical :revision] [:revision :historical])]
                          (let [r (challenge! (cond-> (assoc ctx :rank rank) (= "routed" (first *command-line-args*)) (assoc :route route)) ep :reranked q)]
                            (spit (io/file (:dir ctx) "scores.jsonl") (str (json/write-json r) "\n") :append true)
                            (println "reranked" ep q (select-keys r [:correct :supported :seconds :generated-tokens]))
                            (flush))))))))
    (finally (.destroy process))))

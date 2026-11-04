(load-file (str (System/getenv "LAIN_HARNESS_ROOT") "/tools/offline_uuid.clj"))
(require '[clojure.edn :as edn] '[clojure.test :as test])

(defn run-module! [modules module]
  (let [{:keys [namespaces vars description] :as config} (get modules module)]
    (when-not config
      (throw (ex-info "Unknown test module" {:module module :available (keys modules)})))
    (println "MODULE" (name module) "—" description)
    (doseq [ns namespaces] (require ns))
    (let [selected-vars (vec (distinct
                             (concat (sort-by str (filter #(-> % meta :test)
                                                        (mapcat #(vals (ns-publics %)) namespaces)))
                                     (map requiring-resolve vars))))
          result (binding [test/*report-counters* (ref test/*initial-report-counters*)]
                   (when (empty? selected-vars)
                     (throw (ex-info "Module has no tests" {:module module})))
                   (test/test-vars selected-vars)
                   (let [r @test/*report-counters*]
                     (test/do-report (assoc r :type :summary))
                     r))]
      (println "MODULE-RESULT" (name module) (pr-str result))
      (flush)
      (when (pos? (+ (:fail result) (:error result)))
        (shutdown-agents)
        (System/exit 1)))))

(let [root (System/getenv "LAIN_HARNESS_ROOT")
      modules (edn/read-string (slurp (str root "/test/modules.edn")))
      requested (or (first *command-line-args*) "list")
      selected (case requested
                 "all" [:foundation :transport :model :integration]
                 "staged" [:transport :model :user-io :integration]
                 "list" []
                 [(keyword requested)])]
  (if (empty? selected)
    (doseq [[name {:keys [description live]}] (sort-by key modules)]
      (println (clojure.core/name name) (if live "[LIVE]" "[deterministic]") description))
    (doseq [module selected] (run-module! modules module))))
(shutdown-agents)

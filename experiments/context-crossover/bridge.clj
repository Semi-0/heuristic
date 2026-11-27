;; The editor modules live in the editor checkout. Load its actual projection.
(let [editor (System/getenv "LAIN_EDITOR_ROOT")
      wanted '#{feedback-message estimated-tokens recent-messages request-messages}
      target (create-ns 'crossover.editor-projection)]
  (load-file (str editor "/src/lain_harness/conversation_journal.clj"))
  (binding [*ns* target]
    (clojure.core/refer 'clojure.core)
    (alias 'journal 'lain-harness.conversation-journal)
    (with-open [r (java.io.PushbackReader. (io/reader (str editor "/experiments/editor-chat/editor_compaction.clj")))]
      (loop [found #{}]
        (let [form (read {:eof ::end} r)]
          (cond
            (= form ::end) (assert (= wanted found))
            (and (list? form) (= 'defn- (first form)) (wanted (second form)))
            (do (eval form) (recur (conj found (second form))))
            :else (recur found)))))))
(def owner (h/open! (model/compiler)))
(def dest (io/file (second *command-line-args*)))
(def ctx {:dir dest :owner owner :calls (atom []) :script (atom nil)
          :store (catalog/open! (.getAbsolutePath (io/file dest "db")))})
(def history (atom []))
(def library (atom {}))
(def project (ns-resolve 'crossover.editor-projection 'request-messages))
(def present (ns-resolve 'lain-harness.conversation-journal 'provider-message))
(defn output! [x] (println (str "RPC " (json/write-json x))) (flush))
(defn ingest! [records]
  (doseq [batch (partition-all 128 records)]
    (let [local (set (map :id batch))]
      (d/transact (:connection (:store ctx))
                {:tx-data (mapv (fn [{:keys [id label body parents seq]}]
                                  (cond-> {:db/id id :catalog/id (keyword id) :catalog/interface label :catalog/description (if (System/getenv "LAIN_ARCHIVE_EXTERNAL_TEXT")
                                                                  (subs body 0 (min 512 (count body))) body)
                                           :event/seq seq :event/time (java.util.Date. (long seq))}
                                    (not-empty parents) (assoc :catalog/depends-on (mapv #(if (local %) % [:catalog/id (keyword %)]) parents))))
                                batch)})))
  (swap! history into (map (fn [{:keys [id label body parents kind seq]}]
                            {:id id :seq seq :kind (keyword kind) :speaker (if (= kind "tool-output") :cli :fixture)
                             :depends-on parents :text (str label "\n" body)}) records))
  (count @history))
(defn handle [{:keys [op args]}]
  (case op
    "install" (let [{:keys [file symbol]} args
                    line (first (filter #(str/starts-with? % (str "(define " symbol " (network "))
                                        (str/split-lines (slurp (io/file root file)))))
                    _ (assert line)
                    decl (tools/extend-source! owner (binding! ctx 'repair-frame) [:library file symbol] line)
                    row {:file file :symbol symbol :source line :cell (str decl)}]
                (swap! library assoc symbol row) row)
    "ingest" (ingest! (:records args))
    "expand" (mapv #(select-keys % [:id :body :label :parents]) (expand ctx (:ids args)))
    "project" (let [{:keys [role question arm]} args
                    priors (into [{:id "system" :kind :environment :text role}] @history)
                    pending {:kind :user :speaker :semi :id "current" :text question}
                    messages (if (= "editor" arm) (project priors pending []) (mapv present (conj priors pending)))]
                messages)
    "solve" (let [answer (call! ctx (:id args) "flash" nil (:messages args) nil)]
              (assoc (select-keys (last @(:calls ctx)) [:usage :seconds :finish-reason]) :text answer))
    "execute" (let [expr (:expr args)
                    form (:form (tools/read-draft expr))
                    allowed (set (map clojure.core/symbol (keys @library)))
                    _ (when-not (and (list? form) (every? #(or (integer? %) (list? %) (allowed %)) (tree-seq list? seq form)))
                        (throw (ex-info "Expected an invocation of an installed Lain callable" {})))
                    cell (tools/extend-source! owner (binding! ctx 'repair-frame) [:answer (:id args)] (pr-str form))
                    called (mapv #(get @library (str (first %))) (filter list? (tree-seq list? seq form)))]
                {:value (:value (ttms/inspect-value (s/network (h/current-session owner)) cell)) :called called :application (str cell)})))
(try
  (h/extend! owner (soul/extension owner (atom [])))
  (h/extend! owner (ttms/facilities))
  (h/extend! owner (model/declaration root "experiments/dependency-context/runtime.lain"
                                   {:transport (qwen/completion-transport (assoc settings :max-tokens 12288))}))
  (d/transact (:connection (:store ctx)) {:tx-data [{:db/ident :event/seq :db/valueType :db.type/long :db/cardinality :db.cardinality/one}
                                                    {:db/ident :event/time :db/valueType :db.type/instant :db/cardinality :db.cardinality/one}]})
  (d/transact (:connection (:store ctx)) {:tx-data edge-schema})
  (output! {:ready true})
  (doseq [line (line-seq (java.io.BufferedReader. *in*))]
    (try (output! {:ok (handle (json/read-json line))})
         (catch Exception e (output! {:error (str (ex-message e))}))))
  (finally (h/close! owner) (shutdown-agents)))

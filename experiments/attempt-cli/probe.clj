(load-file (str (System/getenv "LAIN_HARNESS_ROOT") "/tools/offline_uuid.clj"))
(load-file (str (System/getenv "LAIN_HARNESS_ROOT") "/experiments/attempt-cli/tools.clj"))
(require '[attempt-cli.tools :as t] '[lain-harness.blazux :as qwen] '[lain-harness.catalog :as catalog]
         '[lain-harness.experiment-observation :as obs] '[lain-harness.soul-session :as soul]
         '[lain-harness.model-io-session :as model] '[lain-harness.premise-experiment :as premise]
         '[lain-harness.session-publication :as pub] '[propagators.compiler-2.session :as s]
         '[propagators.compiler-2.session.host :as h] '[clojure.java.io :as io]
         '[clojure.data.json :as json] '[clojure.string :as str])
(def settings
  (let [[_ _ max-tokens timeout-ms] *command-line-args*]
    (assoc qwen/sampling-defaults
           :max-tokens (Long/parseLong (or max-tokens "512"))
           :timeout-ms (Long/parseLong (or timeout-ms "75000")))))
(def base-role (str "You are a planner working on one attempt. You choose the next action. Respond with ONE action per turn, up to four turns. No Markdown fences. "
 "NOTE followed by text records a public decision and continues; FINAL followed by text ends the session. "
 "HISTORY all, HISTORY open or HISTORY contradicted queries this shop's earlier attempts (two reads). "
 "INVOICE 90 or INVOICE 120 obtains one additional invoice (one total). QUOTE rule invoice-id applies an existing callable at the target and checks support; rule names are flat, marginal, whole. "
 "SUBMIT on a line alone followed by one complete Lain definition on the next line tests your callable and finishes on success. Example for an unrelated function: (define price (network [n] (+ n 5))). "
 "Lain has + - * <= and (if condition then else). A failed check returns a diagnostic. Use any available actions as useful."))
(def cli-description (str " CLI python3 -c on a line followed by Python source runs a scratch calculation. CLI lain-read on a line checks draft.lain, or follow it with replacement Lain source to check that revision. Maximum two CLI actions."))
(def tasks {"price" "Shop p1: an 80-copy invoice was 16000 cents. Flat costs 175 cents/copy. Marginal charges 200 each through 100 and 150 on the excess. Whole charges 200 each through 100 and 150 on ALL copies for larger orders. Quote 140 copies under the current policy using a justified existing callable and the identifying invoice."
            "repair" (str "Shop p1: verified 120-copy invoice invoice-120 is 23000 cents; the marginal rule is established. Repair and submit the supplied executable Lain price function. Its current draft is:\n" t/broken)})
(def scripts {"price" ["NOTE Both survivors fit the known observation." "CLI python3 -c\nprint(20000+20*150,120*150)" "INVOICE 120" "QUOTE marginal invoice-120"]
              "repair" ["CLI lain-read" (str "SUBMIT\n" t/broken) (str "CLI lain-read\n" t/good) (str "SUBMIT\n" t/good)]})
(defn run-arm [dest mode fixture arm]
  (let [folder (io/file dest (str fixture "-" arm)) _ (when-not (.mkdir folder) (throw (ex-info "Expected new evidence directory" {:path (str folder)})))
        file #(io/file folder %) scratch (file "scratch") _ (.mkdir scratch)
        _ (spit (io/file scratch "draft.lain") (if (= "repair" fixture) t/broken ""))
        _ (spit (io/file scratch "task.txt") (tasks fixture))
        save (fn [name obj] (spit (file name) (json/write-str obj :default str)))
        cli? (= "cli" arm) calls (atom []) journal (atom []) admissions (atom []) counters (atom {})
        archive (catalog/open! (.getAbsolutePath (file "db")))
        rows (concat [[:flat :contradicted "Flat predicted 14000 for invoice-80, observed 16000, policy p1."]
                      [:probe :checked "invoice-90 observed 18000; marginal and whole both match."]]
                     (if (= "repair" fixture)
                       [[:marginal :verified "invoice-120 at policy p1: 23000. Marginal matched."] [:whole :contradicted "Whole predicted 18000 on invoice-120."]]
                       [[:marginal :open "Marginal open under p1."] [:whole :open "Whole open under p1."]]))
        _ (catalog/seed! archive (mapv (fn [[id status desc]] {:catalog/id id :catalog/status status :catalog/description desc}) rows))
        cfg {:fixture fixture :cli? cli? :archive archive :counters counters :dir scratch}
        provider (qwen/completion-transport settings)
        transport (fn [req]
                    (let [i (count @calls) id (:id req)]
                      (save (str id "-request.json") (:messages req))
                      (swap! calls conj id)
                      (let [r (cond
                                (and (= "guided-repair-cli" mode) (zero? i))
                                {:kind :reply :finish-reason "stop" :truncated? false :text "CLI lain-read"}
                                (= "control" mode)
                                {:kind :reply :finish-reason "stop" :truncated? false :text (nth (scripts fixture) i)}
                                :else (provider req))]
                        (save (str id "-response.json") r)
                        (when (string? (:thinking r))
                          (spit (file (str id "-thinking.txt")) (:thinking r) :encoding "UTF-8"))
                        r)))
        owner (h/open! (model/compiler))
        start (System/nanoTime)]
    (try
      (h/extend! owner (soul/extension owner admissions))
      (h/extend! owner (obs/facilities))
      (h/extend! owner (premise/facilities))
      (h/extend! owner (t/facilities cfg journal))
      (h/extend! owner (model/declaration (System/getenv "LAIN_HARNESS_ROOT") "experiments/attempt-cli/runtime.lain" {:transport transport}))
      (doseq [[n x] [['policy "p1"] ['target-count 140]]]
        (h/extend! owner (pub/declaration (h/current-session owner) n 1 :active x)))
      (h/publish-input! owner (fn [ss] [(s/publication-input (s/binding-cell ss 'spec)
        {:role (str base-role (when cli? cli-description)) :task (tasks fixture)})]))
      (h/step! owner)
      (loop [deadline (+ (System/currentTimeMillis) (* 4 (:timeout-ms settings)) 20000)]
        (when-let [e (first (h/failures owner))] (throw e))
        (when-not (or (:done (last @journal)) (>= (count @journal) 4))
          (when (> (System/currentTimeMillis) deadline) (throw (ex-info "Arm deadline" {:calls @calls})))
          (Thread/sleep 75) (recur deadline)))
      (when (= "control" mode)
        (let [feedback (fn [i] (slurp (file (str (nth @calls i) "-request.json"))))]
          (assert (:verified (last @journal)) (pr-str @journal))
          (if (= fixture "price")
            (assert (str/includes? (feedback 2) "23000 18000"))
            (do (assert (str/includes? (feedback 1) "Missing `)`"))
                (assert (str/includes? (feedback 3) "One complete form"))))))
      (catch Throwable e (save "exception.json" {:message (ex-message e) :data (str (ex-data e))})
             (when (= mode "control") (throw e)))
      (finally
        (save "result.json" {:mode mode :fixture fixture :arm arm :calls @calls :settings settings :actions @journal
                             :verified (true? (:verified (last @journal))) :elapsed-seconds (/ (- (System/nanoTime) start) 1e9)})
        (try (save "close.json" (h/close! owner))
             (catch Exception e (save "close.json" {:error (ex-message e)})))
        (println fixture arm (mapv :action @journal) (:verified (last @journal))) (flush)))))
(let [[mode dest] *command-line-args*]
  (try (doseq [[fixture arm] (case mode "control" [["price" "cli"] ["repair" "cli"]]
                               "remaining-repair" [["repair" "cli"] ["repair" "base"]]
                               ("guided-repair-cli" "repair-cli") [["repair" "cli"]]
                               [["price" "base"] ["price" "cli"] ["repair" "cli"] ["repair" "base"]])]
         (run-arm dest mode fixture arm))
       (finally (shutdown-agents))))

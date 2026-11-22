(load-file (str (System/getenv "LAIN_HARNESS_ROOT") "/tools/offline_uuid.clj"))
(require '[lain-harness.soul-session :as soul] '[lain-harness.premise-experiment :as premise]
         '[lain-harness.session-publication :as pub] '[lain-harness.experiment-observation :as obs]
         '[lain-harness.model-io-session :as model] '[lain-harness.blazux :as qwen]
         '[propagators.compiler-2.runtime.application :as app] '[propagators.network :as net]
         '[propagators.compiler-2.session :as s] '[propagators.compiler-2.session.host :as host]
         '[propagators.compiler-2.session.connected :as connected] '[clojure.data.json :as json])
(def root (System/getenv "LAIN_HARNESS_ROOT"))
(def out (System/getenv "OUT"))
(def control (= "control" (System/getenv "MODE")))
(def executable (= "executable" (System/getenv "MODE")))
(def clear (= "clear" (System/getenv "MODE")))
(def logic {"plan" "(-> \"ambiguous\" assessment) (-> 101 probe-count) (-> \"marginal\" job-a) (-> \"volume\" job-b)"
            "a" "(define cost (network [n] (if (<= n 100) (* n 200) (+ 20000 (* (- n 100) 150))))) (-> cost quote-output)"
            "b" "(define cost (network [n] (if (<= n 100) (* n 200) (* n 150)))) (-> cost quote-output)"})
(def provider (qwen/completion-transport {:max-tokens 4096 :enable-thinking true :reasoning-effort "medium" :thinking-token-budget 2048 :timeout-ms 240000}))
(defn save [file data] (spit (str out "/" file) data))
(def times (atom {}))
(defn transport [request]
  (let [id (:id request)]
    (save (str id "-request.json") (json/write-str (select-keys request [:id :messages])))
    (swap! times assoc-in [id :start] (System/currentTimeMillis))
    (println "request" id) (flush)
    (let [reply (if control {:kind :reply :text (logic id) :finish-reason "stop" :truncated? false :thinking ""} (provider request))]
      (swap! times assoc-in [id :end] (System/currentTimeMillis))
      (save (str id ".lain") (:text reply))
      (save (str id "-completion.edn") (pr-str (select-keys reply [:finish-reason :truncated?])))
      reply)))
(def owner (host/open! (model/compiler)))
(def journal (atom []))
(defn current [] (host/current-session owner))
(defn val-of [n] (connected/payload (s/binding-value (current) n)))
(defn publish [n x] (host/extend! owner (pub/declaration (current) n 1 :active x)))
(defn wait-for [f seconds]
  (let [deadline (+ (System/currentTimeMillis) (* 1000 seconds))]
    (loop [] (when-let [e (first (host/failures owner))] (throw e))
      (or (f) (if (< (System/currentTimeMillis) deadline) (do (Thread/sleep 150) (recur)) (throw (ex-info "Wait expired" {})))))))
(defn receipt [n] (let [v (val-of n)] (when (:status v) (when-not (= :admitted (:status v)) (throw (ex-info "Rejected response" {:name n :receipt v}))) v)))
(def ports '[assessment probe-count job-a job-b old-a-80 old-a-100 old-b-80 old-b-100 am-predicted bm-predicted am-fits bm-fits av-fits bv-fits am-quote bm-quote av-quote bv-quote])
(defn snapshot []
 (let [cs (current) names (into {} (for [n '[pricing-question branch-a-premise branch-b-premise invoice-marginal invoice-volume target-count]] [(s/binding-cell cs n) n]))]
 (into {} (for [n ports :let [v (premise/inspect-value (s/network cs) (s/binding-cell cs n))]]
   [n (assoc (select-keys v [:value :support]) :source-names (mapv #(str (get names (:source %) (:source %))) (:support v)))]))))
(try
 (host/extend! owner (soul/extension owner journal))
 (host/extend! owner (obs/facilities))
 (host/extend! owner (premise/facilities))
 (host/extend! owner (model/declaration root "experiments/delegation/pricing.lain" {:transport transport}))
 (publish 'branch-a-premise "branch A is under consideration")
 (publish 'branch-b-premise "branch B is under consideration")
 (publish 'target-count 140)
 (publish 'pricing-question (if clear
  "A print shop charges exactly 200 cents per copy for every positive quantity, without discounts or fees. What is the total at 140 copies? Decide whether implementation work is warranted."
  (str "A print shop charges $2 per copy for orders up to 100 copies and advertises '$1.50 per copy over 100.' Verified invoices: 80 copies cost $160 and 100 cost $200. What does this evidence establish? Choose one additional positive order quantity whose invoice would be informative."
       (when executable " Deliver independently executable predictions for each materially different interpretation you identify, so the new invoice can be checked when it arrives."))))
 (wait-for #(receipt 'planner-receipt) 300)
 (save "planner.edn" (pr-str (mapv val-of '[assessment probe-count job-a job-b])))
 (let [jobs (for [[n r] '[[job-a receipt-a] [job-b receipt-b]] :when (string? (val-of n))] r)]
  (doseq [r jobs] (wait-for #(receipt r) 300))
  (let [cs (current) nw (s/network cs)
        graph (net/net-graph nw)
        export (fn [n]
                 (let [ref (s/binding-cell cs n) callable (val-of n)
                       identity-of #(select-keys % [app/declaration-id-key app/captured-environment-key])
                       identity (when (map? callable) (identity-of callable))]
                   {:reference ref :edges (select-keys (get graph ref) [:inputs :outputs])
                    :callable identity :applications
                    (when (seq identity)
                      (vec (for [a (app/application-topologies nw)
                                 :let [v (connected/payload (net/network-cell-strongest nw (:operator-id a)))]
                                 :when (and (map? v) (= identity (identity-of v)))] a)))}))]
    (save "ports.edn" (pr-str (into {} (for [n '[job-a job-b fn-a fn-b]] [n (export n)])))))
  (save "before.edn" (pr-str (snapshot)))
  (when (and (= 2 (count jobs)) (integer? (val-of 'probe-count)) (> (val-of 'probe-count) 100))
    (let [n (val-of 'probe-count)]
      (publish 'invoice-marginal (+ 20000 (* 150 (- n 100))))
      (save "after-first.edn" (pr-str (snapshot)))
      (publish 'invoice-volume (* 150 n))))
  (save "after.edn" (pr-str (snapshot))))
 (catch Throwable e (save "error.edn" (pr-str [(ex-message e) (ex-data e)])) (.printStackTrace e))
 (finally (save "times.json" (json/write-str @times)) (save "admissions.edn" (pr-str @journal))
          (save "closed.edn" (pr-str (host/close! owner))) (shutdown-agents)))

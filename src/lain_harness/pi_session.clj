(ns lain-harness.pi-session
  "Pi transport for the existing model-refined.lain session extension. No admission or phase policy."
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [lain-harness.model-io-session :as model]
            [lain-harness.soul-session :as soul]
            [lain-harness.value-metadata :as metadata]
            [propagators.compiler-2.session :as session]
            [propagators.compiler-2.session.host :as host]
            [propagators.compiler-2.session.connected :as connected])
  (:import [java.util.concurrent TimeUnit]))

(defn inspector
  "Bounded immediate inspection, never serializes callable bodies or runtime objects."
  [owner environment-name]
  (fn [{:keys [name query]}]
    (let [current (host/current-session owner)
          network (session/network current)
          environment (connected/payload (session/binding-value current environment-name))
          facts (soul/visible-facts network environment)
          brief (fn [v] (cond (string? v) (subs v 0 (min 2000 (count v)))
                              (or (number? v) (boolean? v) (keyword? v) (nil? v)) (str v)
                              :else (str (metadata/kind v))))]
      (if (str/blank? name)
        (pr-str (mapv #(select-keys % [:name :available])
                      (take 20 (filter #(str/includes? (str (:name %)) (or query "")) facts))))
        (if-let [fact (first (filter #(= name (str (:name %))) facts))]
          (let [edge (soul/edge-view network (:cell fact))]
            (pr-str {:name name :available (:available fact) :reference (str (:cell fact))
                     :kind (if (:available fact) (metadata/kind (:value fact)) :unknown)
                     :value (brief (:value fact))
                     :inputs (mapv str (take 8 (:inputs edge)))
                     :outputs (mapv str (take 8 (:outputs edge)))}))
          (pr-str {:name name :status :not-found}))))))

(defn transport
  [{:keys [bridge evidence inspect endpoint timeout-ms node]
    :or {node "node" timeout-ms 900000 endpoint "http://127.0.0.1:18320/v1"
         inspect (fn [_] "No inspection binding supplied")}}]
  (fn [{:keys [id cancelled?] :as request}]
    (let [directory (io/file evidence (str (java.util.UUID/randomUUID)))
          _ (.mkdirs directory)
          process (-> (ProcessBuilder. ^java.util.List [node bridge])
                      (.redirectError (io/file directory "stderr.log")) .start)
          writer (io/writer (.getOutputStream process))
          reader (io/reader (.getInputStream process))
          worker (future
                   (try
                     (.write writer (str (json/write-str (assoc (select-keys request [:id :text :messages])
                                                                :workspace (.getAbsolutePath directory) :endpoint endpoint)) "\n"))
                     (.flush writer)
                     (loop []
                       (if-let [line (.readLine ^java.io.BufferedReader reader)]
                         (let [event (json/read-str line :key-fn keyword)]
                           (spit (io/file directory "protocol.jsonl") (str line "\n") :append true)
                           (case (:type event)
                             "tool" (do
                                      (.write writer (str (json/write-str {:type "tool_result" :id (:id event)
                                                                           :text (if (= "inspect_lain" (:name event))
                                                                                   (str (inspect (:arguments event)))
                                                                                   "Unknown tool")}) "\n"))
                                      (.flush writer) (recur))
                             "reply" (do (when-not (and (= :reply (keyword (:kind event)))
                                                        (= "stop" (:finish-reason event))
                                                        (false? (:truncated? event)) (string? (:text event)))
                                           (throw (ex-info "Incomplete Pi reply" {:request id})))
                                         (assoc (dissoc event :type) :kind :reply))
                             "error" (throw (ex-info "Pi adapter failed" (select-keys event [:message :stack])))
                             (recur)))
                         (throw (ex-info "Pi process exited without reply" {:request id}))))
                     (catch Throwable error
                       (spit (io/file directory "exception.edn") (pr-str (Throwable->map error)))
                       (throw error))))
          deadline (+ (System/currentTimeMillis) timeout-ms)]
      (try
        (loop []
          (when (and cancelled? (cancelled?)) (throw (InterruptedException. "Pi request cancelled")))
          (when (> (System/currentTimeMillis) deadline) (throw (ex-info "Pi request timed out" {:request id})))
          (let [answer (deref worker 50 ::pending)] (if (= ::pending answer) (recur) answer)))
        (finally
          (.destroy process)
          (.waitFor process 1000 TimeUnit/MILLISECONDS)
          (future-cancel worker)
          (try (.close writer) (catch java.io.IOException _ nil))
          (try (.close reader) (catch java.io.IOException _ nil)))))))

(defn declaration
  "Install the same model and worker through the existing session extension API."
  [root source options]
  (model/declaration root source (assoc options :transport (transport options))))

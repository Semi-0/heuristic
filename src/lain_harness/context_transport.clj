(ns lain-harness.context-transport
  "Direct Qwen transport, serving-tokenizer measurement and availability wait."
  (:require [graph.json :as json]
            [lain-harness.blazux :as blazux])
  (:import [java.net URI]
           [java.net.http HttpClient HttpClient$Version HttpRequest HttpRequest$BodyPublishers HttpResponse$BodyHandlers]
           [java.time Duration]))

(def endpoint "http://127.0.0.1:18320")
(def model "qwen3.8-flash-next")
(def settings {:endpoint (str endpoint "/v1") :model model :timeout-ms 600000
               :max-tokens 8192 :enable-thinking true :reasoning-effort "medium"
               :thinking-token-budget 4096})
(def template {:enable_thinking true :reasoning_effort "medium"})

(defn http! [path body]
  (let [builder (-> (HttpRequest/newBuilder (URI/create (str endpoint path)))
                    (.timeout (Duration/ofSeconds 30)))
        request (.build (if body
                          (-> builder (.header "Content-Type" "application/json")
                              (.POST (HttpRequest$BodyPublishers/ofString (json/write-json body))))
                          (.GET builder)))
        response (.send (-> (HttpClient/newBuilder) (.version HttpClient$Version/HTTP_1_1) .build) request (HttpResponse$BodyHandlers/ofString))]
    (when-not (= 200 (.statusCode response))
      (throw (ex-info "Qwen observation HTTP failure" {:path path :http-status (.statusCode response)})))
    (.body response)))

(defn token-count [request]
  (let [response (json/read-json (http! "/tokenize" {:model model :messages (:messages request)
                                                    :add_generation_prompt true :add_special_tokens false
                                                    :chat_template_kwargs template}))
        n (:count response)]
    (when-not (and (nat-int? n) (= n (count (:tokens response))))
      (throw (ex-info "Invalid tokenizer result" {:count n})))
    n))

(defn idle! [request checkpoint]
  (let [deadline (+ (System/currentTimeMillis) 21600000)]
    (loop [quiet 0]
      (when ((:cancelled? request)) (throw (InterruptedException. "Cancelled waiting for Qwen")))
      (let [text (http! "/metrics" nil)
            count-of (fn [metric]
                       (let [matches (re-seq (re-pattern (str "(?m)^vllm:" metric "\\{[^\\n]*\\} ([0-9.]+)$")) text)]
                         (when-not (seq matches) (throw (ex-info "Missing availability metrics" {:metric metric})))
                         (reduce + (map #(Double/parseDouble (second %)) matches))))
            running (count-of "num_requests_running") waiting (count-of "num_requests_waiting")
            n (if (and (zero? running) (zero? waiting)) (inc quiet) 0)]
        (checkpoint {:status :waiting-idle :running running :waiting waiting :quiet n})
        (cond (>= n 3) true
              (> (System/currentTimeMillis) deadline) (throw (ex-info "Qwen availability timeout" {}))
              :else (do (Thread/sleep 1000) (recur n)))))))

(defn transport [record!]
  (let [provider (blazux/completion-transport settings)]
    (fn [request]
      (let [started (System/nanoTime)]
        (try
          (idle! request #(record! (:id request) :availability %))
          (let [reply (provider request)]
            (record! (:id request) :http {:http-status (:http-status reply) :usage (:usage reply)
                                          :finish-reason (:finish-reason reply)
                                          :elapsed-ms (/ (- (System/nanoTime) started) 1e6)})
            reply)
          (catch Throwable e
            (record! (:id request) :http {:status :failed :message (ex-message e) :data (ex-data e)
                                          :elapsed-ms (/ (- (System/nanoTime) started) 1e6)})
            (throw e)))))))

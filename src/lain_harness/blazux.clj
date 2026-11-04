(ns lain-harness.blazux
  "Bounded HTTP transport for the model IO experiment. No compiler state."
  (:require [clojure.string :as str]
            [graph.json :as json])
  (:import [java.net URI]
           [java.net.http HttpClient HttpClient$Version HttpRequest HttpRequest$BodyPublishers HttpResponse$BodyHandlers]
           [java.time Duration]
           [java.util.concurrent TimeUnit TimeoutException]))

(defn- await-response! [pending cancelled?]
  (try
    (loop []
      (when (cancelled?) (throw (InterruptedException. "Request cancelled")))
      (let [response (try (.get pending 50 TimeUnit/MILLISECONDS)
                          (catch TimeoutException _ ::waiting))]
        (if (= ::waiting response) (recur) response)))
    (finally (.cancel pending true))))

(defn completion-transport
  [{:keys [endpoint model timeout-ms max-tokens api-key]
    :or {endpoint "http://127.0.0.1:18320/v1" model "qwen3.8-flash-next"
         timeout-ms 30000 max-tokens 512}}]
  (when-not (and (string? endpoint) (string? model)
                 (pos-int? timeout-ms) (pos-int? max-tokens))
    (throw (ex-info "Invalid model transport configuration" {})))
  (fn [{:keys [text cancelled?]}]
    (when (cancelled?) (throw (InterruptedException. "Request cancelled")))
    (let [client (-> (HttpClient/newBuilder)
                     (.version HttpClient$Version/HTTP_1_1)
                     (.connectTimeout (Duration/ofMillis timeout-ms)) .build)
          builder (-> (HttpRequest/newBuilder
                       (URI/create (str (str/replace endpoint #"/$" "") "/chat/completions")))
                      (.timeout (Duration/ofMillis timeout-ms))
                      (.header "Content-Type" "application/json")
                      (.POST (HttpRequest$BodyPublishers/ofString
                              (json/write-json {:model model :stream false :temperature 0
                                                :max_tokens max-tokens
                                                :messages [{:role "user" :content text}]}))))
          _ (when (seq api-key) (.header builder "Authorization" (str "Bearer " api-key)))
          response (await-response!
                    (.sendAsync client (.build builder) (HttpResponse$BodyHandlers/ofString))
                    cancelled?)]
      (when (cancelled?) (throw (InterruptedException. "Request cancelled")))
      (when-not (= 200 (.statusCode response))
        ;; Do not publish provider bodies or credentials as diagnostic values.
        (throw (ex-info "Model HTTP request failed" {:status (.statusCode response)})))
      (let [body (json/read-json (.body response))
            choice (first (:choices body))
            message (:message choice)
            text (:content message)
            thinking (or (:reasoning_content message) (:reasoning message) "")]
        (when (or (:error body) (nil? choice)
                  (not (or (nil? text) (string? text))) (not (string? thinking)))
          (throw (ex-info "Malformed model response" {})))
        {:kind :reply :text (or text "") :thinking thinking
         :finish-reason (or (:finish_reason choice) "unknown")
         :truncated? (= "length" (:finish_reason choice))}))))

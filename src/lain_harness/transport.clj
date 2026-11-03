(ns lain-harness.transport
  "Configurable streaming HTTP effect; no compiler or weather dispatch."
  (:require [clojure.string :as str] [clojure.java.io :as io] [graph.json :as json])
  (:import [java.net URI] [java.net.http HttpClient HttpRequest HttpRequest$BodyPublishers HttpResponse$BodyHandlers]
           [java.time Duration] [java.util.concurrent Executors TimeUnit ThreadFactory]))
(defn consume-sse! [reader cancelled? emit!]
  (loop []
    (when (cancelled?) (throw (ex-info "Model stream cancelled" {:phase :transport})))
    (if-let [line (.readLine ^java.io.BufferedReader reader)]
      (if (str/starts-with? line "data:")
        (let [payload (str/trim (subs line 5))]
          (when-not (= "[DONE]" payload)
            (let [event (json/read-json payload)]
              (when (:error event) (throw (ex-info "Model stream error" {:phase :transport})))
              (when (= "length" (get-in event [:choices 0 :finish_reason]))
                (throw (ex-info "Model output limit reached" {:phase :transport})))
              (when-let [chunk (get-in event [:choices 0 :delta :content])]
                (when (seq chunk) (emit! chunk))))
            (recur)))
        (recur))
      (throw (ex-info "Model stream ended without completion" {:phase :transport})))))
(defn streaming-model
  [{:keys [endpoint model timeout-ms max-tokens headers]
    :or {timeout-ms 90000 max-tokens 1800 headers {}}}]
  (when-not (and (string? endpoint) (string? model) (pos-int? timeout-ms))
    (throw (ex-info "Model endpoint, name and positive timeout are required" {})))
  (fn [{:keys [messages cancelled?]} emit!]
    (let [builder (-> (HttpRequest/newBuilder (URI/create (str (str/replace endpoint #"/$" "") "/chat/completions")))
                      (.timeout (Duration/ofMillis timeout-ms))
                      (.header "Content-Type" "application/json")
                      (.POST (HttpRequest$BodyPublishers/ofString
                               (json/write-json {:model model :messages messages :stream true
                                                 :temperature 0 :max_tokens max-tokens}))))
          _ (doseq [[name value] headers] (.header builder name value))
          deadline (+ (System/nanoTime) (* 1000000 timeout-ms))
          client (-> (HttpClient/newBuilder) (.connectTimeout (Duration/ofMillis timeout-ms)) .build)
          response (.send client (.build builder) (HttpResponse$BodyHandlers/ofInputStream))
          timer (Executors/newSingleThreadScheduledExecutor
                  (reify ThreadFactory (newThread [_ r] (doto (Thread. r "lain-http-deadline") (.setDaemon true)))))
          expired (atom false)]
      (try
        (with-open [body (.body response)]
          (.scheduleAtFixedRate timer ^Runnable
            (fn [] (when (or (cancelled?) (> (System/nanoTime) deadline))
                     (reset! expired true) (.close body))) 0 50 TimeUnit/MILLISECONDS)
          (when-not (= 200 (.statusCode response))
            (throw (ex-info "Model HTTP request failed" {:phase :transport :status (.statusCode response)})))
          (with-open [reader (io/reader body)]
            (consume-sse! reader #(or @expired (cancelled?)) emit!)))
        (finally (.shutdownNow timer))))))

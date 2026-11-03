(ns lain-harness.live-v4
  "Opt-in direct inference and compiler adapters. All IO is post-commit."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [graph.json :as json]
            [propagators.compiler-2.session :as session]
            [propagators.compiler-2.session.host :as host]
            [propagators.compiler-2.session.operators :as ops]
            [propagators.compiler-2.runtime.session.program.source :as source]
            [propagators.stdlib.source-publication :as publication])
  (:import [java.net URI]
           [java.net.http HttpClient HttpClient$Version HttpRequest HttpRequest$BodyPublishers HttpResponse$BodyHandlers]
           [java.time Duration Instant]))

(def project "/home/lain/projects/lain-workspace")
(defn stamp [] (str (Instant/now)))
(defn log! [config event]
  (when-let [path (:events config)]
    (locking log!
      (spit path (str (json/write-json (assoc event :at (stamp))) "\n") :append true))))

(defn http-json [method url body seconds]
  (let [client (-> (HttpClient/newBuilder) (.version HttpClient$Version/HTTP_1_1) (.connectTimeout (Duration/ofSeconds 10)) .build)
        builder (-> (HttpRequest/newBuilder (URI/create url))
                    (.timeout (Duration/ofSeconds seconds))
                    (.header "Content-Type" "application/json"))
        request (.build (if (= :post method)
                          (.POST builder (HttpRequest$BodyPublishers/ofString (json/write-json body)))
                          (.GET builder)))
        response (.send client request (HttpResponse$BodyHandlers/ofString))]
    (when-not (= 200 (.statusCode response))
      (throw (ex-info "HTTP request failed" {:status (.statusCode response)
                                             :body (subs (.body response) 0 (min 600 (count (.body response))))})))
    (json/read-json (.body response))))

(def instructions
  (str "You are inside a persistent Lain computational network. Read the environment's guide, design, capability index and observation. LIVE signatures and compiler diagnostics override schematic examples in the design document. "
       "Your job is to formulate reusable Lain NETWORK SOURCE for the request, not a narrative or tool-call sequence. "
       "Return exactly a JSON object with keys source (string) and note (string). No markdown fences. "
       "Only use the exposed Lain vocabulary. No imports, host code or invented helpers. "
       "Use (define umbrella-for-v4 (network [place date] ...)) to compose h4:weather-rain-probability and a conditional. "
       "h4:weather-rain-probability returns a NUMBER 0..1, so compare it directly. Return umbrella advice as a string. "
       "Wire the application to the requested output cell with ->. Outputs are already declared. "
       "If umbrella-for-v4 already exists, reuse it without redefining. If the observation says this request was compiled, "
       "return source empty: the network computes its result without further source. Do not regenerate it. "
       "The environment is live; changes to its fields arrive automatically. Your response is one bounded inference, not a host agent loop."))

(defn direct-model [config description]
  (let [response (http-json :post (str (:endpoint config) "/chat/completions")
                            {:model (:model config) :temperature 0 :max_tokens 1800
                             :chat_template_kwargs {:enable_thinking false}
                             :messages [{:role "system" :content instructions}
                                        {:role "user" :content description}]} 90)
        content (get-in response [:choices 0 :message :content])
        finish (get-in response [:choices 0 :finish_reason])]
    (when-not (and (string? content) (not= "length" finish))
      (throw (ex-info "Model did not finish a source response" {:finish finish})))
    (let [parsed (json/read-json content)]
      (when-not (and (string? (:source parsed)) (string? (:note parsed)))
        (throw (ex-info "Expected JSON source and note strings" {})))
      (log! config {:event "model-response" :response parsed :usage (:usage response)})
      content)))

(def allowed-symbols
  '#{define network let if -> + - * / > < >= <= = list
     umbrella-for-v4 h4:weather-rain-probability
     h4:answer h4:answer-2 h4:answer-live})

(defn validate-source [text]
  (when (> (count text) 6000) (throw (ex-info "Generated source too large" {})))
  (let [forms (source/read-source-forms text)]
    (when (> (count forms) 8) (throw (ex-info "Too many generated forms" {})))
    (letfn [(walk [form locals]
              (cond
                (symbol? form)
                (when-not (or (contains? locals form) (allowed-symbols form))
                  (throw (ex-info "Generated symbol is outside the MVP vocabulary" {:symbol form})))
                (seq? form)
                (case (first form)
                  network
                  (let [[_ parameters & body] form]
                    (when-not (and (vector? parameters) (every? symbol? parameters))
                      (throw (ex-info "Expected simple network parameters" {})))
                    (doseq [expression body] (walk expression (into locals parameters))))
                  let
                  (let [[_ bindings & body] form]
                    (when-not (and (vector? bindings) (even? (count bindings)))
                      (throw (ex-info "Expected let binding pairs" {})))
                    (let [scope (reduce (fn [scope [name expression]]
                                          (when-not (symbol? name)
                                            (throw (ex-info "Expected a local name" {})))
                                          (walk expression scope)
                                          (conj scope name))
                                        locals (partition 2 bindings))]
                      (doseq [expression body] (walk expression scope))))
                  (doseq [expression form] (walk expression locals)))
                (coll? form) (throw (ex-info "Unsupported generated literal collection" {}))
                :else nil))]
      (doseq [form forms]
        (when-not (and (seq? form) (#{'define '->} (first form)))
          (throw (ex-info "Expected a network definition or output connection" {})))
        (when (and (= 'define (first form)) (not= 'umbrella-for-v4 (second form)))
          (throw (ex-info "Only umbrella-for-v4 may be defined by this experiment" {})))
        (walk form #{})))
    forms))

(defn description [request guide design capabilities observation]
  (json/write-json {:request request
                    :index [{:name "How to Think in Lain" :path "docs/how-to-think-in-lain.md"
                             :sections [4 9 17 19 20 22]}
                            {:name "Harness design" :path "soul-refined.lain"}]
                    :guide guide :design design :capabilities capabilities :observation observation}))

(def publish-targets #{'h4:request 'h4:observation 'h4:capabilities 'h4:weather-mode})
(defn publish! [owner name revision content]
  (let [sym (symbol name)]
    (when-not (publish-targets sym) (throw (ex-info "Undeclared publication endpoint" {:symbol sym})))
    (host/submit! owner (session/cell-input (session/binding-cell (host/current-session owner) sym)
                                            (publication/active sym revision content)))))

(defn forecast [config place date mode]
  (if (= mode "fixture")
    (case place "Amsterdam" 0.8 "Rotterdam" 0.1
          (throw (ex-info "Unknown fixture place" {:place place})))
    (let [[latitude longitude] (case place "Amsterdam" [52.37 4.90] "Rotterdam" [51.92 4.48]
                                     (throw (ex-info "MVP supports Amsterdam and Rotterdam" {})))
          _ (java.time.LocalDate/parse date)
          response (http-json :get
                              (str "https://api.open-meteo.com/v1/forecast?latitude=" latitude
                                   "&longitude=" longitude "&daily=precipitation_probability_max&timezone=Europe%2FAmsterdam")
                              nil 25)
          days (get-in response [:daily :time])
          index (.indexOf ^java.util.List days date)
          percentage (when (>= index 0) (nth (get-in response [:daily :precipitation_probability_max]) index))]
      (when-not (number? percentage) (throw (ex-info "No forecast for requested date" {:date date})))
      (log! config {:event "weather-observation" :provider "Open-Meteo" :place place
                    :date date :probability (/ percentage 100.0)})
      (/ percentage 100.0))))

(defn bindings [config]
  (let [counter (atom 0) revisions (atom 10) compiled (atom #{}) observations (atom [])
        observe! (fn [owner event]
                   (locking observations
                     (let [history (swap! observations #(vec (take-last 8 (conj % event))))]
                       (publish! owner "h4:observation" (swap! revisions inc)
                                 (str/join "\n" history)))))
        model-call (or (:model-call config) #(direct-model config %))
        effect
        (fn [kind work]
          (ops/async-op
           (reify ops/EffectInterpreter
             (interpret-effect! [_ owner context input emit!]
               (log! config {:event "effect-start" :kind kind :adapter "v4" :id (str (hash (:id context)))})
               (try
                 (let [result (work owner context input)]
                   (when-not ((:cancelled? context)) (emit! result))
                   (log! config {:event "effect-finish" :kind kind}))
                 (catch Exception error
                   (log! config {:event "effect-error" :kind kind :error (ex-message error) :details (ex-data error)})
                   (when (and (= kind "compile") (not ((:cancelled? context))))
                     (observe! owner
                               (str "Compiler rejected source; repair it using this LIVE diagnostic: "
                                    (ex-message error) " " (pr-str (ex-data error)))))
                   (when-not ((:cancelled? context))
                     (emit! (json/write-json {:source "" :note (str "ERROR: " (ex-message error))}))))
                 (finally ((:complete! context))))
               nil))
           (fn [make] (make (fn [& arguments] (ops/effect-output (vec arguments)))))))]
    [['h4:description (ops/prim-op (fn [make] (make description)))]
     ['h4:source (ops/prim-op (fn [make] (make #(get (json/read-json %) :source ""))))]
     ['h4:model (effect "model"
                        (fn [_ context [input]]
                          (let [n (swap! counter inc)]
                            (when (> n (or (:max-inferences config) 8))
                              (throw (ex-info "Inference budget exhausted; supervisor intervention required" {})))
                            (log! config {:event "model-input" :inference n :description (json/read-json input)})
                            (let [output (model-call input)]
                              (when ((:cancelled? context)) (throw (ex-info "Stale inference discarded" {})))
                              output))))]
     ['h4:install (effect "compile"
                          (fn [owner context [text]]
                            (if (or (str/blank? text) (contains? @compiled text))
                              "No new source"
                              (do
                                (validate-source text)
                                (when ((:cancelled? context)) (throw (ex-info "Stale source rejected" {})))
                                (host/extend-source! owner text)
                                (swap! compiled conj text)
                                (log! config {:event "source-installed" :source text})
                                (observe! owner
                                          (str "Compiled requested network/application. umbrella-for-v4 exists. Source:\n" text))
                                "Source compiled into this session"))))]
     ['h4:publish (effect "publish"
                          (fn [owner _ [name revision content]]
                            (when (= name "h4:observation") (swap! revisions max revision))
                            (str (publish! owner name revision content))))]
     ['h4:weather (effect "weather"
                          (fn [owner _ [place date mode]]
                            (log! config {:event "weather-request" :place place :date date :mode mode})
                            (let [result (forecast config place date mode)]
                              (observe! owner (str "Weather observation for " place " on " date ": probability=" result " mode=" mode))
                              result)))]]))

(defn environment []
  (bindings (edn/read-string (slurp (str project "/experiments/live/config.edn")))))

(ns lain-harness.model-io-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.core.async :as async]
            [graph.json :as json]
            [lain-harness.blazux :as blazux]
            [lain-harness.model-io :as bridge]
            [lain-harness.model-io-session :as setup]
            [propagators.cells.value :as value]
            [propagators.compiler-2.session :as session]
            [propagators.compiler-2.session.connected :as connected]
            [propagators.compiler-2.session.files :as files]
            [propagators.compiler-2.session.host :as host]
            [propagators.compiler-2.session.operators :as ops]
            [propagators.semantic-trace.capture :as capture]
            [propagators.stdlib.source-publication :as publication])
  (:import [com.sun.net.httpserver HttpServer HttpHandler]
           [java.net InetSocketAddress]))

(def root (System/getenv "LAIN_HARNESS_ROOT"))
(def fixture "test/fixtures/model-io.lain")

(defn observed [owner binding]
  (connected/payload (session/binding-value (host/current-session owner) binding)))

(defn await! [owner predicate]
  (let [deadline (+ (System/currentTimeMillis) 15000)]
    (loop []
      (when-let [error (first (host/failures owner))] (throw error))
      (cond
        (predicate) true
        (> (System/currentTimeMillis) deadline)
        (throw (ex-info "Model IO test timed out"
                        {:receipts (mapv (fn [[id receipt]] [(nth id 2) (:status receipt)])
                                         (host/receipts owner))}))
        :else (do (async/<!! (async/timeout 10)) (recur))))))

(defn publish! [owner binding revision input]
  (host/submit! owner
                (session/cell-input (session/binding-cell (host/current-session owner) binding)
                                    (publication/active [:test binding] revision input))))

(defn receipt-name [receipt] (nth receipt 2))
(defn subscriptions-ready? [owner]
  (= 2 (count (filter #(and (= 'io-model:receive (receipt-name (key %)))
                            (= :active (:status (val %)))) (host/receipts owner)))))

(deftest primitive-slot-preserves-publication-support
  (let [initial (session/extend-session
                 (session/create-session)
                 (files/prepare-extension!
                  {:id :primitive-slot :root root :read-files ["test/fixtures/primitive-slot.lain"]}))
        publish (fn [s update]
                  (:session (session/advance s [(session/cell-input (session/binding-cell s 'reply) update)])))
        read-value (fn [s binding] (connected/payload (session/binding-value s binding)))
        first-reply (publish initial (publication/active :reply 1 {:thinking "thought" :text "answer"}))
        withdrawn (publish first-reply (publication/retracted :reply 2))
        recovered (publish withdrawn (publication/active :reply 3 {:thinking "thought" :text "answer"}))
        replaced (publish recovered (publication/active :reply 4 {:thinking "" :text "next"}))
        absent (publish replaced (publication/active :reply 5 {:text "no thinking"}))]
    (is (value/nothing? (read-value initial 'thinking)))
    (is (= "thought" (read-value first-reply 'thinking)))
    (is (= "answer" (read-value first-reply 'answer)))
    (is (value/nothing? (read-value first-reply 'missing)))
    (is (value/nothing? (read-value withdrawn 'thinking)))
    (is (= "thought" (read-value recovered 'thinking)))
    (is (= "" (read-value replaced 'thinking)))
    (is (= "next" (read-value replaced 'answer)))
    (is (value/nothing? (read-value absent 'thinking)))))

(deftest actual-lain-two-instances-replay-and-reply-replacement
  (let [calls (atom [])
        transport (fn [{:keys [text]}]
                    (swap! calls conj text)
                    {:kind :reply :text (str "reply:" text) :thinking (str "thought:" text)})
        declaration (setup/declaration root fixture {:transport transport})
        pure (session/extend-session (setup/compiler) declaration)]
    (testing "Compilation declares effects without allocating or invoking the transport"
      (is (empty? @calls))
      (is (value/nothing? (session/binding-value pure 'output))))
    (let [owner (host/open! pure)]
      (try
        (host/step! owner)
        (await! owner #(subscriptions-ready? owner))
        (is (empty? @calls))
        (is (value/nothing? (observed owner 'output)))
        (publish! owner 'input 1 {:id "a" :text "first"})
        (await! owner #(= "reply:first" (:text (observed owner 'output))))
        (is (= "thought:first" (observed owner 'thinking)))
        (is (= "reply:first" (observed owner 'actions)))
        (is (capture/fragment? (observed owner 'model-struct-trace)))
        (is (seq (:nodes (capture/root-graph (observed owner 'model-struct-trace)))))
        (is (value/nothing? (observed owner 'other-output)))
        (publish! owner 'other-input 1 {:id "b" :text "second"})
        (await! owner #(= "reply:second" (:text (observed owner 'other-output))))
        (is (= "reply:first" (:text (observed owner 'output))))
        (publish! owner 'input 1 {:id "a" :text "first"})
        (host/step! owner)
        (is (= ["first" "second"] @calls))
        (publish! owner 'input 2 {:id "c" :text "third"})
        (await! owner #(= "reply:third" (:text (observed owner 'output))))
        (is (= "c" (:request-id (observed owner 'output))))
        (is (= 2 (count (filter #(= 'io-model:receive (receipt-name %)) (keys (host/receipts owner))))))
        (is (empty? (host/failures owner)))
        (host/rerun! owner (random-uuid))
        (await! owner #(subscriptions-ready? owner))
        (is (value/nothing? (observed owner 'output)))
        (publish! owner 'input 1 {:id "fresh-run" :text "first"})
        (await! owner #(= "fresh-run" (:request-id (observed owner 'output))))
        (is (= ["first" "second" "third" "first"] @calls))
        (finally (is (= :closed (:status (host/close! owner)))))))))

(deftest failure-is-a-supported-output-and-next-request-still-works
  (let [owner (setup/open! root fixture
                           {:transport (fn [{:keys [text]}]
                                         (if (= text "fail")
                                           (throw (ex-info "secret provider body" {:status 503}))
                                           {:kind :reply :text text :thinking ""}))})]
    (try
      (publish! owner 'input 1 "fail")
      (await! owner #(= :error (:kind (observed owner 'output))))
      (is (= 503 (:http-status (observed owner 'output))))
      (is (= "Model request failed" (:error (observed owner 'output))))
      (publish! owner 'input 2 "recovered")
      (await! owner #(= "recovered" (:text (observed owner 'output))))
      (is (= "" (observed owner 'thinking)))
      (finally (host/close! owner)))))

(deftest closing-cancels-inflight-transport-and-fences-late-output
  (let [entered (promise) stopped (promise)
        owner (setup/open! root fixture
                           {:transport (fn [{:keys [cancelled?]}]
                                         (deliver entered true)
                                         (loop []
                                           (when-not (cancelled?)
                                             (async/<!! (async/timeout 10))
                                             (recur)))
                                         (deliver stopped true)
                                         {:kind :reply :text "late" :thinking ""})})]
    (try
      (publish! owner 'input 1 "wait")
      (is (= true (deref entered 5000 :timeout)))
      (is (= :closed (:status (host/close! owner))))
      (is (= true (deref stopped 1000 :timeout)))
      (is (value/nothing? (observed owner 'output)))
      (finally (when (= :active (host/lifecycle owner)) (host/close! owner))))))

(deftest actual-http-contract
  (let [received (promise)
        server (HttpServer/create (InetSocketAddress. "127.0.0.1" 0) 0)]
    (.createContext server "/v1/chat/completions"
                    (reify HttpHandler
                      (handle [_ exchange]
                        (deliver received (json/read-json (slurp (.getRequestBody exchange))))
                        (let [body (.getBytes (json/write-json
                                               {:choices [{:message {:content "hello" :reasoning_content "considered"}
                                                           :finish_reason "stop"}]}) "UTF-8")]
                          (.sendResponseHeaders exchange 200 (alength body))
                          (with-open [out (.getResponseBody exchange)] (.write out body))))))
    (.start server)
    (try
      (let [transport (blazux/completion-transport
                       {:endpoint (str "http://127.0.0.1:" (.getPort (.getAddress server)) "/v1")
                        :model "test-model" :max-tokens 32 :timeout-ms 2000})
            result (transport {:text "hi" :cancelled? (constantly false)})]
        (is (= {:kind :reply :text "hello" :thinking "considered"
                :finish-reason "stop" :truncated? false} result))
        (is (= [{:role "user" :content "hi"}] (:messages @received)))
        (is (= false (:stream @received)))
        (is (= 32 (:max_tokens @received))))
      (finally (.stop server 0)))))

(deftest bounded-queue-and-explicit-effect-matching
  (let [owner (host/open! (session/create-session))
        entered (promise) release (promise) handle (promise) acknowledgements (atom [])
        context {:id :worker :cancelled? (constantly false) :complete! (fn [])}
        interpreter (bridge/interpreter
                     (fn [{:keys [text cancelled?]}]
                       (deliver entered true)
                       (loop []
                         (when (and (not (realized? release)) (not (cancelled?)))
                           (async/<!! (async/timeout 5)) (recur)))
                       {:kind :reply :text text :thinking ""})
                     {:capacity 1})
        stop (ops/interpret-effect! interpreter owner context {:op :open}
                                    (fn [value] (deliver handle value) :accepted))
        enqueue! (fn [id]
                   (ops/interpret-effect!
                    interpreter owner (assoc context :id id)
                    {:op :enqueue :worker @handle :request {:id id :text id}}
                    (fn [value] (swap! acknowledgements conj [id value]) :accepted)))]
    (try
      (enqueue! "running")
      (is (= true (deref entered 1000 :timeout)))
      (enqueue! "queued")
      (enqueue! "full")
      (is (= [["full" :backpressured]] @acknowledgements))
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Unknown model IO effect"
                            (ops/interpret-effect! interpreter owner context {:op :unsupported} identity)))
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"different session run"
                            (ops/interpret-effect! interpreter owner context
                                                   {:op :subscribe :worker :not-a-handle} identity)))
      (finally
        (deliver release true)
        (is (= {:status :closed} (stop)))
        (host/close! owner)))))

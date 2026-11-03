(ns lain-harness.streaming-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.io :as io]
            [lain-harness.queue :as q] [lain-harness.stream :as stream]
            [lain-harness.transport :as transport] [lain-harness.runtime :as h]
            [lain-harness.premises :as premises]
            [propagators.compiler-2.session :as s]
            [propagators.compiler-2.session.host :as host]
            [propagators.compiler-2.compiler.basis :as basis]
            [propagators.experimental.visualization.data :as data]
            [propagators.network :as net]
            [propagators.cells.value :as value])
  (:import [java.io StringReader BufferedReader]))
(def source-path (str (System/getenv "LAIN_HARNESS_ROOT") "/soul-refined.lain"))
(defn await! [f]
  (let [deadline (+ (System/currentTimeMillis) 20000)]
    (loop [] (cond (f) true (> (System/currentTimeMillis) deadline) false
                  :else (do (Thread/sleep 20) (recur))))))
(deftest queue-contract
  (let [q (q/queue 2) a {:id :a :value 1} b {:id :b :value 1}]
    (is (= :accepted (q/append! q a)))
    (is (= :duplicate (q/append! q a)))
    (is (thrown? Exception (q/append! q (assoc a :value 2))))
    (q/append! q b)
    (is (= [a b] (q/pending q)))
    (is (thrown? Exception (q/append! q {:id :c})))
    (is (thrown? Exception (q/acknowledge! q [:b])))
    (q/acknowledge! q [:a])
    (is (= [b] (q/pending q)))
    (is (= [a b] (q/history q)))
    (q/close! q)
    (is (thrown? Exception (q/append! q {:id :c})))))
(deftest fragmented-forms
  (let [f (stream/framer) seen (atom []) emit #(swap! seen conj %)]
    (stream/feed! f "; comment\n(define x \"hel" false emit)
    (is (empty? @seen))
    (stream/feed! f "lo\\\"world\")\n(define y" false emit)
    (is (= 1 (count @seen)))
    (stream/feed! f " 2) ; end" true emit)
    (is (= ['(define x "hello\"world") '(define y 2)] (mapv :form @seen))))
  (is (thrown? Exception (stream/feed! (stream/framer) "(define x" true identity)))
  (is (thrown? Exception (stream/feed! (stream/framer) "(define x ])" false identity)))
  (let [f (stream/framer) seen (atom [])]
    (is (thrown? Exception (stream/feed! f "(define a 1)(define b 2)" true
                            (fn [form] (swap! seen conj form) (throw (ex-info "Admission failed" {}))))))
    (is (= 1 (count @seen)))))
(deftest sse-contract
  (let [seen (atom [])]
    (transport/consume-sse! (BufferedReader. (StringReader.
      "data: {\"choices\":[{\"delta\":{\"content\":\"(define x 1)\"}}]}\n\ndata: [DONE]\n"))
      (constantly false) #(swap! seen conj %))
    (is (= ["(define x 1)"] @seen)))
  (is (thrown? Exception (transport/consume-sse! (BufferedReader. (StringReader. "")) (constantly false) identity))))
(defn setup [transport]
  (let [owner (host/open! (s/create-session)) services (h/services owner)]
    (host/extend! owner (h/facilities-extension services))
    (host/extend! owner (h/specification-extension services source-path))
    ;; Deliberately supply the independent model and user AFTER their pending cells.
    (host/extend! owner (h/user-extension :test-user))
    (host/extend! owner (h/model-extension services {:id :test-model :transport transport :max-inferences 5 :timeout-ms 5000}))
    {:owner owner :services services :model (get @(:models services) :test-model)}))
(defn launch! [{:keys [owner services]}]
  (h/publish-user! services :test-user 1 "Make plus-two and apply it.")
  (h/start-model! services)
  (host/extend-source! owner
    "(define active-run (runtime ((contribute-user-input user-input) ((construct-concept-of \"context\" harness-context) (empty-env))) model-session))"))
(defn observed [{:keys [owner model]} name]
  (let [s (host/current-session owner) target (:target @(:state model))]
    (when target
      (try (net/network-cell-strongest (s/network s) (s/scoped-binding-cell s target name))
           (catch Exception _ value/nothing)))))
(deftest actual-source-streaming-reuse-and-support
  (let [calls (atom [])
        fixture (setup (fn [request emit!]
                         (swap! calls conj (:round request))
                         (case (:round request)
                           1 (do (emit! "(define plus-two (network [x] (+ x")
                                 (emit! " 2)))")
                                 (emit! "(define answer (plus-two 5))"))
                           2 (emit! "(define reused (plus-two 8))")
                           nil)))
        {:keys [owner services model]} fixture]
    (try
      (is (value/nothing? (data/payload (s/binding-value (host/current-session owner) 'weather-at))))
      (is (empty? @calls))
      (launch! fixture)
      (is (await! #(and (= 10 (data/payload (observed fixture 'reused)))
                         (= 3 (count (:admissions @(:state model)))))))
      (is (= 7 (data/payload (observed fixture 'answer))))
      (is (= 3 (count (:admissions @(:state model)))))
      (is (empty? (host/failures owner)))
      (let [round (basis/stable-node-id :model-round :test-model 1)]
        (premises/publish! owner round 2 :retracted)
        (is (await! #(value/unusable? (observed fixture 'answer))))
        (is (value/unusable? (observed fixture 'reused)))
        (premises/publish! owner round 3 :active)
        (is (await! #(= 7 (data/payload (observed fixture 'answer))))))
      (is (= 3 (count (:admissions @(:state model)))))
      (finally (host/close! owner)))))
(deftest failed-form-stops-response-and-feeds-diagnostic
  (let [fixture (setup (fn [{:keys [round]} emit!]
                         (when (= round 1)
                           (emit! "(define kept 1)")
                           (emit! "(define kept 2) (define forbidden 3)"))))
        {:keys [owner model]} fixture]
    (try
      (launch! fixture)
      (is (await! #(seq (:diagnostics @(:state model)))))
      (is (= 1 (data/payload (observed fixture 'kept))))
      (is (value/nothing? (observed fixture 'forbidden)))
      (is (await! #(>= (:round @(:state model)) 2)))
      (is (= 1 (count (:admissions @(:state model)))))
      (finally (host/close! owner)))))
(deftest transport-failure-is-visible-and-paused
  (let [calls (atom 0)
        fixture (setup (fn [_ _] (swap! calls inc) (throw (ex-info "Fixture transport disconnected" {}))))
        {:keys [owner model]} fixture]
    (try
      (launch! fixture)
      (is (await! #(true? (:paused? @(:state model)))))
      (is (await! #(seq (:diagnostics @(:state model)))))
      (is (= 1 @calls))
      (is (await! #(seq (filter (fn [event] (clojure.string/starts-with? (str (:binding event)) "diagnostic-"))
                       (vals (lain-harness.environment/snapshot (host/current-session owner) (:target @(:state model))))))))
      (is (empty? (:admissions @(:state model))))
      (finally (host/close! owner)))))
(deftest changes-during-inference-wait-for-next-round
  (let [started (promise) release (promise) calls (atom [])
        fixture (setup (fn [request _]
                         (swap! calls conj request)
                         (when (= 1 (:round request)) (deliver started true) @release)))
        {:keys [owner services model]} fixture]
    (try
      (launch! fixture)
      (is (= true (deref started 20000 :timeout)))
      (h/publish-user! services :test-user 2 "A second request while the first inference is active")
      (is (await! #(seq (q/pending (:inbound model)))))
      (is (= 1 (count @calls)))
      (deliver release true)
      (is (await! #(>= (count @calls) 2)))
      (is (= 1 (:round (first @calls))))
      (is (= 2 (:round (second @calls))))
      (finally (deliver release true) (host/close! owner)))))
(deftest both-extension-installation-orders
  (doseq [order [:model-first :user-first]]
    (let [owner (host/open! (s/create-session)) services (h/services owner) calls (atom 0)
          model #(host/extend! owner (h/model-extension services {:id order :transport (fn [_ _] (swap! calls inc))}))
          user #(host/extend! owner (h/user-extension order))]
      (try
        (host/extend! owner (h/facilities-extension services))
        (if (= :model-first order) (do (model) (user)) (do (user) (model)))
        (host/extend! owner (h/specification-extension services source-path))
        (is (= 0 @calls))
        (is (not (value/nothing? (s/binding-value (host/current-session owner) 'model-session))))
        (is (value/nothing? (data/payload (s/binding-value (host/current-session owner) 'user-input))))
        (finally (host/close! owner))))))

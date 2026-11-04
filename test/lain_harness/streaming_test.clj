(ns lain-harness.streaming-test
  (:require [clojure.test :refer [deftest is]]
            [lain-harness.transport :as transport]
            [lain-harness.runtime :as h]
            [propagators.compiler-2.session :as s]
            [propagators.compiler-2.session.host :as host]
            [propagators.experimental.visualization.data :as data]
            [propagators.network :as net])
  (:import [java.io StringReader BufferedReader]))
(def source-path (str (System/getenv "LAIN_HARNESS_ROOT") "/soul-refined.lain"))
(defn await! [f]
  (let [deadline (+ (System/currentTimeMillis) 90000)]
    (loop [] (cond (f) true (> (System/currentTimeMillis) deadline) false
                   :else (do (Thread/sleep 20) (recur))))))
(deftest sse-contract
  (let [seen (atom [])]
    (transport/consume-sse! (BufferedReader. (StringReader.
                                              "data: {\"choices\":[{\"delta\":{\"content\":\"(define x 1)\"}}]}\n\ndata: [DONE]\n"))
                            (constantly false) #(swap! seen conj %))
    (is (= ["(define x 1)"] @seen)))
  (is (thrown? Exception (transport/consume-sse! (BufferedReader. (StringReader. "")) (constantly false) identity))))

(defn messages [services]
  (h/list-values (h/read-cell (host/current-session (:owner services))
                              (h/model-cell (host/current-session (:owner services)) "model" :delivered))))
(defn settled? [services]
  (let [state (h/model-state services "model")]
    (and (= :idle (:active state)) (zero? (get-in state [:inputs :count])))))
(defn child-value [services name]
  (let [current (host/current-session (:owner services))
        child (data/payload (s/binding-value current 'conversation-child))]
    (data/payload (net/network-cell-strongest (s/network current)
                                              (s/scoped-binding-cell current (:id child) name)))))

(deftest canonical-coded-conversation
  (let [requests (atom [])
        services (h/prepare!
                  {:source-path source-path
                   :transport (fn [request emit!]
                                (swap! requests conj request)
                                (case (:round request)
                                  1 (do (emit! "(define plus-two (network [x] (+ x")
                                        (emit! " 2))) (define answer (plus-two 5))")
                                        (emit! "(message-to-user \"Hello\")"))
                                  2 (emit! "(define reused (plus-two 8)) (message-to-user \"Ten\")")
                                  (throw (ex-info "Unexpected self-observation round" {}))))})
        owner (:owner services)]
    (try
      (is (empty? @requests) "Preparing and attaching does not infer")
      (h/publish-user! services "user" "first" "Hello")
      (is (await! #(and (= 1 (count (messages services))) (settled? services))))
      (is (= ["Hello"] (mapv :payload (messages services))))
      (is (= 7 (child-value services 'answer)))
      (is (= 1 (count @requests)) "Child topology and delivery do not infer")
      (h/publish-user! services "user" "first" "Hello")
      (h/publish-user! services "user" "second" "Use plus-two on eight")
      (is (await! #(and (= 2 (count (messages services))) (settled? services))))
      (is (= ["Hello" "Ten"] (mapv :payload (messages services))))
      (is (= 10 (child-value services 'reused)))
      (is (= 2 (count @requests)))
      (is (some #{{:role "assistant" :content "Hello"}} (:messages (second @requests))))
      (is (= 1 (get-in (h/model-state services "model") [:round :number])))
      (is (empty? (host/failures owner)))
      (finally (host/close! owner)))))

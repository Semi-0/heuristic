(ns lain-harness.soul-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [lain-harness.behavior :as behavior]
            [lain-harness.model-io :as bridge]
            [lain-harness.model-io-session :as model]
            [lain-harness.soul-session :as soul]
            [propagators.cells.value :as value]
            [propagators.network :as net]
            [propagators.compiler-2.session :as session]
            [propagators.compiler-2.session.connected :as connected]
            [propagators.compiler-2.session.host :as host]
            [propagators.datastructures.support-collection :as collection]
            [propagators.datastructures.reducer-cell :as reducer]
            [propagators.experimental.temporal-behavior :as temporal]
            [propagators.stdlib.source-publication :as publication]))

(def root (System/getenv "LAIN_HARNESS_ROOT"))
(defn read-value [owner binding]
  (connected/payload (session/binding-value (host/current-session owner) binding)))
(defn await! [owner predicate]
  (let [deadline (+ (System/currentTimeMillis) 45000)]
    (loop []
      (when-let [error (first (host/failures owner))] (throw error))
      (cond (predicate) true
            (> (System/currentTimeMillis) deadline) (throw (ex-info "Soul test timed out" {:diagnostic (try (read-value owner 'observe-cell) (catch Exception _ :absent))}))
            :else (do (Thread/sleep 10) (recur))))))

(deftest definitions-only-import-does-not-allocate-model
  (let [calls (atom []) owner (model/open! root "model-refined.lain" {:transport #(swap! calls conj %)})]
    (try
      (is (empty? @calls))
      (is (empty? (host/receipts owner)))
      (finally (host/close! owner)))))

(deftest explicit-messages-validated-without-semantic-additions
  (let [request {:id "x" :messages [{:role "system" :content "caller instructions"}
                                    {:role "user" :content "hello"}]}]
    (is (= request (bridge/request-value request)))
    (is (= {:text "hi"} (bridge/request-value "hi")))
    (is (thrown? Exception (bridge/request-value {:messages []})))
    (is (thrown? Exception (bridge/request-value {:messages [{:role "tool" :content "x"}]})))))

(deftest compiled-qualified-reducers-and-ordinary-projection
  (let [owner (host/open! (model/compiler))]
    (try
      (host/extend! owner (soul/extension owner (atom [])))
      (host/extend! owner (behavior/extension root "test/fixtures/soul-operators.lain"))
      (let [publish (fn [binding id time v]
                      (host/submit! owner
                                    (session/cell-input
                                     (session/binding-cell (host/current-session owner) binding)
                                     (collection/content {:base (temporal/occurrence id time v) :support #{}}))))]
        (is (value/nothing? (read-value owner 'latest)))
        (is (= [] (read-value owner 'initial-current)))
        (is (< (count (read-value owner 'overview)) 100000))
        (is (str/includes? (read-value owner 'overview) ":state :unknown"))
        (is (str/includes? (read-value owner 'overview) ":state :contradictory"))
        (is (str/includes? (read-value owner 'overview) "available"))
        (is (contains? (read-value owner 'inspected) :inputs))
        (is (not= (:id (read-value owner 'sibling-a)) (:id (read-value owner 'sibling-b))))
        (publish 'source-a :two 2 2)
        (await! owner #(= [2] (read-value owner 'latest)))
        (publish 'source-a :one 1 1)
        (await! owner #(= [1 2] (read-value owner 'latest)))
        (publish 'source-a :one 1 1)
        (host/step! owner)
        (is (= [1 2] (read-value owner 'latest)))
        (is (value/nothing? (read-value owner 'other-latest)))
        (publish 'source-b :one 1 9)
        (await! owner #(= [9] (read-value owner 'other-latest)))
        (is (= [1 2] (read-value owner 'latest)))
        (publish 'source-a :zero 3 0)
        (await! owner #(= [1 2 0] (read-value owner 'initial-current)))
        (is (= [1 2] (read-value owner 'latest)))
        (doseq [[revision v] [[1 10] [2 20]]]
          (host/submit! owner
                        (session/cell-input (session/binding-cell (host/current-session owner) 'changing-record)
                                            (publication/active :changing revision {:id revision :time revision :value v})))
          (await! owner #(= revision (count (or (when (vector? (read-value owner 'retained-values))
                                                  (read-value owner 'retained-values)) [])))))
        (is (= [10 20] (read-value owner 'retained-values))))
      (finally (host/close! owner)))))

(deftest completed-source-preserves-successful-prefix-and-rejects-truncation
  (doseq [[id reply expected]
          [["prefix" {:kind :reply :text "(define admitted-value 7) (define admitted-value 8)"
                      :thinking "private" :finish-reason "stop" :truncated? false} 1]
           ["truncated" {:kind :reply :text "(define must-not-exist 8)" :thinking ""
                         :finish-reason "length" :truncated? true} 0]]]
    (let [{:keys [owner journal deliveries]}
          (soul/open! root {:transport (fn [_] (assoc reply :test-case id))})]
      (try
        (await! owner #(= :rejected (:status (read-value owner 'observe-cell))))
        (is (= expected (count (filter #(= :admitted (get-in % [:receipt :status])) @journal))))
        (is (empty? @deliveries))
        (when (= id "prefix")
          (let [target (:id (read-value owner 'generated-environment))
                current (host/current-session owner)]
            (is (= 7 (connected/payload
                      (net/network-cell-strongest
                       (session/network current) (session/scoped-binding-cell current target 'admitted-value)))))
            (is (thrown? Exception (session/binding-cell current 'admitted-value)))))
        (finally (host/close! owner))))))

(deftest definitions-survive-later-responses-and-source-replay
  (let [requests (atom [])
        {:keys [owner journal]}
        (soul/open! root {:transport (fn [request]
                                       (swap! requests conj request)
                                       {:kind :reply :finish-reason "stop" :truncated? false :thinking ""
                                        :text (if (= "setup" (:id request))
                                                "(define retained-value 42)"
                                                "(define second-value 17)")})})]
    (try
      (await! owner #(= :admitted (:status (read-value owner 'observe-cell))))
      (let [extension (session/session-extension
                       {:id :second-request
                        :files {"second-request.lain"
                                "(-> (data:record :id \"next\" :text \"next\") (model:input m))"}})]
        (host/extend! owner extension)
        (await! owner #(= "next" (:response (last @journal))))
        (await! owner #(= :admitted (:status (read-value owner 'observe-cell))))
        (host/extend! owner extension)
        (is (= 2 (count @requests)))
        (host/extend-source! owner
                             "(source:admit (model:output m) generated-environment (cell:reference (model:output m)))")
        (await! owner #(= 3 (count @journal)))
        (is (= 2 (count @requests)))
        (let [current (host/current-session owner)
              target (:id (read-value owner 'generated-environment))]
          (is (= 42 (connected/payload (net/network-cell-strongest
                                        (session/network current)
                                        (session/scoped-binding-cell current target 'retained-value)))))
          (is (= 17 (connected/payload (net/network-cell-strongest
                                        (session/network current)
                                        (session/scoped-binding-cell current target 'second-value)))))))
      (finally (host/close! owner)))))

(deftest existing-user-ingress-and-acknowledged-delivery-extension
  (let [{:keys [owner deliveries]}
        (soul/open! root {:transport (fn [_] {:kind :reply :text "" :thinking ""
                                              :finish-reason "stop" :truncated? false})})]
    (try
      (await! owner #(= :admitted (:status (read-value owner 'observe-cell))))
      (let [input {:interface [:user "conversation"]
                   :run-id (:run-id (session/session-identity (host/current-session owner)))
                   :id "ingress-1" :text "actual input"}]
        (host/publish-user-message! owner input)
        (await! owner #(= "actual input" (:content (read-value owner 'user-ingress))))
        (is (= {:id "ingress-1" :time 1 :role "user" :content "actual input"}
               (read-value owner 'user-ingress)))
        (host/publish-user-message! owner input)
        (is (thrown? Exception (host/publish-user-message! owner (assoc input :text "conflict"))))
        (let [reply (session/session-extension
                     {:id :test-acknowledged-delivery
                      :files {"test-delivery.lain"
                              "(define acknowledged (message-to-user (data:record :id \"reply-1\" :time 2 :role \"assistant\" :content \"delivered input\")))"}})]
          (host/extend! owner reply)
          (await! owner #(true? (:acknowledged (read-value owner 'acknowledged))))
          (is (= "delivered input" (:content (first @deliveries))))
          (host/extend! owner reply)
          (is (= 1 (count @deliveries)))))
      (finally (host/close! owner)))))

(deftest reducer-source-in-live-admission-target
  (let [{:keys [owner]}
        (soul/open! root {:transport (fn [_] {:kind :reply :thinking "" :finish-reason "stop" :truncated? false
                                              :text "(define admitted-source (behavior:source))
                                                   (define admitted-sum (behavior:scan admitted-source + 0))
                                                   (define admitted-current (behavior:latest-value admitted-sum))"})})]
    (try
      (await! owner #(= :admitted (:status (read-value owner 'observe-cell))))
      (let [current (host/current-session owner)
            target (:id (read-value owner 'generated-environment))
            source (session/scoped-binding-cell current target 'admitted-source)
            result (session/scoped-binding-cell current target 'admitted-current)]
        (host/submit! owner (session/cell-input source (collection/content
                                                        {:base (temporal/occurrence :one 1 5) :support #{}})))
        (host/step! owner)
        (let [network (session/network (host/current-session owner))
              sum (session/scoped-binding-cell current target 'admitted-sum)]
          (spit (str root "/runtime/soul-target-debug.edn")
                (pr-str (into {} (map (fn [[name id]]
                                        (let [content (net/network-cell-content network id)]
                                          [name {:keys (when (map? content) (keys content))
                                                 :reducer? (reducer/reducer-cell? content)
                                                 :result (:result (reducer/reducer-slots content))
                                                 :strongest (when-not (reducer/reducer-cell? content)
                                                              (net/network-cell-strongest network id))}]))
                                      [[:source source] [:sum sum] [:current result]])))))
        (is (= 5 (connected/payload (net/network-cell-strongest
                                     (session/network (host/current-session owner)) result)))))
      (finally (host/close! owner)))))

(deftest staged-context-retains-model-child-code-and-real-memory-feedback
  (let [requests (atom [])
        orientation "(define orient:source (behavior:source))
                     (-> (event:point \"a\" 1 2) orient:source)
                     (-> (event:point \"b\" 2 3) orient:source)
                     (define orient:sum (behavior:scan orient:source + 0))
                     (define orient:reading (behavior:latest-value orient:sum 0))
                     (-> (cell:value (cell:reference orient:reading)) (cell:input (data:get :orientation-value memory-cell)))
                     (-> (inspect:cell (cell:reference orient:reading)) (cell:input (data:get :orientation-edge memory-cell)))"
        {:keys [owner journal]}
        (soul/open! root {:transport (fn [request]
                                       (swap! requests conj request)
                                       {:kind :reply :thinking "" :finish-reason "stop" :truncated? false
                                        :text (if (= "setup" (:id request)) orientation "(define construction-ready 1)")})})]
    (try
      (await! owner #(= "setup" (:response (read-value owner 'observe-cell))))
      (await! owner #(= 5 (:value (read-value owner 'orientation-result-cell))))
      (let [before (session/session-identity (host/current-session owner))
            target (:id (read-value owner 'generated-environment))]
        (is (= (session/binding-cell (host/current-session owner) 'memory-cell)
               (session/scoped-binding-cell (host/current-session owner) target 'memory-cell)))
        (is (= 5 (get-in (read-value owner 'orientation-result-cell) [:edge :value])))
        (is (str/includes? (:text (first @requests)) "First orient yourself"))
        (soul/advance-context! owner)
        (await! owner #(= "construction" (:response (read-value owner 'observe-cell))))
        (is (= before (session/session-identity (host/current-session owner))))
        (is (= target (:id (read-value owner 'generated-environment))))
        (is (= ["setup" "construction"] (mapv :id @requests)))
        (is (str/includes? (:text (last @requests)) "orientation-result-cell"))
        (is (str/includes? (:text (last @requests)) ":value 5"))
        (is (not (str/includes? (:text (last @requests)) "construction-ready")))
        (is (= orientation (:source (first (read-value owner 'code-history-cell)))))
        (is (= :admitted (get-in (last @journal) [:receipt :status]))))
      (finally (host/close! owner)))))

(deftest reference-port-crosses-lexical-child
  (let [owner (host/open! (model/compiler))]
    (try
      (host/extend! owner (soul/extension owner (atom [])))
      (host/extend! owner (model/declaration root "test/fixtures/operator-examples-base.lain" {}))
      (host/extend-source! owner "(define port-target)
                                 (define memory-port (data:record :value (cell:reference port-target)))
                                 (define port-environment (lexical-child (extend-env (empty-env) (env:reference-slot \"memory-port\" (cell:reference memory-port)))))")
      (let [e (read-value owner 'port-environment)]
        (host/extend! owner (session/session-extension
                             {:id :write-reference-port :target (:id e) :frames (:frames e)
                              :files {"write-port.lain" "(-> 7 (cell:input (data:get :value memory-port)))"}}))
        (host/step! owner)
        (is (= 7 (read-value owner 'port-target))))
      (finally (host/close! owner)))))

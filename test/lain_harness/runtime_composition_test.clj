(ns lain-harness.runtime-composition-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [lain-harness.model-io-session :as model]
            [lain-harness.premises :as premises]
            [lain-harness.soul-session :as soul]
            [propagators.compiler-2.session :as session]
            [propagators.compiler-2.session.connected :as connected]
            [propagators.compiler-2.session.host :as host]
            [propagators.network :as net]))

(def root (System/getenv "LAIN_HARNESS_ROOT"))
(defn read-value [owner name]
  (connected/payload (session/binding-value (host/current-session owner) name)))
(defn await! [owner ready?]
  (let [deadline (+ (System/currentTimeMillis) 45000)]
    (loop []
      (when-let [error (first (host/failures owner))] (throw error))
      (cond (ready?) true
            (> (System/currentTimeMillis) deadline) (throw (ex-info "Runtime composition timed out" {}))
            :else (do (Thread/sleep 20) (recur))))))

(deftest clear-runtime-definitions-and-completed-admission
  (let [base (model/compiler)
        owner (host/open! (session/create-session
                           (assoc (session/session-identity base)
                                  :native-extensions (:bootstrap (session/session-declarations base))
                                  :runtime-options (:options (session/session-declarations base))
                                  :propagation-options {:max-steps 300000 :timeout-ms 180000})))
        journal (atom []) requests (atom [])
        options {:transport (fn [request]
                              (swap! requests conj request)
                              (let [truncated? (str/includes? (:text request) "truncated")]
                                {:kind :reply :thinking "not admitted as source"
                                 :finish-reason (if truncated? "length" "stop")
                                 :truncated? truncated?
                                 :text (if (= "runtime-a-next" (:id request)) "(define after-update 11)" (if truncated? "(define should-not-exist 13)"
                                                                                                             (if (str/includes? (:text request) "alpha")
                                                                                                               "(define retained 7)" "(define retained 9)")))}))}]
    (try
      (host/extend! owner (soul/extension owner journal))
      (host/extend! owner (model/declaration root "soul-refined.lain" options))
      (is (empty? @requests) "Runtime definitions alone start no model")
      (host/extend-source! owner (slurp (str root "/test/fixtures/runtime-composition.lain")))
      (await! owner #(and (= :admitted (:status (read-value owner 'receipt-a)))
                          (= :admitted (:status (read-value owner 'receipt-b)))
                          (= :rejected (:status (read-value owner 'receipt-c)))))
      (let [current (host/current-session owner)
            a (:id (read-value owner 'child-a)) b (:id (read-value owner 'child-b))
            value-at (fn [target name]
                       (connected/payload (net/network-cell-strongest
                                           (session/network current)
                                           (session/scoped-binding-cell current target name))))]
        (is (empty? (:cells (session/session-declarations current)))
            "Extensions add source and propagators, without pre-seeded cells")
        (is (every? #(contains? (net/net-env (session/network current)) (:source-result-reference %))
                    (filter #(= :admitted (get-in % [:receipt :status])) @journal))
            "Admission refers to canonical source-result cells")
        (is (= 3 (count @requests)))
        (is (not= a b))
        (is (= 7 (value-at a 'retained)))
        (is (= 9 (value-at b 'retained)))
        (is (thrown? Exception (session/binding-cell current 'retained)))
        (is (= 2 (count (filter #(= :admitted (get-in % [:receipt :status])) @journal)))))
      (host/extend-source! owner
                           "(io-model:input ((slot :worker) model-a) (data:record :id \"runtime-a-next\" :text \"alpha next\"))")
      (await! owner #(= "runtime-a-next" (:response (read-value owner 'receipt-a))))
      (let [current (host/current-session owner)
            target (:id (read-value owner 'child-a))]
        (is (= 4 (count @requests)))
        (is (= 11 (connected/payload (net/network-cell-strongest
                                      (session/network current)
                                      (session/scoped-binding-cell current target 'after-update))))))
      (finally (host/close! owner)))))

(deftest premise-publication-uses-an-existing-lain-endpoint
  (let [owner (host/open! (model/compiler))]
    (try
      (host/extend-source! owner "(define observation)")
      (let [reference (session/binding-cell (host/current-session owner) 'observation)]
        (is (= reference (premises/source! owner reference 7)))
        (is (= 7 (read-value owner 'observation)))
        (is (empty? (:cells (session/session-declarations (host/current-session owner)))))
        (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Declare the premise source in Lain"
                              (premises/source! owner (random-uuid) 9))))
      (finally (host/close! owner)))))

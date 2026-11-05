(ns lain-harness.operator-examples-test
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [lain-harness.environment :as environment]
            [lain-harness.soul-session :as soul]
            [lain-harness.model-io-session :as model]
            [lain-harness.soul-test :as support]
            [propagators.compiler-2.session :as session]
            [propagators.compiler-2.session.connected :as connected]
            [propagators.compiler-2.session.host :as host]
            [propagators.datastructures.reducer-cell :as reducer]
            [propagators.network :as net]
            [propagators.stdlib.source-publication :as publication]))

(def root (System/getenv "LAIN_HARNESS_ROOT"))
(defn catalog [] (edn/read-string (slurp (str root "/docs/operator-examples.edn"))))
(defn observed [owner binding] (support/read-value owner (symbol binding)))
(defn matches? [owner {:keys [binding kind expected target parent name title headline content request]}]
  (try
    (let [current (host/current-session owner)
          v (observed owner binding)]
      (case kind
        :value (= expected v)
        :code-history (and (vector? v) (seq v) (every? #(and (contains? % :source) (not (contains? % :thinking))) v))
        :behavior-values (= expected (mapv :value (:records (reducer/reduced-result v))))
        :reference-of (= v (session/binding-cell current (symbol target)))
        :cell-view (and (= expected (connected/payload (:value v)))
                        (every? #(contains? v %) [:edge :value :inputs :outputs]))
        :environment (= (type v) (type (environment/reference nil)))
        :child-of (let [p (observed owner parent)]
                    (and (not= (:id p) (:id v)) (= (:id p) (get-in v [:frames (:id v) :parent]))))
        :contribution (and (instance? lain_harness.environment.Contribution v)
                           (= name (str (:name v)))
                           (= expected (connected/payload (net/network-cell-strongest (session/network current) (:cell v)))))
        :overview-has (some #(and (= title (:title %)) (= headline (:headline %)))
                            (edn/read-string {:default (fn [_ value] value)} v))
        :model-response (and (= :reply (:kind v)) (= request (:request-id v)))
        :acknowledgement (and (true? (:acknowledged v)) (= content (:content v)))
        :admission (and (= :admitted (:status v)) (= 1 (:forms v))
                        (= expected (connected/payload
                                     (net/network-cell-strongest
                                      (session/network current)
                                      (session/scoped-binding-cell current (:id (observed owner target)) (symbol name))))))
        false))
    (catch Exception _ false)))

(deftest every-advertised-capability-has-an-example
  (let [examples (catalog)
        documented (into #{} (mapcat :operators) examples)
        advertised (map second (re-seq #"\(construct-concept-of \"([^\"]+)\"" (slurp (str root "/soul-refined.lain"))))
        callable-names (remove #(str/ends-with? % "-cell") advertised)
        callable-names (remove #{"user-ingress" "history"} callable-names)]
    (is (every? #(and (seq (:source %)) (seq (:checks %))) examples))
    (doseq [operator callable-names]
      (is (contains? documented operator) (str "Missing runnable example for " operator)))))

(def contextual-operators #{"model:request" "message-to-user" "memory-cell" "history" "construct-concept-of"})
(defn contextual? [entry] (some contextual-operators (:operators entry)))

(defn verify! [owner entry]
  (println "EXAMPLE" (str/join ", " (:operators entry)))
  (flush)
  (host/extend! owner (session/session-extension
                       {:id [::example (:operators entry)]
                        :files {(str "examples/" (first (:operators entry)) ".lain") (:source entry)}}))
  (doseq [[revision {:keys [binding value expected] observed-binding :observed}] (map-indexed vector (:publications entry))]
    (host/submit! owner
                  (session/cell-input
                   (session/binding-cell (host/current-session owner) (symbol binding))
                   (publication/active [::example-input binding] (inc revision) value)))
    (host/step! owner)
    (when observed-binding
      (support/await! owner #(= expected (observed owner observed-binding)))
      (is (= expected (observed owner observed-binding)))))
  (try (support/await! owner #(every? (partial matches? owner) (:checks entry)))
       (catch Exception _ nil))
  (doseq [check (:checks entry)] (is (matches? owner check) (str (:operators entry) " " check)))
  (when (some #{"source:admit"} (:operators entry))
    (support/await! owner
                    #(let [receipts (filter (fn [[id _]] (= 'source:admit (nth id 2 nil)))
                                            (host/receipts owner))]
                       (and (seq receipts)
                            (every? (fn [[_ receipt]] (and (= :completed (:status receipt))
                                                           (= :closed (:cleanup receipt)))) receipts))))))

(deftest compile-independent-core-examples
  (let [preparation (host/open! (model/compiler))
        initial (try
                  (host/extend! preparation (soul/extension preparation (atom [])))
                  (host/extend! preparation (model/declaration root "test/fixtures/operator-examples-base.lain"
                                                               {:transport (fn [_] (throw (ex-info "No model in pure examples" {})))}))
                  (host/current-session preparation)
                  (finally (host/close! preparation)))]
    (doseq [entry (remove contextual? (catalog))]
      (let [owner (host/open! initial)]
        (try (verify! owner entry) (finally (host/close! owner)))))))

(deftest compile-context-and-feedback-examples
  (let [requests (atom [])
        {:keys [owner deliveries journal]}
        (soul/open! root {:transport (fn [request]
                                       (swap! requests conj request)
                                       {:kind :reply :thinking "" :finish-reason "stop" :truncated? false
                                        :text (case (:id request)
                                                "setup" ""
                                                "doc-memory-probe" "(define doc-memory-generated 7)"
                                                "(define doc-model-generated 42)")})})]
    (try
      (support/await! owner #(= :admitted (:status (observed owner "observe-cell"))))
      (doseq [entry (filter contextual? (catalog))]
        (verify! owner entry)
        (when-let [request-id (case (first (:operators entry))
                                "model:request" "doc-model-probe"
                                "memory-cell" "doc-memory-probe" nil)]
          (support/await! owner #(and (= request-id (:response (last @journal)))
                                      (= :admitted (:status (observed owner "observe-cell")))))))
      (is (= [{:role "user" :content "Return only (define doc-model-generated 42)"}]
             (:messages (first (filter #(= "doc-model-probe" (:id %)) @requests)))))
      (is (= "memory-score=7" (:text (first (filter #(= "doc-memory-probe" (:id %)) @requests)))))
      (is (= "operator probe" (:content (first @deliveries))))
      (support/await! owner #(= 3 (count (observed owner "code-history-cell"))))
      (is (= ["" "(define doc-model-generated 42)" "(define doc-memory-generated 7)"]
             (mapv :source (observed owner "code-history-cell"))))
      (is (str/includes? (observed owner "guide-core-cell") "Compose Relationships into Expressions"))
      (is (str/includes? (observed owner "guide-examples-cell") "Textual order never schedules execution"))
      (doseq [{:keys [source]} (catalog)]
        (is (str/includes? (observed owner "operator-examples-cell") source)))
      (finally (host/close! owner)))))

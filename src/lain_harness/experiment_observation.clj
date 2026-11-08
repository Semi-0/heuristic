(ns lain-harness.experiment-observation
  "Pure data readers and live observations. No requests, stages or retry policy."
  (:require [clojure.edn :as edn]
            [clojure.set :as set]
            [clojure.string :as str]
            [propagators.cells.value :as value]
            [propagators.network :as net]
            [propagators.ids :as ids]
            [lain-harness.connected-data :as cd]
            [lain-harness.premise-experiment :as premise]
            [lain-harness.soul-session :as soul]
            [propagators.compiler-2.model.env :as env]
            [propagators.compiler-2.model.env.index :as index]
            [propagators.compiler-2.session :as session]
            [propagators.compiler-2.session.connected :as connected]))

(defn binding-views
  "Read selected runtime-local edges for a diagnostic report; never change them."
  [network names]
  (vec (for [[key _] (index/runtime-topology network)
             :when (and (= :binding (first key)) (contains? names (nth key 2 nil)))
             :let [reference (env/local-binding-id network (second key) (nth key 2))
                   view (soul/edge-view network reference)
                   value (:value view)]]
         (assoc view :frame (second key) :name (nth key 2)
                :value (cond
                         (= 'context (nth key 2)) value
                         (and (= 'snapshot (nth key 2)) (map? value)) (select-keys value [:values])
                         (map? value)
                         (assoc (select-keys value [:id :request-id :status :kind :finish-reason :truncated? :decision :ready?])
                                :keys (vec (keys value)))
                         :else value)))))

(defn description [network [environment selected]]
  ;; Text, directory and selected values share one immutable network snapshot.
  (let [facts (soul/visible-facts network environment)
        selected-names (set selected)]
    {:text (soul/describe network [environment selected])
     :values (into {} (for [{:keys [name value available]} facts
                            :when (and available (contains? selected-names name))]
                        [name (cd/unpack value)]))}))

(defn inspect-outputs [network [environment references worker]]
  (let [references (cd/unpack references)
        expected (set (vals (:premises references)))
        views (into {} (for [[name reference] (dissoc references :premises)]
                         [name (premise/inspect-value network reference)]))
        supports (into {} (for [[name view] views]
                            [name (set (map :source (:support view)))]))]
    {:child (:id environment)
     :worker (into {} (cd/unpack worker))
     :identity {:id (:session-id (cd/unpack worker)) :run-id (:run-id (cd/unpack worker))}
     :child-bindings (soul/overview network [environment []])
     :outputs (into {} (map (fn [[name view]] [name (:value view)])) views)
     :supports supports
     :expected-support expected
     :missing-supports (into {} (for [[name sources] supports
                                      :let [missing (set/difference expected sources)]
                                      :when (seq missing)] [name missing]))}))

(defn facilities []
  (let [primitive (fn [name f] [name (connected/primitive name f)])]
    (session/session-extension
     {:id ::observations
      :primitives
      (into {}
            [(primitive 'data:read-edn (fn [text]
                                         (try {:status :parsed :value (edn/read-string text)}
                                              (catch Exception error {:status :invalid :message (ex-message error)}))))
             (primitive 'data:has? (fn [record key] (and (map? record) (contains? record key))))
             (primitive 'data:strings? (fn [record fields]
                                         (and (map? record)
                                              (every? #(and (string? (get record %))
                                                            (not (str/blank? (get record %)))) fields))))
             (primitive 'seq:of vector)
             (primitive 'set:subset? set/subset?)
             (primitive 'set:intersection set/intersection)
             (primitive 'logic:all (fn [& xs] (every? true? xs)))
             (primitive 'logic:any (fn [& xs] (boolean (some true? xs))))
             (primitive 'logic:not not)
             (primitive 'text:prefix? (fn [prefix text] (and (string? text) (str/starts-with? text prefix))))
             ['environment:snapshot
              (cd/observation-operator 'environment:snapshot description identity
                                       (fn [network _ [environment]]
                                         (map :cell (soul/visible-facts network environment))) true)]
             ['environment:values
              (cd/observation-operator
               'environment:values
               (fn [network [environment names stop]]
                 (if (and (ids/node-id? stop) (not (value/nothing? (net/network-cell-strongest network stop))))
                   connected/pending
                   (let [wanted (set names)]
                     (into {} (for [{:keys [name value available]} (soul/visible-facts network environment)
                                    :when (and available (wanted name))] [name (cd/unpack value)])))))
               identity
               (fn [network _ [environment names stop]]
                 (if (and (ids/node-id? stop) (not (value/nothing? (net/network-cell-strongest network stop))))
                   [stop]
                   (let [wanted (set names)]
                     (concat (when (ids/node-id? stop) [stop])
                             (map :cell (filter #(wanted (:name %)) (soul/visible-facts network environment)))))))
               true)]
             ['environment:snapshot-once
              (cd/observation-once-operator
               'environment:snapshot-once
               (fn [network [environment selected required]]
                 (let [available (set (map :name (filter :available (soul/visible-facts network environment))))]
                   (if (every? available required) (description network [environment selected]) connected/pending)))
               (fn [network _ [environment]] (map :cell (soul/visible-facts network environment))))]
             ['inspect:outputs-once
              (cd/observation-once-operator
               'inspect:outputs-once inspect-outputs
               (fn [_ _ [_ references]]
                 (when (map? references)
                   (concat (vals (dissoc references :premises)) (vals (:premises references))))))]
             ['inspect:outputs
              (cd/observation-operator
               'inspect:outputs inspect-outputs identity
               (fn [network _ [environment references]]
                 (concat (when (map? (cd/unpack references))
                           (concat (vals (dissoc (cd/unpack references) :premises))
                                   (vals (:premises (cd/unpack references)))))
                         (map :cell (soul/visible-facts network environment))))
               true)]])})))

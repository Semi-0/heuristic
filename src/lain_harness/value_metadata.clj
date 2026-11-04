(ns lain-harness.value-metadata
  "Immediate connected slot metadata. Lain chooses traversal and presentation."
  (:require [lain-harness.connected-data :as cd]
            [propagators.cells.value :as value]
            [propagators.compiler-2.runtime.application :as application]
            [propagators.compiler-2.model.operator-value :as operator]
            [propagators.compiler-2.model.closure-value :as closure]
            [propagators.compiler-2.session :as session]
            [propagators.compiler-2.session.connected :as connected]

            [propagators.datastructures.compound-object :as object]
            [propagators.datastructures.layered-value :as datum]
            [propagators.experimental.visualization.data :as data]))
(defn kind [v]
  (cond (object/accessor-network? v) :compound
        (or (application/compiler-callable? v) (operator/operator-closure? v) (closure/closure-info? v)) :callable
        :else :scalar))
(defn slots [network [v]]
  (mapv (fn [key]
          (let [field (data/slot-value network v key)]
            {:name (str key) :key key :available (not (value/unusable? field))
             :cell (first (sort-by str (object/accessor-parent-ids v key)))
             :premises (datum/support-of field)
             :value (if (value/unusable? field) :unavailable (data/payload field))}))
        (sort-by str (object/accessor-slot-keys v))))
(defn as-list [xs]
  (if (empty? xs) connected/empty-list
      (reduce (fn [tail x] {:car x :cdr tail}) connected/empty-list (reverse xs))))
(defn extensions []
  (mapv (fn [[name primitive]]
          (let [id (keyword "lain-harness.value-metadata" (str name))]
            (session/session-extension {:id id :primitives {name primitive}
                                        :imports {["lain_harness/value_metadata.clj" id 1] [id 1]}})))
        [['value:kind (connected/primitive 'value:kind kind)]
         ['value:slots (cd/observation-operator 'value:slots slots #(as-list (cd/unpack %))
                                                (fn [network inputs _] (mapcat #(cd/value-cells network %) inputs)) true)]]))

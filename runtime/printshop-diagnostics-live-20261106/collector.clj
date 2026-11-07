(ns diagnostics (:require [clojure.edn :as edn] [clojure.walk :as walk]))

(defn source-index [{:keys [action source]}]
  (try
    (mapv (fn [i form]
            (let [symbols (atom #{}) calls (atom #{})]
              (walk/postwalk (fn [x] (when (symbol? x) (swap! symbols conj (str x)))
                              (when (and (seq? x) (symbol? (first x))) (swap! calls conj (str (first x)))) x) form)
              {:action action :form-index i :form (pr-str form) :symbols @symbols :calls @calls}))
          (range) (edn/read-string (str "[" source "]")))
    (catch Throwable e [{:action action :reader-error (.getMessage e)}])))

(defn collect-diagnostics [{:keys [sources facts outputs admission]}]
  (let [index (mapcat source-index sources)
        references (fn [name] (mapv #(dissoc % :symbols :calls) (filter #(contains? (:symbols %) name) index)))]
    (vec (concat
      (when (= :rejected (:status admission))
        [{:kind :admission-error :receipt admission :evidence :admission-receipt}])
      (for [f facts :when (and (= :unknown (:state f))
                               (some #(contains? (:calls %) (:name f)) index))]
        {:kind :unresolved-operator-binding :name (:name f) :cell (:cell f)
         :source-references (references (:name f)) :evidence :source-reference
         :meaning "An operator-position symbol currently has an unknown binding; this does not by itself prove a compiler error."})
      (for [o outputs :when (#{:unknown :contradictory} (:value o))]
        (merge o {:kind (if (= :contradictory (:value o)) :runtime-contradiction :blocked-output)
                  :source-references (references (:name o)) :evidence :runtime-state-and-direct-input-edges
                  :meaning "Inputs and supports come from the runtime. Source references identify uses, not a proven causal action. Unknown can mean waiting for information."}))))))

;; Keep the caller's namespace stable when loaded by the experiment.
(in-ns 'user)

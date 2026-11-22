(ns lain-harness.context-inspection
  "Bounded immediate topology observations. Never walks neighboring nodes."
  (:require [lain-harness.connected-data :as cd]
            [propagators.compiler-2.runtime.application :as application]
            [propagators.compiler-2.model.closure-value :as closure]
            [propagators.network :as net]))

(defn digest [value]
  (format "%064x" (java.math.BigInteger. 1
                    (.digest (java.security.MessageDigest/getInstance "SHA-256")
                             (.getBytes (pr-str value) "UTF-8")))))

(defn value-summary [v]
  (cond
    (and (map? v) (get v application/compiler-callable-key))
    (let [declaration (application/callable-declaration v)
          inputs (when (closure/closure-info? declaration) (closure/closure-inputs declaration))]
     {:kind :callable
      :parameters (mapv #(subs (str %) 0 (min 128 (count (str %)))) (take 32 inputs))
      :interface-omitted? (> (count inputs) 32)
     :declaration (get v application/declaration-id-key)
     :captured-environment (get v application/captured-environment-key)
     :argument-count (application/callable-argument-count v)})
    (string? v) (let [n (.codePointCount ^String v 0 (.length ^String v))
                      end (.offsetByCodePoints ^String v 0 (min n 512))]
                  {:kind :text :prefix (subs v 0 end) :codepoints n :omitted? (> n 512)})
    (or (number? v) (keyword? v) (boolean? v) (nil? v)) {:kind :scalar :value v}
    :else {:kind :structured :omitted? true}))

(defn inspect [network reference cursor limit]
  (when-not (and (pos-int? limit) (<= limit 32))
    (throw (ex-info "Inspection limit must be 1..32" {})))
  (let [graph (net/net-graph network)]
    (if-not (contains? graph reference)
      {:status :missing-live-reference :reference reference}
      (let [node (get graph reference)
            edges (vec (sort-by pr-str (concat (map #(vector :input %) (:inputs node))
                                               (map #(vector :output %) (:outputs node)))))
            signature (digest edges)
            offset (or (:offset cursor) 0)]
        (when (and cursor (not (and (= reference (:reference cursor))
                                    (= signature (:signature cursor))
                                    (nat-int? offset) (<= offset (count edges)))))
          (throw (ex-info "Topology changed or invalid continuation; inspect again" {:reference reference})))
        (let [end (min (count edges) (+ offset limit))]
          {:status :observed :reference reference :depth 0
           :value (value-summary (cd/unpack (net/network-cell-strongest network reference)))
           :edges (subvec edges offset end)
           :next (when (< end (count edges))
                   {:reference reference :signature signature :offset end})
           :complete? (= end (count edges))})))))

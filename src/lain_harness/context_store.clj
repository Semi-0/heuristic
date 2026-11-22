(ns lain-harness.context-store
  "Immutable evidence and evidence-backed claims in the existing catalog.
  Functions here are IO adapters; facilities dispatch them only after commit."
  (:require [clojure.edn :as edn]
            [datomic.client.api :as d]
            [lain-harness.catalog :as catalog]
            [lain-harness.context-inspection :as inspection]
            [lain-harness.connected-data :as cd]
            [propagators.compiler-2.runtime.application :as application]
            [propagators.compiler-2.session.host :as host]
            [propagators.network :as net]
            [propagators.compiler-2.session :as session]
            [propagators.compiler-2.session.operators :as op]))

(def schema
  (mapv (fn [[ident type many?]]
          {:db/ident ident :db/valueType type
           :db/cardinality (if many? :db.cardinality/many :db.cardinality/one)})
        [[:catalog/evidence-chunk :db.type/string true]
         [:catalog/evidence-kind :db.type/keyword false]
         [:catalog/evidence :db.type/ref true]
         [:catalog/claim-chunk :db.type/string true]
         [:catalog/author :db.type/keyword false]
         [:catalog/supersedes :db.type/ref true]]))

(defn install! [store]
  (d/transact (:connection store) {:tx-data schema})
  store)

(defn restore [chunks] (apply str (map #(subs % 9) (sort chunks))))
(defn entity [store id]
  (d/pull (d/db (:connection store)) '[*] [:catalog/id id]))

(defn archive! [store id kind text]
  (when-not (and (keyword? id) (keyword? kind) (string? text))
    (throw (ex-info "Evidence requires keyword id/kind and exact text" {})))
  (locking (:references store)
    (let [old (entity store id)
          chunks (catalog/archive-chunks text)]
      (if (:catalog/id old)
        (when-not (and (= kind (:catalog/evidence-kind old))
                      (= (set chunks) (set (:catalog/evidence-chunk old))))
          (throw (ex-info "Immutable evidence identity conflicts" {:id id})))
        (d/transact (:connection store)
                    {:tx-data [{:catalog/id id :catalog/evidence-kind kind
                                :catalog/author :observation
                                :catalog/evidence-chunk chunks}]}))))
  (let [receipt {:status :archived :id id :kind kind}]
    (swap! (:journal store) conj (assoc receipt :operation :archive))
    receipt))

(defn read! [store id offset limit]
  (when-not (and (nat-int? offset) (pos-int? limit) (<= limit 2048))
    (throw (ex-info "Read requires codepoint offset and limit 1..2048" {})))
  (let [record (entity store id)]
    (when-not (:catalog/evidence-kind record)
      (throw (ex-info "Missing evidence" {:id id})))
    (let [text (restore (:catalog/evidence-chunk record))
          points (.toArray (.codePoints ^String text))
          n (alength points) end (min n (+ offset limit))]
      (when (> offset n) (throw (ex-info "Offset beyond evidence" {:id id})))
      (let [result {:id id :kind (:catalog/evidence-kind record) :offset offset
                    :text (String. points offset (- end offset))
                    :next (when (< end n) end) :complete? (= end n) :codepoints n}]
        (swap! (:journal store) conj {:operation :read :result result})
        result))))

(def claim-keys
  #{:catalog/id :catalog/description :catalog/interface :catalog/assumptions
    :catalog/example :catalog/status :catalog/tag :catalog/capability
    :catalog/depends-on :catalog/evidence :catalog/supersedes})

(defn index! [store text]
  (let [record (edn/read-string text) id (:catalog/id record)
        evidence (:catalog/evidence record)]
    (when-not (and (map? record) (keyword? id)
                   (every? claim-keys (keys record))
                   (string? (:catalog/description record)) (seq evidence))
      (throw (ex-info "Index requires description and supporting evidence references" {})))
    (locking (:references store)
      (doseq [[attribute refs] (select-keys record [:catalog/evidence :catalog/capability :catalog/depends-on :catalog/supersedes])
              ref refs]
        (when-not (and (vector? ref) (= 2 (count ref)) (= :catalog/id (first ref))
                       (keyword? (second ref))
                       (if (= attribute :catalog/evidence)
                         (:catalog/evidence-kind (entity store (second ref)))
                         (:catalog/id (entity store (second ref)))))
          (throw (ex-info "Invalid index reference" {:attribute attribute :reference ref}))))
      (let [old (entity store id)
            registered (when (:catalog/registration old)
                         (edn/read-string (:catalog/registration old)))
            chunks (catalog/archive-chunks (pr-str record))]
        (when (and (:catalog/id old) (not (:catalog/claim-chunk old)) (nil? registered))
          (throw (ex-info "Claim identity already belongs to another record" {:id id})))
        (if (:catalog/claim-chunk old)
          (when-not (= record (edn/read-string (restore (:catalog/claim-chunk old))))
            (throw (ex-info "Immutable claim identity conflicts; create a version" {:id id})))
          (do
            ;; An existing callable registration may be annotated, never rewritten.
            (doseq [[k v] (dissoc record :catalog/evidence)]
              (when (and (contains? registered k) (not= v (get registered k)))
                (throw (ex-info "Index conflicts with existing metadata" {:id id :attribute k}))))
            (d/transact (:connection store)
                        {:tx-data [(assoc record :catalog/author :agent
                                          :catalog/claim-chunk chunks)]})))))
    (swap! (:journal store) conj {:operation :index :id id :claim record})
    {:status :indexed :id id :certified? false}))

(defn page! [store query-text rules-text cursor limit]
  (when-not (and (pos-int? limit) (<= limit 32))
    (throw (ex-info "Page limit must be 1..32" {})))
  (let [query (edn/read-string query-text) rules (edn/read-string rules-text)
        _ (catalog/validate-query query rules)
        _ (when (some #{:catalog/evidence-chunk :catalog/claim-chunk
                        :catalog/source-chunk :catalog/admission-chunk :catalog/registration}
                      (tree-seq coll? seq [query rules]))
            (throw (ex-info "Use chunked evidence reads for archived content" {})))
        current (d/db (:connection store))
        _ (when (and cursor (not (and (= query (:query cursor)) (= rules (:rules cursor))
                                      (nat-int? (:offset cursor)) (integer? (:basis cursor))
                                      (<= (:basis cursor) (:t current)))))
            (throw (ex-info "Cursor does not match query" {})))
        basis (or (:basis cursor) (:t current))
        database (if cursor (d/as-of current (:basis cursor)) current)
        offset (or (:offset cursor) 0)
        rows (vec (sort-by pr-str (d/q {:query query :args [database rules]})))
        end (min (count rows) (+ offset limit))]
    (when (> offset (count rows)) (throw (ex-info "Invalid page offset" {})))
    (let [result {:rows (subvec rows offset end) :basis basis
                  :next (when (< end (count rows))
                          {:query query :rules rules :basis basis :offset end})
                  :complete? (= end (count rows)) :absence-established? false}]
      (swap! (:journal store) conj {:operation :page :query query :rules rules :result result})
      result)))

(defn facilities [store]
  (let [operations {:archive (partial archive! store) :read (partial read! store)
                    :index (partial index! store) :page (partial page! store)
                    :resolve (partial catalog/resolve! store) :register (partial catalog/register! store)}
        interpreter
        (reify op/EffectInterpreter
          (interpret-effect! [_ owner context {:keys [operation arguments]} emit!]
            (let [result (try
                           (let [network (session/network (host/current-session owner))]
                             (case operation
                               :inspect (apply inspection/inspect network arguments)
                               :register (let [reference (second arguments)
                                               v (when (contains? (net/net-graph network) reference)
                                                   (cd/unpack (net/network-cell-strongest network reference)))]
                                           (when-not (and (map? v) (get v application/compiler-callable-key))
                                             (throw (ex-info "Registration requires a live callable reference" {})))
                                           (apply (get operations :register) arguments))
                               :resolve (let [reference (apply (get operations :resolve) arguments)]
                                          (if (contains? (net/net-graph network) reference)
                                            reference
                                            {:status :missing-live-reference :id (first arguments)}))
                               (apply (get operations operation) arguments)))
                              (catch Exception e
                                {:status :error :diagnostic (ex-message e) :details (ex-data e)}))]
              (swap! (:journal store) conj {:operation :boundary :capability operation
                                           :arguments arguments :result result})
              (when-not (= :accepted (emit! result))
                (throw (ex-info "Context result delivery failed" {:operation operation})))
              ((:complete! context))
              nil)))]
    (session/session-extension
     {:id ::facilities
      :primitives
      (into {} (for [[name operation] {'context:archive :archive 'context:read :read
                                       'context:index :index 'context:query :page
                                       'context:resolve :resolve 'context:inspect :inspect 'context:register :register}]
                 [name (op/async-op interpreter
                                    (fn [make]
                                      (make (fn [& arguments]
                                              (op/effect-output {:operation operation :arguments arguments})))))]))})))

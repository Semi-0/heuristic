(ns lain-harness.catalog
  "Local discovery storage. No model calls, phase selection, or source admission."
  (:require [clojure.edn :as edn]
            [datomic.client.api :as d]
            [propagators.ids :as ids]
            [propagators.compiler-2.session :as session]
            [propagators.compiler-2.session.connected :as connected]))

(def schema
  (vec (for [[ident type cardinality] [[:catalog/id :db.type/keyword :db.cardinality/one]
                                       [:catalog/description :db.type/string :db.cardinality/one]
                                       [:catalog/source-chunk :db.type/string :db.cardinality/many]
                                       [:catalog/registration :db.type/string :db.cardinality/one]
                                       [:catalog/admission-chunk :db.type/string :db.cardinality/many]
                                       [:catalog/interface :db.type/string :db.cardinality/one]
                                       [:catalog/example :db.type/string :db.cardinality/one]
                                       [:catalog/assumptions :db.type/string :db.cardinality/one]
                                       [:catalog/status :db.type/keyword :db.cardinality/one]
                                       [:catalog/tag :db.type/keyword :db.cardinality/many]
                                       [:catalog/capability :db.type/ref :db.cardinality/many]
                                       [:catalog/depends-on :db.type/ref :db.cardinality/many]]]
         (cond-> {:db/ident ident :db/valueType type :db/cardinality cardinality}
           (= ident :catalog/id) (assoc :db/unique :db.unique/identity)))))

(defn open! [path]
  (let [client (d/client {:server-type :datomic-local :storage-dir path :system "lain-discovery"})
        _ (d/create-database client {:db-name "catalog"})
        conn (d/connect client {:db-name "catalog"})]
    (d/transact conn {:tx-data schema})
    {:connection conn :references (atom {}) :journal (atom [])}))

(defn seed! [store facts]
  (doseq [fact facts]
    (d/transact (:connection store) {:tx-data [fact]})))

(defn register! [store text reference]
  (let [record (edn/read-string text) id (:catalog/id record)
        references (:references store)]
    (when-not (and (ids/node-id? reference) (keyword? id) (every? #(string? (get record %))
                                                                  [:catalog/description :catalog/interface :catalog/assumptions]))
      (throw (ex-info "Registration requires id, description, interface and assumptions" {})))
    (locking references
      (when-let [saved (:catalog/registration (d/pull (d/db (:connection store))
                                                      [:catalog/registration] [:catalog/id id]))]
        (when-not (= record (edn/read-string saved))
          (throw (ex-info "Persisted catalog identity conflicts; use a new version id" {:id id}))))
      (if-let [old (get @(:references store) id)]
        (when-not (= old {:record record :reference reference})
          (throw (ex-info "Conflicting catalog identity; use a new version id" {:id id})))
        (do (d/transact (:connection store) {:tx-data [(assoc record :catalog/registration (pr-str record))]})
            (swap! (:references store) assoc id {:record record :reference reference})
            (swap! (:journal store) conj {:operation :register :id id :reference reference :record record}))))
    {:status :registered :id id}))

(defn validate-query [query rules]
  (let [sections (reduce (fn [[section result] item]
                           (if (keyword? item)
                             [item (assoc result item [])]
                             [section (update result section (fnil conj []) item)]))
                         [nil {}] query)
        parts (second sections)
        variable? #(and (symbol? %) (.startsWith (str %) "?"))
        names (set (map ffirst rules))
        clause? (fn [clause]
                  (or (and (vector? clause) (= 3 (count clause))
                           (keyword? (second clause))
                           (= "catalog" (namespace (second clause)))
                           (not-any? coll? clause))
                      (and (seq? clause) (contains? names (first clause))
                           (every? #(or (variable? %) (keyword? %) (string? %) (number? %)) (rest clause)))))]
    (when-not (and (vector? query) (<= (count query) 300)
                   (= #{:find :in :where} (set (keys parts)))
                   (= '[$ %] (:in parts))
                   (seq (:find parts)) (every? variable? (:find parts))
                   (seq (:where parts)) (every? clause? (:where parts))
                   (vector? rules) (<= (count rules) 20)
                   (every? (fn [rule] (and (vector? rule) (seq (first rule))
                                           (symbol? (ffirst rule)) (nil? (namespace (ffirst rule)))
                                           (every? variable? (rest (first rule)))
                                           (seq (rest rule)) (every? clause? (rest rule)))) rules))
      (throw (ex-info "Use relation queries with :in $ %, catalog fact clauses and named rules only" {})))
    query))

(defn query! [store text rules-text]
  (let [query (edn/read-string text) rules (edn/read-string rules-text)]
    (validate-query query rules)
    (let [database (d/db (:connection store))
          result (vec (sort-by pr-str (d/q {:query query :args [database rules]})))]
      (when (> (count result) 64) (throw (ex-info "Query exceeds 64 result rows" {})))
      (swap! (:journal store) conj {:operation :query :query query :rules rules :basis (:t database) :result result})
      result)))

(defn archive-chunks [text]
  ;; Datomic Local limits individual values; numbered short chunks retain order.
  (mapv (fn [i points] (format "%08d:%s" i (String. (int-array points) 0 (count points))))
        (range) (partition-all 512 (.toArray (.codePoints ^String text)))))

(defn archive-admission! [store id source receipts]
  ;; Evidence recording only. This does not compile, restore, or validate source.
  (d/transact (:connection store)
              {:tx-data [{:catalog/id id :catalog/source-chunk (archive-chunks source)
                          :catalog/admission-chunk (archive-chunks (pr-str receipts))}]}))

(defn resolve! [store id]
  (let [reference (:reference (get @(:references store) id))]
    (when-not reference (throw (ex-info "No live reference in this session" {:id id})))
    (swap! (:journal store) conj {:operation :resolve :id id :reference reference})
    reference))

(defn facilities [store]
  (session/session-extension
   {:id ::catalog
    :primitives
    (into {} (for [[name f] {'catalog:register (partial register! store)
                             'catalog:query (partial query! store)
                             'catalog:resolve (partial resolve! store)
                             'catalog:entries (fn [_] (mapv :record (vals @(:references store))))}]
               [name (connected/primitive name f)]))}))

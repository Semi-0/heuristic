(ns lain-harness.source-restore
  "Pure-source dependency bundling and integrity checks. No model or session loop."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [datomic.client.api :as d]
            [lain-harness.catalog :as catalog]
            [lain-harness.source-reader :as reader]
            [propagators.compiler-2.session :as session]
            [propagators.compiler-2.session.connected :as connected])
  (:import [java.security MessageDigest]
           [java.math BigInteger]))

(def bundle-id :archive/durable-bundle-v1)
(def pure-operators '#{+ - * / >= <= = str if cell:value cell:reference catalog:resolve catalog:register})

(defn sha256 [text]
  (format "%064x" (BigInteger. 1 (.digest (MessageDigest/getInstance "SHA-256") (.getBytes ^String text "UTF-8")))))

(defn read-archive [store id]
  (let [record (d/pull (d/db (:connection store)) [:catalog/source-chunk :catalog/admission-chunk] [:catalog/id id])
        restore (fn [chunks]
                  (when-not (seq chunks) (throw (ex-info "Missing archive" {:id id})))
                  (let [ordered (sort chunks)]
                    (doseq [[index chunk] (map-indexed vector ordered)]
                      (when-not (str/starts-with? chunk (format "%08d:" index))
                        (throw (ex-info "Non-contiguous archive chunks" {:id id :index index}))))
                    (apply str (map #(subs % 9) ordered))))]
    {:source (restore (:catalog/source-chunk record))
     :receipts (edn/read-string {:default tagged-literal} (restore (:catalog/admission-chunk record)))}))

(defn source-forms [text]
  (loop [text text index 0 result []]
    (let [{:keys [status source remaining message]} (reader/read-next text true)]
      (case status
        :complete result
        :form (recur remaining (inc index) (conj result {:index index :source source :form (edn/read-string source)}))
        (throw (ex-info "Invalid archived source" {:status status :message message}))))))

(defn build-bundle [archives root-id]
  (let [definitions (into {} (for [[module artifact] archives
                                   entry (source-forms (:source artifact))
                                   :let [form (:form entry)]
                                   :when (and (seq? form) (= 'define (first form)) (= 3 (count form)))]
                               [[module (second form)] (assoc entry :module module :name (second form) :rhs (nth form 2))]))
        registrations (into {} (for [[key entry] definitions
                                     :let [rhs (:rhs entry)]
                                     :when (and (seq? rhs) (= 'catalog:register (first rhs)))]
                                 [(:catalog/id (edn/read-string (second rhs))) key]))
        selected (atom #{}) active (atom #{})]
    (letfn [(visit-id [id]
              (if-let [key (get registrations id)] (visit key)
                      (throw (ex-info "Missing saved catalog dependency" {:id id}))))
            (walk-expr [module expression bound]
              (cond
                (symbol? expression)
                (when-not (or (bound expression) (pure-operators expression))
                  (if (contains? definitions [module expression]) (visit [module expression])
                      (throw (ex-info "Unpersisted capture or unsupported native capability" {:module module :symbol expression}))))
                (seq? expression)
                (case (first expression)
                  network (do (when-not (vector? (second expression)) (throw (ex-info "Invalid network parameters" {})))
                              (doseq [body (drop 2 expression)] (walk-expr module body (into bound (second expression)))))
                  let (let [bindings (second expression)
                            locals (reduce (fn [names [name rhs]] (walk-expr module rhs names) (conj names name))
                                           bound (partition 2 bindings))]
                        (doseq [body (drop 2 expression)] (walk-expr module body locals)))
                  catalog:resolve (do (when-not (and (= 2 (count expression)) (keyword? (second expression)))
                                        (throw (ex-info "Only literal catalog dependencies are supported" {})))
                                      (visit-id (second expression)))
                  (doseq [part expression] (walk-expr module part bound)))
                (coll? expression) (doseq [part expression] (walk-expr module part bound))))
            (visit [key]
              (when (@active key) (throw (ex-info "Cyclic restoration dependency" {:key key})))
              (when-not (@selected key)
                (swap! active conj key)
                (let [{:keys [module rhs source index]} (get definitions key)
                      receipt (first (filter #(and (= index (:form %)) (= source (:source %))) (:receipts (get archives module))))]
                  (when-not (= :admitted (get-in receipt [:receipt :status]))
                    (throw (ex-info "Source lacks matching successful admission" {:key key :index index})))
                  (walk-expr module rhs #{})
                  (swap! selected conj key))
                (swap! active disj key)))]
      (visit-id root-id)
      {:format :lain/pure-source-bundle-v1 :root root-id
       :operators (vec (sort (for [[id key] registrations :when (@selected key)] id)))
       :modules (mapv (fn [[module _]]
                        (let [entries (sort-by :index (for [[key entry] definitions :when (and (= module (:module entry)) (@selected key))] entry))
                              source (apply str (map :source entries))]
                          {:id module :source source :sha256 (sha256 source)
                           :origin-sha256 (sha256 (:source (get archives module)))
                           :forms (mapv #(select-keys % [:index :name]) entries)})) archives)})))

(defn verify-bundle [bundle]
  (when-not (and (= :lain/pure-source-bundle-v1 (:format bundle))
                 (= [:archive/build :archive/compose] (mapv :id (:modules bundle))))
    (throw (ex-info "Expected the two-module pure-source experiment bundle" {})))
  (doseq [{:keys [source sha256] :as module} (:modules bundle)]
    (when-not (= sha256 (lain-harness.source-restore/sha256 source))
      (throw (ex-info "Saved source hash mismatch" {:id (:id module)}))))
  bundle)

(defn save-bundle! [store bundle]
  (verify-bundle bundle)
  (when (d/pull (d/db (:connection store)) [:catalog/source-chunk] [:catalog/id bundle-id])
    (throw (ex-info "Bundle identity already exists; use a fresh experiment database" {})))
  (catalog/archive-admission! store bundle-id (pr-str bundle) {:kind :source-bundle :sha256 (sha256 (pr-str bundle))})
  bundle)

(defn load-bundle [store]
  (let [{:keys [source receipts]} (read-archive store bundle-id)]
    (when-not (= (:sha256 receipts) (sha256 source)) (throw (ex-info "Bundle hash mismatch" {})))
    (verify-bundle (edn/read-string source))))

(defn facilities [store]
  (session/session-extension {:id ::source-restore
                              :primitives {'catalog:restore-bundle (connected/primitive 'catalog:restore-bundle (fn [] (load-bundle store)))}}))

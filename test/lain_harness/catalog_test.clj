(ns lain-harness.catalog-test
  (:require [datomic.client.api :as d]
            [clojure.test :refer [deftest is]]
            [lain-harness.catalog :as catalog]
            [lain-harness.connected-data :as cd]
            [propagators.compiler-2.session :as session]
            [propagators.compiler-2.session.connected :as connected]))

(def rules
  '[[(reachable ?a ?b) [?a :catalog/depends-on ?b]]
    [(reachable ?a ?b) [?a :catalog/depends-on ?mid] (reachable ?mid ?b)]])
(def query
  '[:find ?id :in $ % :where [?root :catalog/id :root]
    (reachable ?root ?cap) [?n :catalog/capability ?cap] [?n :catalog/id ?id]])

(deftest compiled-registration-discovery-and-reuse
  (let [path (str (java.nio.file.Files/createTempDirectory "lain-catalog-test" (make-array java.nio.file.attribute.FileAttribute 0)))
        store (catalog/open! path)
        metadata {:catalog/id :example/v1 :catalog/description "Multiply" :catalog/interface "[x y] -> number"
                  :catalog/assumptions "Numbers" :catalog/status :current :catalog/capability [[:catalog/id :leaf]]}
        _ (catalog/seed! store [{:catalog/id :leaf}
                                {:catalog/id :middle :catalog/depends-on [[:catalog/id :leaf]]}
                                {:catalog/id :root :catalog/depends-on [[:catalog/id :middle]]}])
        initial (session/extend-session (session/create-session {:id (random-uuid) :run-id (random-uuid)})
                                        (catalog/facilities store))
        with-refs (session/extend-session initial (session/session-extension {:id :references :primitives {'cell:reference (cd/reference-operator) 'cell:value (cd/value-operator)}}))
        compiled (session/extend-session with-refs
                                         (session/session-extension
                                          {:id :source :files {"catalog.lain"
                                                               (str "(define multiply (network [x y] (* x y)))\n"
                                                                    "(define registered (catalog:register " (pr-str (pr-str metadata)) " (cell:reference multiply)))\n")}}))]
    ;; Registration enters through the public compiler pass, not low-level graph installation.
    (is (= {:status :registered :id :example/v1} (connected/payload (session/binding-value compiled 'registered))))
    (is (= [[:example/v1]] (catalog/query! store (pr-str query) (pr-str rules))))
    (let [queried (session/extend-session compiled
                                          (session/session-extension
                                           {:id :query :files {"query.lain"
                                                               (str "(define found (catalog:query " (pr-str (pr-str query)) " "
                                                                    (pr-str (pr-str rules)) "))")}}))]
      (is (= [[:example/v1]] (connected/payload (session/binding-value queried 'found)))))
    (catalog/seed! store [{:catalog/id :leaf :catalog/depends-on [[:catalog/id :root]]}])
    (is (= [[:example/v1]] (catalog/query! store (pr-str query) (pr-str rules))))
    (let [reopened (catalog/open! path)]
      (is (= [[:example/v1]] (catalog/query! reopened (pr-str query) (pr-str rules))))
      (is (thrown? Exception (catalog/resolve! reopened :example/v1)))
      (is (thrown? Exception (catalog/register! reopened (pr-str (assoc metadata :catalog/description "Conflict"))
                                                (session/binding-cell compiled 'multiply)))))
    (let [compiled (session/extend-session compiled (session/session-extension
                                                     {:id :reuse :files {"reuse.lain" "(define retained (cell:value (catalog:resolve :example/v1))) (define answer (retained 6 7))"}}))]
      (is (= 42 (connected/payload (session/binding-value compiled 'answer)))))
    (is (= {:status :registered :id :example/v1}
           (catalog/register! store (pr-str metadata) (session/binding-cell compiled 'multiply))))
    (is (= 1 (count (filter #(= :register (:operation %)) @(:journal store)))))
    (is (thrown? Exception (catalog/register! store (pr-str (assoc metadata :catalog/description "Conflict"))
                                              (session/binding-cell compiled 'multiply))))))

(deftest query-contract
  (is (= query (catalog/validate-query query rules)))
  (is (thrown? Exception (catalog/validate-query '[:find ?x :in $ % :where [(clojure.core/slurp "secret") ?x]] [])))
  (is (thrown? Exception (catalog/validate-query '[:find [?x ...] :in $ % :where [?x :catalog/id :root]] [])))
  (is (thrown? Exception (catalog/validate-query '[:find ?x :in $ :where [?x :catalog/id :root]] []))))

(deftest large-source-archive
  (let [path (str (java.nio.file.Files/createTempDirectory "lain-catalog-archive" (make-array java.nio.file.attribute.FileAttribute 0)))
        store (catalog/open! path)
        source (apply str (repeat 3000 "λ你好😀"))
        receipts {:forms (vec (range 5000))}]
    (catalog/archive-admission! store :archive/test source receipts)
    (let [saved (d/pull (d/db (:connection store)) [:catalog/source-chunk :catalog/admission-chunk] [:catalog/id :archive/test])
          restore (fn [chunks] (apply str (map #(subs % 9) (sort chunks))))]
      (is (= source (restore (:catalog/source-chunk saved))))
      (is (= (pr-str receipts) (restore (:catalog/admission-chunk saved)))))))

(deftest documented-seed-examples-compile
  (let [path (str (java.nio.file.Files/createTempDirectory "lain-catalog-examples" (make-array java.nio.file.attribute.FileAttribute 0)))
        store (catalog/open! path)
        _ (catalog/seed! store [{:catalog/id :cap/linear} {:catalog/id :cap/percentage} {:catalog/id :cap/maximum}])
        initial (session/extend-session (session/create-session {:id (random-uuid) :run-id (random-uuid)})
                                        (catalog/facilities store))
        with-refs (session/extend-session initial (session/session-extension
                                                   {:id :references :primitives {'cell:reference (cd/reference-operator)
                                                                                 'cell:value (cd/value-operator)
                                                                                 'seq:of (connected/primitive 'seq:of vector)}}))
        compiled (session/extend-session with-refs (session/session-extension
                                                    {:id :seed :files {"seed.lain" (slurp (str (System/getenv "LAIN_HARNESS_ROOT") "/test/fixtures/catalog/library.lain"))}}))
        checked (session/extend-session compiled (session/session-extension
                                                  {:id :examples :files {"examples.lain"
                                                                         "(define ex-linear ((cell:value (catalog:resolve :operator/linear-v1)) 4 3))
                                                     (define ex-percentage ((cell:value (catalog:resolve :operator/percentage-v1)) 100 0.08))
                                                     (define ex-legacy ((cell:value (catalog:resolve :operator/legacy-v1)) 4))
                                                     (define ex-max ((cell:value (catalog:resolve :operator/max-v1)) 4 9))"}}))]
    (doseq [[name expected] [['ex-linear 12] ['ex-percentage 108] ['ex-legacy 8] ['ex-max 9]]]
      (is (< (abs (- expected (connected/payload (session/binding-value checked name)))) 0.00001)))
    (let [rows (catalog/query! store "[:find ?id ?example :in $ % :where [?op :catalog/id ?id] [?op :catalog/example ?example]]" "[]")]
      (is (= 4 (count rows)))
      (is (every? #(string? (second %)) rows)))))

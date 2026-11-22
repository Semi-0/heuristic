(ns lain-harness.context-store-test
  (:require [clojure.test :refer [deftest is]]
            [lain-harness.catalog :as catalog]
            [lain-harness.context-store :as context]
            [lain-harness.context-runtime :as runtime]
            [lain-harness.connected-data :as cd]
            [lain-harness.context-inspection :as inspection]
            [propagators.compiler-2.session :as s]
            [propagators.compiler-2.session.host :as h]
            [propagators.compiler-2.session.connected :as connected]))

(defn store []
  (context/install!
   (catalog/open! (str (java.nio.file.Files/createTempDirectory
                       "context-test" (make-array java.nio.file.attribute.FileAttribute 0))))))

(defn await-value [owner name]
  (let [deadline (+ (System/currentTimeMillis) 10000)]
    (loop []
      (let [v (connected/payload (s/binding-value (h/current-session owner) name))]
        (cond (map? v) v
              (> (System/currentTimeMillis) deadline) (throw (ex-info "Timed out" {:name name :value v}))
              :else (do (Thread/sleep 10) (recur)))))))

(deftest compiled-evidence-contract
  (let [store (store) owner (h/open! (s/create-session))
        text (apply str (repeat 3000 "λ你好😀\n"))]
    (try
      (h/extend! owner (context/facilities store))
      (h/extend! owner (s/session-extension
                        {:id :archive :files {"archive.lain"
                          (str "(define archived (context:archive :task/input :input " (pr-str text) "))")}}))
      (is (= :archived (:status (await-value owner 'archived))))
      (is (= text (loop [offset 0 result ""]
                    (let [name (symbol (str "chunk-" offset))
                          _ (h/extend! owner (s/session-extension
                               {:id [:read offset] :files {(str "read-" offset ".lain")
                                (str "(define " name " (context:read :task/input " offset " 521))")}}))
                          page (await-value owner name)
                          result (str result (:text page))]
                      (if-let [next (:next page)] (recur next result) result)))))
      (h/extend! owner (s/session-extension
                        {:id :contracts :files {"contracts.lain"
                         (str "(define replay (context:archive :task/input :input " (pr-str text) "))\n"
                              "(define conflict (context:archive :task/input :input \"changed\"))\n"
                              "(define missing (context:resolve :gone))\n"
                              "(define invalid (context:index \"{:catalog/id :claim/bad :catalog/description \\\"bad\\\" :catalog/evidence [[:catalog/id :missing]]}\"))\n"
                              "(define readback (context:read :task/input 0 5))")}}))
      (is (= :archived (:status (await-value owner 'replay))))
      (is (= :error (:status (await-value owner 'conflict))))
      (is (= :error (:status (await-value owner 'missing))))
      (is (= :error (:status (await-value owner 'invalid))))
      (is (= "λ你好😀\n" (:text (await-value owner 'readback))))
      (is (empty? (h/failures owner)))
      (finally (h/close! owner)))))

(deftest versioned-claims-and-pages
  (let [store (store)
        claim {:catalog/id :claim/v1 :catalog/description "Agent claim"
               :catalog/evidence [[:catalog/id :input/v1]]}]
    (context/archive! store :input/v1 :input "exact")
    (is (= :indexed (:status (context/index! store (pr-str claim)))))
    (is (= :indexed (:status (context/index! store (pr-str claim)))))
    (is (= :agent (:catalog/author (context/entity store :claim/v1))))
    (is (= :observation (:catalog/author (context/entity store :input/v1))))
    (is (thrown? Exception (context/index! store (pr-str (assoc claim :catalog/description "changed")))))
    (context/index! store (pr-str (assoc claim :catalog/id :claim/v2 :catalog/supersedes [[:catalog/id :claim/v1]])))
    (doseq [n (range 75)] (context/archive! store (keyword "input" (str n)) :input (str n)))
    (let [query "[:find ?id :in $ % :where [?e :catalog/id ?id]]"
          first-page (context/page! store query "[]" nil 7)
          _ (context/archive! store :input/later :input "later")
          rows (loop [page first-page result []]
                 (let [result (into result (:rows page))]
                   (if-let [cursor (:next page)]
                     (recur (context/page! store query "[]" cursor 7) result) result)))]
      (is (= 78 (count rows) (count (set rows))))
      (is (not (some #{[:input/later]} rows)))
      (is (false? (:absence-established? first-page)))
      (is (thrown? Exception (context/page! store query "[]" nil 33))))))

(deftest effects-wait-for-commit
  (let [store (store)
        compiled (-> (s/create-session)
                     (s/extend-session (context/facilities store))
                     (s/extend-session
                      (s/session-extension
                       {:id :pure :files {"pure.lain"
                         "(define result (context:archive :pure/input :input \"untouched\"))"}})))]
    (is (nil? (:catalog/id (context/entity store :pure/input))))
    (is (some? (s/binding-cell compiled 'result)))))

(deftest compiled-inspection-and-index
  (let [store (store) owner (h/open! (s/create-session))]
    (try
      (h/extend! owner (context/facilities store))
      (h/extend! owner (s/session-extension
                        {:id :refs :primitives {'cell:reference (cd/reference-operator)}}))
      (h/extend! owner (s/session-extension
                        {:id :network :files {"network.lain"
                          "(define offset 3)
                           (define f (network [x] (+ x offset)))
                           (define a (f 4))
                           (define b (+ a 2))
                           (define view (context:inspect (cell:reference f) nil 1))
                           (define saved (context:archive :task/source :source \"source\"))"}}))
      (let [view (await-value owner 'view)]
        (is (= :observed (:status view)))
        (is (= :callable (get-in view [:value :kind])))
        (is (= 1 (get-in view [:value :argument-count])))
        (is (= ["x"] (get-in view [:value :parameters])))
        (is (some? (get-in view [:value :captured-environment])))
        (is (<= (count (:edges view)) 1))
        (is (= 0 (:depth view))))
      (is (= :archived (:status (await-value owner 'saved))))
      (h/extend! owner (s/session-extension
                        {:id :index :files {"index.lain"
                          "(define indexed (context:index \"{:catalog/id :claim/compiled :catalog/description \\\"A claim\\\" :catalog/evidence [[:catalog/id :task/source]]}\"))"}}))
      (is (= :indexed (:status (await-value owner 'indexed))))
      (h/extend! owner (s/session-extension
                        {:id :page :files {"page.lain"
                          "(define page (context:query \"[:find ?id :in $ % :where [?c :catalog/id ?id] [?c :catalog/author :agent]]\" \"[]\" nil 1))"}}))
      (is (= [[:claim/compiled]] (:rows (await-value owner 'page))))
      (let [foreign (s/extend-session (s/create-session)
                                     (s/session-extension {:id :other :files {"other.lain" "(define foreign 4)"}}))
            view (inspection/inspect (s/network (h/current-session owner))
                                     (s/binding-cell foreign 'foreign) nil 1)]
        (is (= :missing-live-reference (:status view))))
      (is (empty? (h/failures owner)))
      (finally (h/close! owner)))))

(deftest compiled-effect-chain
  (let [store (store) owner (h/open! (s/create-session))]
    (try
      (h/extend! owner (context/facilities store))
      (h/extend! owner (s/session-extension
                        {:id :data :primitives
                         {'data:get (connected/primitive 'data:get (fn [k v] (get v k)))}}))
      (h/extend! owner (s/session-extension
                        {:id :chain :files {"chain.lain"
                          "(define archived (context:archive :chain/source :source \"x\"))
                           (define done (if (= (data:get :status archived) :archived)
                             (context:query \"[:find ?id :in $ % :where [?e :catalog/id ?id]]\" \"[]\" nil 1)
                             (context:read :missing 0 1)))"}}))
      (is (= [[:chain/source]] (:rows (await-value owner 'done))))
      (is (empty? (h/failures owner)))
      (finally (h/close! owner)))))

(deftest compiled-measurement-chain
  (let [store (store) owner (h/open! (s/create-session))]
    (try
      (h/extend! owner (runtime/facilities store (constantly 100) (runtime/validator {})))
      (h/extend! owner (s/session-extension
                        {:id :data :primitives
                         {'data:get (connected/primitive 'data:get (fn [k v] (get v k)))}}))
      (h/extend! owner (s/session-extension
                        {:id :chain :files {"chain.lain"
                          "(define archived (context:archive-value :measure/source :source \"x\"))
                           (define done (context:tokens (if (= (data:get :status archived) :archived) \"probe\" \"failed\")))"}}))
      (is (= 100 (:tokens (await-value owner 'done))))
      (is (empty? (h/failures owner)))
      (finally (h/close! owner)))))

(deftest compiled-pagination-and-stale-resolution
  (let [store (store) owner (h/open! (s/create-session))
        foreign (s/extend-session (s/create-session)
                   (s/session-extension {:id :old :files {"old.lain" "(define f (network [x] (+ x 1)))"}}))]
    (try
      (doseq [n (range 10)] (context/archive! store (keyword "page" (str n)) :source (str n)))
      ;; Represents a catalog reference left behind by an earlier live session.
      (catalog/register! store "{:catalog/id :stale/f :catalog/description \"Old callable\" :catalog/interface \"[x]\" :catalog/assumptions \"old session\"}"
                         (s/binding-cell foreign 'f))
      (h/extend! owner (context/facilities store))
      (h/extend! owner (s/session-extension {:id :fields :primitives
                           {'data:get (connected/primitive 'data:get (fn [k v] (get v k)))}}))
      (h/extend! owner (s/session-extension
                        {:id :pages :files {"pages.lain"
                         (str "(define stale (context:resolve :stale/f))\n"
                              (apply str (for [i (range 4)]
                                (str "(define page" i " (context:query \"[:find ?id :in $ % :where [?e :catalog/id ?id]]\" \"[]\" "
                                     (if (zero? i) "nil" (str "(data:get :next page" (dec i) ")")) " 3))\n"))))}}))
      (let [pages (mapv #(await-value owner (symbol (str "page" %))) (range 4))
            rows (mapcat :rows pages)]
        (is (= 11 (count rows) (count (set rows)))))
      (is (= :missing-live-reference (:status (await-value owner 'stale))))
      (is (empty? (h/failures owner)))
      (finally (h/close! owner)))))

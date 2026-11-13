(ns lain-harness.catalog-selection-test
  (:require [clojure.test :refer [deftest is]]
            [datomic.client.api :as d]
            [lain-harness.catalog-selection :as selection]
            [lain-harness.source-restore :as restore]
            [lain-harness.source-restore-test :as fixtures]))

(deftest selected-root-controls-source-without-creating-live-references
  (let [store {:connection ::connection :references (atom {}) :journal (atom [])}
        archives (fixtures/archives fixtures/saved-composition)]
    (with-redefs [d/db identity
                  d/pull (fn [_ _ [_ id]] (when (get archives id) {:catalog/source-chunk ["present"]}))
                  restore/read-archive (fn [_ id] (get archives id))]
      (let [small (selection/selected-bundle store :mul/v1)
            whole (selection/selected-bundle store :composed/v1)]
        (is (= [:mul/v1] (:operators small)))
        (is (= "" (get-in small [:modules 1 :source])))
        (is (= "" (get-in whole [:modules 2 :source])))
        (is (= #{:mul/v1 :composed/v1} (set (:operators whole))))
        (is (empty? @(:references store)))
        (is (= [:mul/v1 :composed/v1] (mapv :id @(:journal store))))
        (is (thrown? Exception (selection/selected-bundle store :missing)))))))

(deftest third-generation-retains-transitive-source-and-excludes-old-answers
  (let [third (fixtures/admitted "(define old (cell:value (catalog:resolve :composed/v1)))
                                  (define delivered (network [x y fee] (+ (old x y) fee)))
                                  (define receipt (catalog:register \"{:catalog/id :delivered/v1}\" (cell:reference delivered)))
                                  (-> (delivered 2 3 4) answer)")
        bundle (restore/build-bundle (assoc (fixtures/archives fixtures/saved-composition) :archive/accumulated third) :delivered/v1)]
    (is (= #{:mul/v1 :composed/v1 :delivered/v1} (set (:operators bundle))))
    (is (= [0 1 2] (mapv :index (get-in bundle [:modules 2 :forms]))))
    (is (not (.contains (get-in bundle [:modules 2 :source]) "answer")))))

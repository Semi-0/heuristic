(ns lain-harness.source-restore-test
  (:require [clojure.test :refer [deftest is]]
            [lain-harness.source-restore :as restore]))

(defn admitted [source]
  {:source source :receipts (mapv (fn [entry] {:form (:index entry) :source (:source entry) :receipt {:status :admitted}})
                                  (restore/source-forms source))})

(def saved-library
  "(define mul (network [x y] (* x y)))
   (define registered (catalog:register \"{:catalog/id :mul/v1}\" (cell:reference mul)))
   (-> (mul 2 3) old-user-output)")

(def saved-composition
  "(define ref (catalog:resolve :mul/v1))
   (define f (cell:value ref))
   (define composed (network [x y] (let [product (f x y)] (+ product 1))))
   (define registered (catalog:register \"{:catalog/id :composed/v1}\" (cell:reference composed)))
   (define old-result (composed 2 3))
   (-> old-result old-user-output)")

(defn archives [composition]
  (array-map :archive/build (admitted saved-library) :archive/compose (admitted composition)))

(deftest selects-exact-admitted-dependencies-without-old-execution
  (let [bundle (restore/build-bundle (archives saved-composition) :composed/v1)
        [library composed] (:modules bundle)]
    (is (= [:mul/v1 :composed/v1] (sort-by #(if (= % :mul/v1) 0 1) (:operators bundle))))
    (is (= [0 1] (mapv :index (:forms library))))
    (is (= [0 1 2 3] (mapv :index (:forms composed))))
    (is (= (apply str (map :source (take 4 (restore/source-forms saved-composition)))) (:source composed)))
    (is (= bundle (restore/verify-bundle bundle)))
    (is (thrown? Exception (restore/verify-bundle (assoc-in bundle [:modules 0 :source] "changed"))))))

(deftest refuses-missing-captures-effects-and-unadmitted-source
  (is (thrown? Exception (restore/build-bundle (archives "(define f (network [x] (+ x missing))) (define r (catalog:register \"{:catalog/id :root}\" (cell:reference f)))") :root)))
  (is (thrown? Exception (restore/build-bundle (archives "(define f (network [x] (shell x))) (define r (catalog:register \"{:catalog/id :root}\" (cell:reference f)))") :root)))
  (is (thrown? Exception (restore/build-bundle (assoc-in (archives saved-composition) [:archive/build :receipts] []) :composed/v1)))
  (is (thrown? Exception (restore/build-bundle (archives saved-composition) :absent))))

(load-file "tools/printshop_diagnostics.clj")
(let [base {:sources [{:action "round-1" :source "(define check-1 (and (= x 1) true)) (-> check-1 self-check-1)"}]
            :facts [{:name "and" :state :unknown :cell :and-ref} {:name "future" :state :unknown :cell :future-ref}]
            :outputs [{:name "self-check-1" :cell :check-ref :value :unknown :inputs #{:and-ref} :support #{}}]}
      ds (diagnostics/collect-diagnostics base)]
  (assert (= [:unresolved-operator-binding :blocked-output] (mapv :kind ds)))
  (assert (= "round-1" (get-in ds [0 :source-references 0 :action])))
  (assert (= #{:and-ref} (:inputs (second ds))))
  (assert (empty? (diagnostics/collect-diagnostics {:facts (:facts base)})))
  (assert (= :runtime-contradiction (:kind (first (diagnostics/collect-diagnostics {:outputs [{:name "out" :value :contradictory}]})))))
  (assert (= :admission-error (:kind (first (diagnostics/collect-diagnostics {:admission {:status :rejected :response "round-1"}})))))
  (assert (empty? (diagnostics/collect-diagnostics (assoc base :facts [] :outputs [{:name "self-check-1" :value true}])))))
(println "Diagnostic controls passed: unresolved binding, benign unknown, blocked output, contradiction, admission rejection, source attribution and cleared state.")

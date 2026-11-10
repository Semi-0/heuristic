(ns tools.verify-printshop-acceptance
  "Assert completed numerical, reuse, conversation and phase evidence. No model calls."
  (:require [clojure.edn :as edn]))

(let [root (first *command-line-args*)
      read-record #(edn/read-string {:default tagged-literal} (slurp (str root "/" %)))
      evaluation (read-record "evaluation.edn")
      evidence (read-record "evidence.edn")
      integrity (:integrity (read-record "integrity.edn"))
      history (:checks (read-record "history-verification.edn"))]
  (assert (= :completed (:operational-status evaluation)) "Experiment did not complete")
  (assert (= [1 2 3 4 5] (mapv :round (:rounds evidence))) "Missing completed questions")
  (assert (and (seq (:checks evaluation)) (every? :pass (:checks evaluation))) "Domain checks failed")
  (assert (= #{2 3 4 5} (set (keys (:reuse evaluation)))) "Missing reuse observations")
  (assert (every? seq (vals (:reuse evaluation))) "A later question did not reuse a retained callable")
  (assert (and (seq integrity) (every? true? (vals integrity))) "Phase/source integrity failed")
  (assert (and (seq history) (every? true? (vals history))) "Conversation or instance stability failed")
  (assert (true? (get-in evidence [:outcome :stable-identity])) "Session identity changed")
  (let [result {:operational-status :completed
                :controlled (:controlled evidence)
                :provider-requests (:provider-requests evaluation)
                :transport-requests (count (:requests evidence))
                :domain-checks (count (:checks evaluation))
                :history-checks (count history) :integrity-checks (count integrity)
                :reuse (into {} (map (fn [[i xs]] [i (mapv :name xs)]) (:reuse evaluation)))
                :stable-identity true :delivered-messages (count (:history evidence))
                :repair-attempts (count (filter #(pos? (:correction %)) (:recovery-attempts evidence)))
                :truncated-responses (count (filter :truncated? (:phase-responses evidence)))}]
    (spit (str root "/acceptance.edn") (pr-str result))
    (prn result)))

(ns lain-harness.model-test
  (:require [clojure.test :refer [deftest is]]
            [lain-harness.module-support :as module]
            [lain-harness.runtime :as h]
            [propagators.compiler-2.session.host :as host]))

(deftest model-communicates-for-three-user-turns
  (let [requests (atom [])
        services (module/open-model!
                  (fn [request emit!]
                    (swap! requests conj (dissoc request :cancelled?))
                    (emit! (str "(message-to-user \"reply " (:id request) "\")"))))
        owner (:owner services)]
    (try
      (module/await! :context services #(string? (:context (h/model-state services "model"))))
      (is (empty? @requests) "A known context alone does not infer")
      (doseq [turn (range 1 4)]
        (let [id (str "user-" turn)]
          (h/publish-user! services "user" id (str "turn " turn))
          (module/await! [:settled turn] services
                         #(and (= turn (count @requests)) (module/settled? services)))
          (is (= turn (:serial (h/model-state services "model"))))
          (is (= 1 (get-in (h/model-state services "model") [:round :number])))
          (is (= id (get-in (h/model-state services "model") [:round :turn])))
          ;; Delivery is an injected boundary acknowledgement in this model test.
          ;; The user-IO module verifies the actual delivery capability separately.
          (h/publish-event! owner [:messages "model"] {:id (str "delivered-" turn) :payload (str "reply " turn)})
          (module/await! [:history turn] services
                         #(= turn (count (h/list-values (:deliveries (h/model-state services "model"))))))
          (h/publish-user! services "user" id (str "turn " turn))))
      (is (= [1 2 3] (mapv :id @requests)))
      (is (= [:chunk :complete :chunk :complete :chunk :complete] (mapv :kind (module/events services))))
      (is (some #{{:role "assistant" :content "reply 1"}} (:messages (second @requests))))
      (is (some #{{:role "assistant" :content "reply 2"}} (:messages (nth @requests 2))))
      (is (= 3 (count @requests)) "Delivery and duplicate ingress cause no extra inference")
      (finally
        (module/write-evidence! "model" {:requests @requests :state (h/model-state services "model")
                                         :events (module/events services)})
        (host/close! owner)))))

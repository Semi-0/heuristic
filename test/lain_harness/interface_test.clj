(ns lain-harness.interface-test
  (:require [clojure.test :refer [deftest is]]
            [graph.lain-editor.runtime.session :as editor]
            [lain-harness.runtime :as harness]
            [propagators.compiler-2.session :as session]
            [propagators.compiler-2.session.host :as host]))

(deftest effectful-capabilities-connect-through-lain-struct-slots
  (let [owner (host/open! (session/create-session))
        services (harness/services owner)
        requests (atom [])]
    (try
      (harness/install-facilities! services)
      (host/extend! owner (harness/specification-extension services
                                                           (str (System/getenv "LAIN_HARNESS_ROOT") "/soul-refined.lain")))
      (host/extend! owner (harness/transport-extension services
                                                       (fn [request emit!]
                                                         (swap! requests conj (:id request))
                                                         (emit! "(message-to-user \"generated\")"))))
      (host/extend! owner (harness/message-extension services))
      (host/extend-source! owner
                           "(define events (source:new (data:list)))
         (define deliveries (source:new (data:list)))
         (session:export :source \"probe\" :source events)
         (session:export :messages \"probe\" :messages deliveries)
         (define capabilities (struct :request transport:stream :deliver message:deliver))
         (define requested (((slot :request) capabilities) (data:record :id 7 :messages (data:list)) events))
         (define sent (((slot :deliver) capabilities) \"explicit reply\" deliveries))")
      (let [values #(host/interface-values owner)
            events #(harness/list-values (get-in (values) [[:source "probe"] :value]))
            delivered #(harness/list-values (get-in (values) [[:messages "probe"] :value]))]
        (loop [remaining 150]
          (when (and (pos? remaining) (or (< (count (events)) 2) (empty? (delivered))))
            (host/step! owner)
            (Thread/sleep 20)
            (recur (dec remaining))))
        (is (= [7] @requests))
        (is (= [:chunk :complete] (mapv :kind (events))))
        (is (= [[7 1] [7 2]] (mapv :id (events))))
        (is (= ["explicit reply"] (mapv :payload (delivered))))
        (host/step! owner)
        (is (= 1 (count (delivered))))
        (is (empty? (host/failures owner))))
      (finally (host/close! owner)))))

(deftest shared-user-ingress-keeps-external-ownership
  (let [owner (host/open! (session/create-session))
        services (harness/services owner)
        views (atom {})
        owners (editor/owner-catalog views)]
    (try
      (harness/install-facilities! services)
      (host/extend! owner (harness/specification-extension services
                                                           (str (System/getenv "LAIN_HARNESS_ROOT") "/soul-refined.lain")))
      (host/extend-source! owner "(define user-source (integration:user \"user\"))")
      (let [entry (editor/register-compiler-session! owners owner "")
            run-id (:run-id (session/session-identity (host/current-session owner)))
            command (assoc entry :inputId "user" :messageId "first" :text "hello" :runId (str run-id))]
        (harness/publish-user! services "user" "first" "hello")
        (editor/reopen! views owners (assoc entry :pane "a"))
        (editor/reopen! views owners (assoc entry :pane "b"))
        (is (= :accepted (editor/publish-user-message! views command)))
        (is (= [{:id "first" :kind :user :payload "hello"}]
               (harness/list-values (get-in (host/interface-values owner) [[:user "user"] :value]))))
        (is (thrown? Exception (editor/publish-user-message! views (assoc command :text "conflict"))))
        (is (thrown? Exception (editor/publish-user-message! views (assoc command :generation "stale"))))
        (is (thrown? Exception (editor/publish-user-message! views (assoc command :runId "stale"))))
        (is (thrown? Exception (editor/publish-user-message! views (assoc command :inputId "not-exported"))))
        (is (thrown? Exception (editor/extend! views (assoc entry :source "(define illegal 1)"))))
        (editor/detach! views owners (assoc entry :pane "a"))
        (editor/remove! views "b")
        (is (= :active (host/lifecycle owner)))
        (is (= 1 (count (:sessions (editor/list-detached owners))))))
      (host/close! owner)
      (is (thrown? Exception (harness/publish-user! services "user" "late" "late")))
      (finally (when (= :active (host/lifecycle owner)) (host/close! owner))))))

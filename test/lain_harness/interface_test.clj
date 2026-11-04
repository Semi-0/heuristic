(ns lain-harness.interface-test
  (:require [clojure.test :refer [deftest is]]
            [graph.lain-editor.runtime.session :as editor]
            [lain-harness.runtime :as harness]
            [propagators.compiler-2.session :as session]
            [propagators.compiler-2.session.host :as host]))

(deftest shared-user-ingress-keeps-external-ownership
  (let [owner (host/open! (session/create-session))
        services (harness/services owner)
        views (atom {})
        owners (editor/owner-catalog views)]
    (try
      (host/extend! owner (harness/user-extension "user"))
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

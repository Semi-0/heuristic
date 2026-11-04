(ns lain-harness.source-reader
  "Isolated source-reading primitive: one parse result, never admission or IO."
  (:require [lain-harness.stream :as stream]
            [propagators.compiler-2.session :as session]

            [propagators.compiler-2.session.connected :as connected]))

(defn read-next [text finish?]
  (let [result (stream/advance-form (assoc (stream/framer) :buffer text) finish?)]
    {:status (:status result) :source (or (:source result) "")
     :remaining (get-in result [:state :buffer])
     :message (or (:message result) "")}))

(defn primitive-bindings []
  [['source:read-next (connected/primitive 'source:read-next read-next)]])

(defn extension []
  (session/session-extension
   {:id ::source-reader
    :imports {["lain_harness/source_reader.clj" ::primitive-bindings 1] [::source-reader 1]}
    :primitives (primitive-bindings)}))

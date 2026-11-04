(ns lain-harness.stream
  "Immutable incremental parser state. Admission and buffering are separate."
  (:require [lain-harness.source-parser :as parser]))

(def read-next parser/read-next)
(defn framer [] {:buffer "" :index 0 :version 0 :closed? false})

(defn receive
  "Buffer a transport publication without admitting or emitting anything."
  [state chunk]
  (when (:closed? state) (throw (ex-info "Code stream is closed" {})))
  (-> state (update :buffer str chunk) (update :version inc)))

(defn advance-form
  "Return one parse result and explicit next state. Call again only after the
  connected admission receipt succeeds; a malformed suffix cannot erase a prefix."
  [state finish?]
  (when (:closed? state) (throw (ex-info "Code stream is closed" {})))
  (try
    (if-let [result (read-next (:buffer state) finish?)]
      (if (:empty? result)
        {:status (if finish? :complete :waiting)
         :state (cond-> (assoc state :buffer "") finish? (assoc :closed? true))}
        {:status :form :form (:form result) :source (:source result) :index (:index state)
         :state (-> state (update :buffer subs (:end result))
                    (update :index inc) (update :version inc))})
      {:status :waiting :state state})
    (catch Exception error
      {:status :invalid :message (ex-message error)
       :state (-> state (assoc :closed? true) (update :version inc))})))

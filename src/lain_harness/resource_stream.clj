(ns lain-harness.resource-stream
  "External streaming resource compatibility adapter."
  (:require [lain-harness.stream :as stream]))
(defn framer [] (atom (stream/framer)))
(defn feed!
  "Emit each complete form synchronously; a failed admission stops parsing."
  [state chunk finish? emit!]
  (locking state
    (when (:closed? @state) (throw (ex-info "Code stream is closed" {})))
    (swap! state update :buffer str chunk)
    (loop []
      (when-let [next (stream/read-next (:buffer @state) finish?)]
        (if (:empty? next)
          (swap! state assoc :buffer "")
          (do
            (emit! (assoc (select-keys next [:source :form]) :index (:index @state)))
            (swap! state #(-> % (update :buffer subs (:end next)) (update :index inc)))
            (recur)))))
    (when finish? (swap! state assoc :closed? true))))

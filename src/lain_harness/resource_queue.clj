(ns lain-harness.resource-queue
  "Compatibility adapter for external resource queues. All transitions are pure."
  (:require [lain-harness.queue :as queue]))

(defrecord Queue [state capacity])
(defn queue
  ([] (queue 256))
  ([capacity] (->Queue (atom (queue/queue capacity)) capacity)))
(defn append! [resource event]
  (locking (:state resource)
    (let [before @(:state resource)
          after (queue/append before event)]
      (reset! (:state resource) after)
      (if (= before after) :duplicate :accepted))))
(defn pending [resource] (queue/pending @(:state resource)))
(defn history [resource] (queue/history @(:state resource)))
(defn acknowledge! [resource ids]
  (swap! (:state resource) queue/acknowledge ids))
(defn close! [resource] (swap! (:state resource) queue/close))

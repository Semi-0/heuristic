(ns lain-harness.queue
  "Independent bounded FIFO event logs. Acknowledgement never erases evidence.")
(defrecord Queue [state capacity])
(defn queue ([] (queue 256)) ([capacity]
  (when-not (pos-int? capacity) (throw (ex-info "Positive queue capacity required" {})))
  (->Queue (atom {:events [] :ids {} :cursor 0 :closed? false}) capacity)))
(defn append! [q event]
  (when-not (and (map? event) (some? (:id event)))
    (throw (ex-info "Queue event requires an identity" {})))
  (locking (:state q)
    (let [{:keys [events ids cursor closed?]} @(:state q)]
      (cond
        closed? (throw (ex-info "Queue is closed" {:id (:id event)}))
        (contains? ids (:id event))
        (if (= event (nth events (ids (:id event)))) :duplicate
          (throw (ex-info "Conflicting event identity" {:id (:id event)})))
        (>= (- (count events) cursor) (:capacity q))
        (throw (ex-info "Queue capacity exceeded" {:capacity (:capacity q)}))
        :else (do (swap! (:state q) #(-> % (update :events conj event)
                                         (assoc-in [:ids (:id event)] (count events)))) :accepted)))))
(defn pending [q] (let [{:keys [events cursor]} @(:state q)] (subvec events cursor)))
(defn history [q] (:events @(:state q)))
(defn acknowledge! [q ids]
  (locking (:state q)
    (let [n (count ids) pending (pending q)]
      (when-not (= (vec ids) (mapv :id (take n pending)))
        (throw (ex-info "Acknowledgement must match FIFO prefix" {:ids ids})))
      (swap! (:state q) update :cursor + n))))
(defn close! [q] (swap! (:state q) assoc :closed? true))

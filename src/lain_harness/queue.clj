(ns lain-harness.queue
  "Immutable bounded FIFO publications. Versions and acknowledgements are data.")

(defn queue
  ([] (queue 256))
  ([capacity]
   (when-not (pos-int? capacity)
     (throw (ex-info "Positive queue capacity required" {})))
   {:events [] :ids {} :cursor 0 :capacity capacity :version 0 :closed? false}))

(defn pending [{:keys [events cursor]}] (subvec events cursor))
(defn history [state] (:events state))

(defn append [state event]
  (when-not (and (map? event) (some? (:id event)))
    (throw (ex-info "Queue event requires an identity" {})))
  (let [{:keys [events ids cursor capacity closed?]} state]
    (cond
      (contains? ids (:id event))
      (if (= event (nth events (ids (:id event)))) state
          (throw (ex-info "Conflicting event identity" {:id (:id event)})))
      closed? (throw (ex-info "Queue is closed" {:id (:id event)}))
      (>= (- (count events) cursor) capacity)
      (throw (ex-info "Queue capacity exceeded" {:capacity capacity})))
    (if (contains? ids (:id event)) state
        (-> state
            (update :events conj event)
            (assoc-in [:ids (:id event)] (count events))
            (update :version inc)))))

(defn acknowledge [state ids]
  (let [ids (vec ids) n (count ids)]
    (when-not (= ids (mapv :id (take n (pending state))))
      (throw (ex-info "Acknowledgement must match FIFO prefix" {:ids ids})))
    (cond-> state (pos? n) (update :cursor + n) (pos? n) (update :version inc))))

(defn close [state]
  (if (:closed? state) state (-> state (assoc :closed? true) (update :version inc))))

(defn feedback
  "Compare an explicit prior version before accepting a transition. Replays of
  an already committed next value are harmless; stale conflicting writes fail."
  [current expected next-state]
  (cond
    (= current next-state) current
    (not= expected (:version current))
    (throw (ex-info "Stale queue feedback" {:expected expected :actual (:version current)}))
    (not= (inc expected) (:version next-state))
    (throw (ex-info "Feedback must advance exactly one version" {}))
    :else next-state))

(load-file "/home/lain/projects/lain-workspace/tools/offline_uuid.clj")
(require '[lain-harness.runtime :as h] '[propagators.compiler-2.session.host :as host])
(let [requests (atom [])
      svc (h/prepare! {:source-path "/home/lain/projects/lain-workspace/soul-refined.lain"
                       :transport (fn [request emit!] (swap! requests conj request) (emit! "(message-to-user \"Hello\")"))
                       :propagation-options {:max-steps 100000 :timeout-ms 30000}})
      owner (:owner svc)]
 (try
  (println :prepared (select-keys (h/model-state svc "model") [:version :active :context]))
  (h/publish-user! svc "user" "first" "hello")
  (loop [attempt 0]
   (let [messages (get-in (host/interface-values owner) [[:messages "model"] :value])]
    (when (and (< attempt 200) (empty? (h/list-values messages)) (empty? (host/failures owner)))
     (Thread/sleep 100) (recur (inc attempt)))))
  (println :requests (count @requests))
  (println :interfaces (host/interface-values owner))
  (println :failures (mapv #(select-keys (Throwable->map %) [:cause :data]) (host/failures owner)))
  (finally (host/close! owner))))
(shutdown-agents)

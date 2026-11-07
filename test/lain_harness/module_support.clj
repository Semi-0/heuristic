(ns lain-harness.module-support
  (:require [clojure.java.io :as io]
            [lain-harness.runtime :as h]
            [propagators.compiler-2.session :as session]
            [propagators.compiler-2.session.host :as host]))

(def root (System/getenv "LAIN_HARNESS_ROOT"))

(defn await! [label services predicate]
  (let [deadline (+ (System/currentTimeMillis) 120000)]
    (loop []
      (cond
        (seq (host/failures (:owner services)))
        (throw (ex-info "Module host failed" {:phase :harness :check label}
                        (first (host/failures (:owner services)))))
        (predicate) true
        (> (System/currentTimeMillis) deadline)
        (throw (ex-info "Module condition timed out"
                        {:phase :harness :check label :state (h/model-state services "model")}))
        :else (do (Thread/sleep 20) (recur))))))

(defn open-model! [transport]
  (let [owner (host/open! (session/create-session
                           {:id (random-uuid) :run-id (random-uuid)
                            :propagation-options {:max-steps 100000 :timeout-ms 60000}}))
        services (h/services owner)]
    (try
      (h/install-facilities! services)
      ;; This suite exercises the legacy model module, not the environment-only
      ;; model-IO bootstrap now owned by soul-refined.lain.
      (let [parts (session/extension-parts
                   (h/specification-extension services (str root "/soul-refined.lain")))]
        (host/extend! owner
                      (session/session-extension
                       (assoc parts :id ::model-module
                              :files (dissoc (:files parts) "soul-refined.lain")))))
      (host/extend! owner (h/transport-extension services transport))
      (host/extend-source! owner
                           (str (slurp (str root "/test/fixtures/model-consumer.lain"))
                                "(define module-user (integration:user \"user\"))
              (define module-model (integration:model \"model\" module-user (struct :request transport:stream)))
              (-> \"Controlled module context; no environment description or compiler attached.\" (inputs-of module-model))
              (test:consume-model-output module-model)"))
      services
      (catch Throwable error (host/close! owner) (throw error)))))

(defn events [services]
  (h/list-values (get-in (host/interface-values (:owner services)) [[:source "model"] :value])))

(defn settled? [services]
  (let [state (h/model-state services "model")]
    (and (= :idle (:active state)) (zero? (get-in state [:inputs :count]))
         (zero? (get-in state [:outputs :count])))))

(defn write-evidence! [name evidence]
  (when-let [directory (System/getenv "LAIN_HARNESS_EVIDENCE_DIR")]
    (let [file (io/file directory (str name ".edn"))]
      (io/make-parents file)
      (spit file (pr-str evidence)))))

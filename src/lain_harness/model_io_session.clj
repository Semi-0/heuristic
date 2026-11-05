(ns lain-harness.model-io-session
  "Local assembly for the model IO experiment; no editor deployment or global rebinding."
  (:require [clojure.string :as str]
            [graph.lain-editor.runtime.display :as display]
            [propagators.compiler-2.session :as session]
            [propagators.compiler-2.session.files :as files]
            [propagators.compiler-2.session.host :as host]
            [propagators.compiler-2.runtime.operators.network-history :as history]
            [propagators.experimental.visualization.extension :as visualization]
            [propagators.semantic-trace.observation :as observation]
            [propagators.semantic-trace.retention :as retention]))

(def runtime-libraries
  ["propagators/compiler_2/runtime/lain/combinator.lain"
   "propagators/compiler_2/runtime/lain/tracers.lain"])

(defn declaration [root source options]
  ;; The runtime and project have different permitted import roots. Snapshot
  ;; them separately, then retain the original source and explicit import edge
  ;; in the joined declaration. That edge is essential when a session reruns.
  (let [runtime (session/extension-parts
                 (files/prepare-extension!
                  {:id :model-runtime :read-files runtime-libraries}))
        model-source (slurp (str root "/model-refined.lain"))
        project-source (reduce #(str/replace %1 (str "(load-lain " (pr-str %2) ")") "")
                               model-source runtime-libraries)
        project (session/extension-parts
                 (files/prepare-extension!
                  {:id :model-io :root root :read-files [source]
                   :files {"model-refined.lain" project-source}
                   :factory-arguments {:lain-harness.model-io/primitive-bindings [options]}}))]
    (session/session-extension
     {:id :model-io
      :files (assoc (merge (:files runtime) (:files project)) "model-refined.lain" model-source)
      :primitives (merge (:primitives runtime) (:primitives project))
      :imports (merge (:imports runtime) (:imports project))
      :evaluation (:evaluation project)})))

(defn compiler []
  (session/create-session {:id (random-uuid) :run-id (random-uuid)
                           :native-extensions [retention/extension visualization/extension
                                               display/extension history/extension observation/extension]
                           :propagation-options {:max-steps 100000 :timeout-ms 30000}}))

(defn open! [root source options]
  (let [owner (host/open! (compiler))]
    (try
      (host/extend! owner (declaration root source options))
      owner
      (catch Throwable error (host/close! owner) (throw error)))))

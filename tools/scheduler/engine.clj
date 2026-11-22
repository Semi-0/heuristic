(ns scheduler.engine
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [propagators.compiler-2.session :as session]
            [propagators.compiler-2.session.operators :as operators]
            [propagators.experimental.visualization.data :as data]
            [propagators.stdlib.source-publication :as publication]))

(def extension
  (session/session-extension
   {:id :experiment-scheduler/v1
    :primitives {'field (operators/prim-op
                         (fn [make]
                           (make (fn [record key]
                                   (let [v (get record (keyword key) ::missing)]
                                     (when-not (boolean? v)
                                       (throw (ex-info "Expected boolean observation" {:key key})))
                                     v)))))}
    :files {"scheduler.lain" (slurp (first *command-line-args*))}}))

(defn opened [] (session/extend-session (session/create-session) extension))
(let [output *out*]
  (binding [*out* *err*]
    (with-open [input (io/reader System/in)]
      (loop [s (opened) revision 0 lines (line-seq input)]
        (when-let [line (first lines)]
          (let [n (inc revision)
                ;; Keep only a bounded set of telemetry revisions, no effect replay.
                s (if (zero? (mod n 32)) (opened) s)
                request (json/read-str line :key-fn keyword)
                turn (session/advance s [(session/cell-input
                                          (session/binding-cell s 'observation)
                                          (publication/active :scheduler/observation n request))])
                next-session (:session turn)
                action (data/payload (session/binding-value next-session 'action))]
            (when (or (seq (:requests turn)) (seq (:next-inputs turn)))
              (throw (ex-info "Unexpected effect or deferred input" {})))
            (when-not (contains? #{"wait" "launch" "wake" "done"} action)
              (throw (ex-info "Unsettled policy" {:action action})))
            (binding [*out* output]
              (println (json/write-str {:action action}))
              (flush))
            (recur next-session n (next lines))))))))

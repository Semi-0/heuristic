(ns lain-harness.resource-environment
  "External owner adapter; pure lexical metadata is in environment."
  (:require [lain-harness.environment :as environment]
            [propagators.compiler-2.session :as session]
            [propagators.compiler-2.session.host :as host]))
(def bindings environment/bindings)
(def snapshot environment/snapshot)
(defn ensure! [owner e]
  (let [known (:frames (session/session-declarations (host/current-session owner)))
        frames (apply dissoc (:frames e) (keys known))]
    (when (seq frames)
      (let [receipt @(host/submit-extension! owner
                                             (session/session-extension {:id [:frames (:id e)] :frames frames}))]
        (when-not (= :admitted (:status receipt)) (throw (ex-info "Frame admission failed" receipt)))))
    (:id e)))

(defn observe!
  "Observe committed lexical data; output is generic binding-change evidence."
  [owner e emit!]
  (let [target (ensure! owner e) previous (atom {}) sequence (atom 0)]
    (host/observe! owner [:describe target (java.util.UUID/randomUUID)]
                   (fn [s]
                     (let [current (snapshot s target)
                           changes (vec (remove (fn [[name item]] (= item (get @previous name))) current))]
                       (reset! previous current)
                       (doseq [[_ item] changes]
                         (emit! (assoc item :id [target (swap! sequence inc)] :kind :binding-change :environment target))))))))

(ns lain-harness.admission
  "Pure source admission declarations. Session advancement owns the transaction."
  (:require [propagators.compiler-2.compiler.basis :as basis]
            [propagators.compiler-2.session :as session]
            [propagators.compiler-2.session.connected :as connected]
            [propagators.compiler-2.session.operators :as ops]))

(defn declaration [id source target support-cell]
  {:id [:lain-admission id] :target (:id target) :frames (:frames target)
   :support-cell support-cell
   :files {(str "admission/" (basis/stable-node-id :admission id) ".lain") source}})

(defn primitive-bindings []
  [['admission:declaration (ops/prim-op (fn [p] (p (comp connected/frozen declaration))))]])

(defn extension []
  (session/session-extension
   {:id ::declaration
    :imports {["lain_harness/admission.clj" ::primitive-bindings 1] [::declaration 1]}
    :primitives (primitive-bindings)}))

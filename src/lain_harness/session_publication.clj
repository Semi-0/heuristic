(ns lain-harness.session-publication
  "Declare versioned source producers through the public compiler session API."
  (:require [propagators.compiler-2.session :as session]
            [propagators.compiler-2.session.operators :as op]
            [propagators.datastructures.support-collection :as collection]))

(defn declaration [compiler binding epoch status base]
  (when-not (and (symbol? binding) (pos-int? epoch) (#{:active :retracted} status))
    (throw (ex-info "Expected a binding, positive revision and premise status"
                    {:binding binding :epoch epoch :status status})))
  (let [reference (session/binding-cell compiler binding)
        producer (symbol (str "publication:" (:uuid reference) "-" epoch))
        content (collection/content
                 {:base base :support #{{:source reference :timestamp epoch :premises-status status}}})]
    (session/session-extension
     {:id [::revision reference epoch]
      :primitives {producer (op/frozen-primitive
                             [::revision reference epoch status content]
                             (op/prim-op (fn [make] (make (fn [] content)))))}
      :files {(str producer ".lain") (str "(-> (" producer ") " binding ")")}})))

(defn publish [compiler binding epoch status base]
  (session/extend-session compiler (declaration compiler binding epoch status base)))

(ns tools.probe-reconverging-branch)

(load-file (str (System/getenv "LAIN_HARNESS_ROOT") "/tools/offline_uuid.clj"))
(require '[clojure.java.io :as io]
         '[lain-harness.session-publication :as publication]
         '[propagators.cells.value :as value]
         '[propagators.compiler-2.session :as session]
         '[propagators.datastructures.layered-value :as datum]
         '[propagators.debug :as debug]
         '[propagators.experimental.amb.observation :as observation]
         '[propagators.experimental.ttms-primitives :as primitives]
         '[propagators.stdlib.premise-state :as state])

(let [directory (first *command-line-args*)
      _ (when-not directory (throw (ex-info "Supply an evidence directory" {})))
      _ (.mkdirs (io/file directory))
      code "(define subtotal) (define fee) (define result (* (+ subtotal (select (>= subtotal 250) 0 fee)) 0.08M))"]
  (with-open [writer (io/writer (io/file directory "propagation.log"))]
    (let [profiled (fn [operation]
                     (let [profile (debug/activation-profile)
                           result (debug/with-activation-profile profile (operation))]
                       (.write writer (str (pr-str @profile) "\n"))
                       result))
          compiled (profiled #(session/extend-session
                               (session/create-session {:id (random-uuid) :run-id (random-uuid)
                                                        :native-extensions [primitives/session-extension]})
                               (session/session-extension {:id :probe :files {"probe.lain" code}})))
          publish (fn [compiler name epoch status base]
                    (profiled #(publication/publish compiler name epoch status base)))
          settled (-> compiled (publish 'subtotal 1 :active 185) (publish 'fee 1 :active 12))
          corrected (publish settled 'subtotal 2 :active 257)
          withdrawn (publish corrected 'subtotal 3 :retracted value/nothing)
          restored (publish withdrawn 'subtotal 4 :active 185)
          snapshots (mapv (fn [[stage compiler expected]]
                            (let [v (session/binding-value compiler 'result)
                                  base (observation/base v)]
                              {:stage stage :value base :support (datum/support-of v)
                               :states (state/states-of v) :expected expected :pass (= expected base)}))
                          [[:initial settled 15.76M] [:corrected corrected 20.56M]
                           [:withdrawn withdrawn value/nothing] [:restored restored 15.76M]])]
      (spit (io/file directory "source.lain") code)
      (spit (io/file directory "results.edn") (pr-str snapshots))
      (doseq [snapshot snapshots] (prn (select-keys snapshot [:stage :value :expected :pass])))
      (assert (every? :pass snapshots) "Branch reconvergence regression"))))
(shutdown-agents)

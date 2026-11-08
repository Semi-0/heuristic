(ns tools.probe-ttms-returned-lists)

;; Reproduce the existing experimental closure cases through public session APIs.
;; All endpoints are declared in Lain; extensions contain no pre-seeded cells.
(load-file (str (System/getenv "LAIN_HARNESS_ROOT") "/tools/offline_uuid.clj"))
(require '[propagators.compiler-2.session :as session]
         '[lain-harness.session-publication :as publication]
         '[propagators.datastructures.layered-value :as datum]
         '[propagators.datastructures.compound-object :as object]
         '[propagators.experimental.amb.observation :as observation]
         '[propagators.experimental.ttms-primitives :as primitives]
         '[propagators.experimental.ttms-application :as application]
         '[propagators.network :as net])

;; Read-only raw topology inspection, retained solely to compare the old
;; assertions with values accessed by compiled Lain expressions.
(defn members [network root]
  (loop [id root result []]
    (if (= :compiler-2/list-empty (net/network-cell-strongest network id)) result
        (let [head (object/existing-slot-cell-id network :car id)
              tail (object/existing-slot-cell-id network :cdr id)]
          (when-not (and head tail) (throw (ex-info "Missing list slot" {:id id})))
          (recur tail (conj result head))))))

(defn publish [compiler epoch status base]
  (let [declaration (publication/declaration compiler 'definition-one epoch status base)]
    (assert (empty? (:cells (session/extension-parts declaration))))
    (session/extend-session compiler declaration)))

(doseq [[kind body]
        [[:flat "(network [x] (list x (+ x 1)))"]
         [:nested "(network [x] (list (list x (+ x 1))))"]
         [:shape "(if (= definition-one 1) (network [x] (list x x)) (network [x] (list x)))"]]]
  (try
    (let [source (str "(define definition-one)\n(define f (ttms-premise-closure " body
                      " definition-one))\n(define result (f 5))\n"
                      (if (= :nested kind)
                        "(define accessed-list (car result))\n(define left (car accessed-list))\n(define right (car (cdr accessed-list)))"
                        (if (= :flat kind)
                          "(define left (car result))\n(define right (car (cdr result)))"
                          "(define left (car result))\n(define right)")))
          declaration (session/session-extension {:id kind :files {"repro.lain" source}})
          initial (session/extend-session
                   (session/create-session {:id (random-uuid) :run-id (random-uuid)
                                            :native-extensions [primitives/session-extension application/session-extension]})
                   declaration)
          active (publish initial 1 :active 'definition-one)
          root (session/binding-cell active 'result)
          first-member (first (members (session/network active) root))
          selected (if (= :nested kind) first-member root)
          snapshot (fn [compiler]
                     (let [network (session/network compiler)]
                       {:list (observation/list-snapshot network selected)
                        :lain-consumers (into {} (for [name '[left right]
                                                       :let [v (session/binding-value compiler name)]]
                                                   [name {:value (observation/base v) :support (datum/support-of v)}]))
                        :accessed-list (when (= :nested kind)
                                         (observation/list-snapshot network (session/binding-cell compiler 'accessed-list)))
                        :member-supports (mapv #(datum/support-of (net/network-cell-strongest network %))
                                               (members (session/network active) selected))}))]
      (prn {:case kind :source source
            :extension-cells (:cells (session/extension-parts declaration))
            :active (snapshot active)})
      (if (= :shape kind)
        (try
          (let [changed (publish active 2 :active 1)]
            (prn {:case kind :stage :shape-change :exception nil :snapshot (snapshot changed)}))
          (catch Throwable error (prn {:case kind :stage :shape-change :exception (ex-message error)})))
        (let [withdrawn (publish active 2 :retracted 'definition-one)
              recovered (publish withdrawn 3 :active 'definition-one)]
          (prn {:case kind :withdrawn (snapshot withdrawn) :recovered (snapshot recovered)}))))
    (catch Throwable error (prn {:case kind :error (ex-message error)})))
  (flush))
(shutdown-agents)

(ns tools.audit-printshop-generated)

;; Controlled diagnostic: compile the saved model source unchanged, then compare
;; explicit source, admission, input and lexical-scope variations. No case calls
;; a provider or repairs the real experiment. All use the public session API;
;; domain cells are declared in Lain, never pre-seeded in an extension.
(load-file (str (System/getenv "LAIN_HARNESS_ROOT") "/tools/offline_uuid.clj"))
(require '[clojure.edn :as edn]
         '[clojure.string :as str]
         '[lain-harness.session-publication :as publication]
         '[lain-harness.admission :as admission]
         '[lain-harness.connected-data :as cd]
         '[lain-harness.environment :as environment]
         '[lain-harness.premise-experiment :as premise]
         '[propagators.cells.value :as value]
         '[propagators.compiler-2.session :as session]
         '[propagators.compiler-2.session.files :as files]
         '[propagators.compiler-2.session.host :as host]
         '[propagators.datastructures.support-collection :as collection]
         '[propagators.experimental.amb.observation :as observation]
         '[propagators.experimental.ttms-application :as application]
         '[propagators.experimental.ttms-primitives :as primitives]
         '[propagators.propagator :as prop])

(def pricing
  '{tier-one-limit 100 tier-two-count 400 tier-one-price 2 tier-two-price 1.60M
    tier-three-price 1.30M rush-rate 0.20M member-rate 0.10M setup-fee 25
    delivery-fee 12 free-delivery-threshold 250 tax-rate 0.08M})
(def fields [:production :rush :discount :subtotal :delivery :tax :total])
(defn extension [id source]
  (session/session-extension {:id id :files {(str (name id) ".lain") source}}))
(defn publish-fact [s n epoch v direct?]
  (if direct?
    (let [ref (session/binding-cell s n)]
      (:session (session/advance s [(session/cell-input ref (collection/content
                                                             {:base v :support #{{:source ref :timestamp epoch :premises-status :active}}}))])))
    (publication/publish s n epoch :active v)))
(defn base-session [direct?]
  (let [s (session/create-session
           {:id (random-uuid) :run-id (random-uuid)
            :native-extensions [primitives/session-extension application/session-extension]
            :propagation-options {:max-steps 300000 :timeout-ms 180000}})
        s (session/extend-session s (files/prepare-extension!
                                     {:id :combinators
                                      :read-files ["propagators/compiler_2/runtime/lain/combinator.lain"]}))
        s (session/extend-session s
                                  (session/session-extension
                                   {:id :generic-scalars
                                    :primitives
                                    (into {} (for [[n f] {'min min 'max max 'round-money
                                                          (fn [x] (.setScale (bigdec x) 2 java.math.RoundingMode/HALF_UP))}]
                                               [n (primitives/scalar-operator
                                                   n (prop/primitive-propagator
                                                      n (fn [& xs] (if-let [bad (first (filter value/unusable? xs))]
                                                                     bad (apply f xs)))))]))}))
        source (str (apply str (for [n (concat (keys pricing) '[quantity-1 rush-1 member-1 discount-cap-1 question-1 shared-rule user-output-1 self-check-1])]
                                 (str "(define " n ")")))
                    "(define pricing (struct " (str/join " " (for [n (keys pricing)] (str ":" n " " n))) "))"
                    "(define order-1 (struct :quantity quantity-1 :rush? rush-1 :member? member-1 :discount-cap discount-cap-1 :question question-1))"
                    (apply str (for [f fields] (str "(define observed-" (name f) "-1)")))
                    "(define quote-1 (struct " (str/join " " (for [f fields] (str f " observed-" (name f) "-1"))) "))")
        s (session/extend-session s (extension :ports source))]
    (reduce (fn [current [n v]] (publish-fact current n 1 v direct?)) s
            (concat pricing [['quantity-1 80] ['rush-1 false] ['member-1 false] ['discount-cap-1 40]
                             ['question-1 "Quote 80 copies"] ['shared-rule "Pricing rules"]]))))
(defn outputs [s]
  (into {} (for [n (concat (map #(symbol (str "observed-" (name %) "-1")) fields) '[user-output-1])]
             [n (observation/base (session/binding-value s n))])))
(defn admit [s label text sequential?]
  (if sequential?
    (reduce (fn [current [i form]]
              (session/extend-session current (extension (keyword (str (name label) "-" i)) (pr-str form))))
            s (map-indexed vector (edn/read-string (str "[" text "]"))))
    (session/extend-session s (extension label text))))
(defn scoped-admit [s label text aliases?]
  (let [s (session/extend-session s (premise/facilities))
        s (session/extend-session s (session/session-extension
                                     {:id :environments
                                      :primitives (assoc (environment/bindings (session/environment s))
                                                         'cell:reference (cd/reference-operator))}))
        aliases {'+ 'ttms:+ '- 'ttms:- '* 'ttms:* '>= 'ttms:>= 'str 'ttms:str
                 'select 'ttms:select 'ttms-premise-closure 'ttms:ttms-premise-closure}
        parent (if aliases?
                 (reduce (fn [source [n reference]]
                           (str "(extend-env " source " (env:reference-slot " (pr-str (str n))
                                " (cell:reference " reference ")))")) "(empty-env)" aliases)
                 "(empty-env)")
        s (session/extend-session s (extension :scope (str "(define target (lexical-child " parent "))")))
        target (cd/unpack (session/binding-value s 'target))]
    (reduce (fn [current [i form]]
              (session/extend-session current (session/session-extension
                                               (admission/declaration [label i] (pr-str form) target nil))))
            s (map-indexed vector (edn/read-string (str "[" text "]"))))))
(let [source (slurp (first *command-line-args*))
      conditional (str/replace source "(select " "(if ")]
  (doseq [[label text sequential? direct?]
          [[:original source false false] [:select-to-if conditional false false]
           [:original-sequential source true false] [:if-sequential conditional true false]
           [:original-direct source true true] [:if-direct conditional true true]]]
    (try
      (let [s (admit (base-session direct?) label text sequential?)
            changed (publish-fact s 'quantity-1 2 120 direct?)]
        (prn {:case label :initial (outputs s) :quantity-corrected (outputs changed)
              :pre-seeded-cells (count (:cells (session/session-declarations s)))})
        (flush))
      (catch Throwable error (prn {:case label :error (ex-message error)})))))
(let [source (slurp (first *command-line-args*))]
  (doseq [[label text aliases?] [[:scoped source false] [:scoped-aliases source true]
                                 [:scoped-aliases-if (str/replace source "(select " "(if ") true]]]
    (try
      (let [s (scoped-admit (base-session false) label text aliases?)]
        (prn {:case label :initial (outputs s)
              :pre-seeded-cells (count (:cells (session/session-declarations s)))})
        (flush))
      (catch Throwable error (prn {:case label :error (ex-message error)})))))
(let [source (slurp (first *command-line-args*))]
  (doseq [[label text] [[:managed-original source] [:managed-if (str/replace source "(select " "(if ")]]]
    (let [owner (host/open! (base-session false))]
      (try
        (doseq [[i form] (map-indexed vector (edn/read-string (str "[" text "]")))]
          (host/extend! owner (extension (keyword (str (name label) "-" i)) (pr-str form))))
        (prn {:case label :initial (outputs (host/current-session owner))})
        (flush)
        (catch Throwable error (prn {:case label :error (ex-message error)}))
        (finally (host/close! owner))))))
(shutdown-agents)

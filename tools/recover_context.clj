;; Offline retention reconciliation after a terminal run. No compiler/session/model.
(load-file (str (System/getenv "LAIN_HARNESS_ROOT") "/tools/offline_uuid.clj"))
(require '[clojure.edn :as edn] '[clojure.string :as str] '[clojure.java.io :as io]
         '[datomic.client.api :as d] '[lain-harness.catalog :as catalog]
         '[lain-harness.context-store :as store])
(def dir (first *command-line-args*))
(defn read-file [name]
  (let [f (io/file dir name)]
    (when (.exists f) (edn/read-string {:default tagged-literal} (slurp f)))))
(let [database (store/install! (catalog/open! (str dir "/database")))
      fatal (slurp (io/file dir "fatal-error.edn"))
      _ (store/archive! database :run/fatal :validation fatal)
      rows (d/q {:query '[:find ?id :where [?e :catalog/id ?id] [?e :catalog/evidence-kind]]
                 :args [(d/db (:connection database))]})
      records (into {} (for [[id] rows
                            :let [e (store/entity database id) text (store/restore (:catalog/evidence-chunk e))]]
                        [id {:kind (:catalog/evidence-kind e) :text text}]))
      parse-record (fn [id] (when-let [text (get-in records [id :text])]
                              (edn/read-string {:default tagged-literal} text)))
      live (read-file "live-status.edn")
      outcomes (into {} (for [[q status] (:statuses live) :when status]
                          [q (parse-record (keyword "evidence" (str "q" q "/outcome")))]))
      costs (vec (for [{:keys [id replay? tokens]} (:calls live)
                       :let [http (read-file (str id "-http.edn"))
                             result (parse-record (keyword "evidence" (str id "/result")))]]
                   {:id id :replay? replay? :preparation? (str/starts-with? id "q0-")
                    :stage (if (str/includes? id "-index") :indexing
                               (if (= :retrieve (get-in result [:result :decision])) :retrieval :execution))
                    :repair? (str/includes? id "-correction") :input-tokens tokens
                    :usage (when-not replay? (:usage http)) :elapsed-ms (:elapsed-ms http)}))
      unreached (vec (for [q (sort (keys (:statuses live)))
                           :when (not-any? #(str/starts-with? (:id %) (str "q" q "-")) (:calls live))] q))
      result {:terminal :host-failure :reconciled-offline? true :journal-complete? false
              :mode (:mode (read-file "settings.edn")) :calls (:calls live) :costs costs :outcomes outcomes
              :correct (into {} (for [q (keys (:statuses live))] [q (= :delivered (get-in outcomes [q :status]))]))
              :indexing (into {} (for [[q outcome] outcomes] [q (get-in outcome [:index :status])]))
              :unreached unreached :within-budget? (every? #(<= (:tokens %) 12000) (:calls live))}]
  (spit (io/file dir "recovered-result.edn") (pr-str result))
  (spit (io/file dir "archive-manifest.edn")
        (pr-str (into {} (for [[id {:keys [kind text]}] records]
                          [id {:kind kind :characters (count text)
                               :sha256 (format "%064x" (java.math.BigInteger. 1
                                          (.digest (java.security.MessageDigest/getInstance "SHA-256")
                                                   (.getBytes text "UTF-8"))))}]))))
  (prn (dissoc result :outcomes :costs :calls)))
(shutdown-agents)

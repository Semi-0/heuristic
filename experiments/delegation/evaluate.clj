(require '[clojure.edn :as edn] '[clojure.set :as set] '[clojure.data.json :as json])
(let [[dir] *command-line-args*
      read-artifact (fn [file] (edn/read-string {:default tagged-literal} (slurp (str dir "/" file ".edn"))))
      stages (mapv read-artifact ["before" "after-first" "after"])
      [before first-world complete] stages
      value (fn [snapshot port] (get-in snapshot [port :value]))
      times (json/read-str (slurp (str dir "/times.json")) :key-fn keyword)
      n (value before 'probe-count)
      [a b] (map #(value before %) '[am-predicted bm-predicted])
      marginal (+ 20000 (* 150 (- n 100)))
      volume (* 150 n)
      worlds [['am a marginal 26000 "invoice-marginal"] ['bm b marginal 26000 "invoice-marginal"]
              ['av a volume 21000 "invoice-volume"] ['bv b volume 21000 "invoice-volume"]]
      checks
      {:calls (= #{:plan :a :b} (set (keys times)))
       :assignments (every? #(string? (value before %)) '[job-a job-b])
       :old-invoices (every? true? (map #(= %1 (value before %2))
                                    [16000 20000 16000 20000] '[old-a-80 old-a-100 old-b-80 old-b-100]))
       :distinct-probe (and (> n 100) (not= a b))
       :covers-both-advertised-readings (= (set [a b]) #{marginal volume})
       :undecided-before (every? #(= :unknown (value before %)) '[am-fits bm-fits av-fits bv-fits])
       :world-isolation (every? #(= :unknown (value first-world %)) '[av-fits bv-fits])
       :verification (every? true? (for [[stem prediction invoice cents source] worlds
                                         :let [matches (= prediction invoice)
                                               port (symbol (str stem "-quote"))]]
                                     (and (= matches (value complete (symbol (str stem "-fits"))))
                                          (= (if matches cents :unknown) (value complete port))
                                          (or (not matches)
                                              (set/subset? #{source "target-count" (str "branch-" (first (str stem)) "-premise")}
                                                           (set (get-in complete [port :source-names]))))))) }]

  (prn {:checks checks :all-pass (every? true? (vals checks)) :quantity n :predictions [a b]}))

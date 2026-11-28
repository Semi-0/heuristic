(ns attempt-cli.tools
  (:require [clojure.java.io :as io] [clojure.string :as str]
            [datomic.client.api :as d]
            [lain-harness.admission :as admission] [propagators.compiler.language.reader :as compiler-reader]
            [lain-harness.premise-experiment :as premise] [lain-harness.session-publication :as pub]
            [propagators.compiler-2.session :as s] [propagators.compiler-2.session.connected :as c]
            [propagators.compiler-2.session.host :as h] [propagators.compiler-2.session.operators :as op]
            [propagators.network :as net])
  (:import [java.util.concurrent TimeUnit]))
(def good "(define price (network [n] (if (<= n 100) (* 200 n) (+ 20000 (* 150 (- n 100))))))")
(def broken (subs good 0 (dec (count good))))
(defn read-draft [text]
  (try
    {:form (compiler-reader/read-form text)}
    (catch RuntimeException e
      {:error (ex-message e)})))
(defn check-python [dir id code]
  (let [file (io/file dir "probe.py") _ (spit file code)
        run-dir (.getParentFile dir) stdout (io/file run-dir (str id ".stdout")) stderr (io/file run-dir (str id ".stderr"))
        cmd ["prlimit" "--cpu=2" "--as=201326592" "--fsize=8192" "--"
             "bwrap" "--unshare-all" "--die-with-parent" "--new-session" "--clearenv"
             "--ro-bind" "/usr" "/usr" "--symlink" "usr/lib" "/lib" "--symlink" "usr/bin" "/bin"
             "--dev" "/dev" "--proc" "/proc" "--tmpfs" "/tmp" "--ro-bind" (.getCanonicalPath dir) "/work"
             "--chdir" "/work" "--" "/usr/bin/prlimit" "--nproc=1" "--" "/usr/bin/python3" "-I" "probe.py"]
        p (.start (doto (ProcessBuilder. ^java.util.List cmd) (.redirectOutput stdout) (.redirectError stderr)))]
    (try
      (let [finished (.waitFor p 5 TimeUnit/SECONDS)]
        (when-not finished (.destroyForcibly p) (.waitFor p))
        {:exit (.exitValue p) :timeout (not finished) :stdout (subs (slurp stdout) 0 (min 1600 (count (slurp stdout))))
         :stderr (subs (slurp stderr) 0 (min 1600 (count (slurp stderr))))})
      (finally (when (.isAlive p) (.destroyForcibly p))))))
(defn extend-source! [owner frame id source]
  (let [declaration (admission/declaration id source frame nil)
        receipt @(h/submit-extension! owner (s/session-extension declaration))]
    (when-not (= :admitted (:status receipt)) (throw (ex-info "Compiler rejected form" receipt)))
    (s/source-result-cell (h/current-session owner) (first (keys (:files declaration))))))
(defn submit! [owner frame id body]
  (let [{:keys [form error]} (read-draft body)]
    (if error {:error error :admitted false}
      (if-not (and (list? form) (= ['define 'price] (take 2 form)) (= 3 (count form))
                   (list? (last form)) (= 'network (first (last form))))
        {:error "Expected one (define price (network [n] ...))" :admitted false}
        (do (extend-source! owner frame [id :definition] body)
            (let [values (mapv (fn [n]
                                 (let [cell (extend-source! owner frame [id n] (str "(price " n ")"))
                                       nw (s/network (h/current-session owner))]
                                   (c/payload (net/network-cell-strongest nw cell)))) [79 100 107 143])]
              {:admitted true :verified (= values [15800 20000 21050 26450]) :values (mapv str values)}))))))
(defn price-result [owner rule ref obtained]
  (if-let [port ({"marginal" 'marginal-result "whole" 'whole-result "flat" 'flat-result} rule)]
    (let [cs (h/current-session owner) v (premise/inspect-value (s/network cs) (s/binding-cell cs port))
          ids (into {} (for [n '[policy invoice120 target-count]] [(s/binding-cell cs n) (str n)]))
          supports (set (keep #(ids (:source %)) (:support v)))]
      {:value (str (:value v)) :sources (vec (sort supports))
       :verified (and (= "invoice-120" ref) (= 120 obtained) (= "marginal" rule)
                      (= (:value v) 26000) (= #{"policy" "invoice120" "target-count"} supports))})
    {:error "Unknown callable"}))
(defn perform! [{:keys [cli? fixture dir archive counters]} owner id reply frame]
  (if-not (and (= :reply (:kind reply)) (= "stop" (:finish-reason reply)) (false? (:truncated? reply)))
    {:done true :feedback "Incomplete provider response" :action "ERROR"}
    (let [[line body] (str/split (str/trim (:text reply)) #"\r?\n" 2)
          [action arg more] (str/split line #"\s+" 3)
          body (or body "")
          charge (fn [k limit] (<= (get (swap! counters update k (fnil inc 0)) k) limit))
          r (case action
              "HISTORY" (if (charge :reads 2)
                          (let [rows (d/q '[:find ?status ?desc :where [?a :catalog/status ?status] [?a :catalog/description ?desc]]
                                          (d/db (:connection archive)))]
                            {:rows (vec (sort (filter #(or (= "all" arg) (= arg (name (first %)))) rows)))})
                          {:error "History budget exhausted"})
              "NOTE" {:recorded true}
              "FINAL" {:answer (str/trim (subs (:text reply) (min 5 (count (:text reply)))))}
              "INVOICE" (cond (not (#{"90" "120"} arg)) {:error "Choose 90 or 120"}
                              (not (charge :invoices 1)) {:error "Invoice budget exhausted"}
                              :else (let [n (parse-long arg) cents (if (= 120 n) 23000 18000)]
                                      (when (= n 120)
                                        @(h/submit-extension! owner (pub/declaration (h/current-session owner) 'invoice120 1 :active cents)))
                                      (swap! counters assoc :obtained n)
                                      {:id (str "invoice-" n) :quantity n :cents cents :policy "p1"}))
              "QUOTE" (price-result owner arg more (:obtained @counters))
              "SUBMIT" (submit! owner frame id body)
              "CLI" (cond (not cli?) {:error "CLI unavailable"}
                          (not (charge :cli 2)) {:error "CLI budget exhausted"}
                          (= ["python3" "-c"] [arg more]) (assoc (check-python dir id body) :draft id)
                          (= arg "lain-read") (let [f (io/file dir "draft.lain")
                                                    _ (when (seq body) (spit f body))
                                                    error (:error (read-draft (slurp f)))]
                                                {:draft id :exit (if error 1 0) :stdout (if error "" "One complete form")
                                                 :stderr (or error "")})
                          :else {:error "Unknown command"})
              {:error "Unknown action. Use the documented action tag on line one."})
          verified (and (:verified r) (= action (if (= fixture "price") "QUOTE" "SUBMIT")))]
      {:action action :result r :verified (boolean verified) :done (boolean (or verified (= "FINAL" action)))
       :feedback (pr-str (dissoc r :values))})))
(defn facilities [config journal]
  (let [effects (reify op/EffectInterpreter
                  (interpret-effect! [_ owner context {:keys [id reply frame]} emit!]
                    (let [finished (atom false)
                          work (future
                                 (try (let [r (try (perform! config owner id reply frame)
                                                   (catch Exception e {:done false :feedback (ex-message e) :action "ERROR"}))]
                                        (swap! journal conj (assoc r :id id))
                                        (emit! r))
                                      (finally (reset! finished true) ((:complete! context)))))]
                      (fn [] (when-not @finished (future-cancel work)) {:status :closed}))))]
    (s/session-extension {:id ::cli-pilot
                          :primitives {'attempt:perform (op/async-op effects
                            (fn [make] (make (fn [id reply frame]
                                              (op/effect-output {:id id :reply reply :frame frame})))) )}})))

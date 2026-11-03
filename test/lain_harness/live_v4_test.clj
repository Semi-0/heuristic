(ns lain-harness.live-v4-test
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [graph.json :as json]
            [lain-harness.live-v4 :as adapter]
            [propagators.compiler-2.session :as session]
            [propagators.compiler-2.session.files :as files]
            [propagators.compiler-2.session.host :as host]
            [propagators.experimental.visualization.data :as data]
            [propagators.cells.value :as value]))
(defn await! [f]
  (let [end (+ (System/currentTimeMillis) 15000)]
    (loop [] (cond (f) true (> (System/currentTimeMillis) end) false
                   :else (do (Thread/sleep 25) (recur))))))
(defn observed [owner name]
  (data/payload (session/binding-value (host/current-session owner) name)))
(def generated
  "(define umbrella-for-v4 (network [place date] (if (> (h4:weather-rain-probability place date) 0.5) \"Take an umbrella\" \"No umbrella needed\"))) (-> (umbrella-for-v4 \"Amsterdam\" \"2026-11-04\") h4:answer)")
(deftest source-admission
  (is (= 2 (count (adapter/validate-source generated))))
  (is (thrown? Exception (adapter/validate-source "(load-lain \"secret\")")))
  (is (thrown? Exception (adapter/validate-source "(define runtime 0)"))))
(deftest lexical-names-are-not-host-capabilities
  (is (seq (adapter/validate-source "(define umbrella-for-v4 (network [city day] (let [prob (h4:weather-rain-probability city day)] (if (> prob 0.5) true false))))")))
  (is (thrown? Exception (adapter/validate-source "(define umbrella-for-v4 (network [city day] (unknown-host-operation city)))"))))

(deftest live-connected-environment-and-compiled-reuse
  (let [seen (atom [])
        config {:model-call (fn [input]
                              (let [description (json/read-json input)]
                                (swap! seen conj description)
                                (json/write-json {:source (if (= "initial" (:observation description)) generated "")
                                                  :note "deterministic model fixture"})))}
        base (session/extend-session (session/create-session)
                                     (files/prepare-extension! {:id :bootstrap
                                                                :primitives (into {} (adapter/bindings config))
                                                                :files {"test-scaffold.lain"
                                                                        (str "(define h4:guide \"guide\") (define h4:design \"design\")\n"
                                                                             (slurp (str adapter/project "/experiments/live/scaffold-v4.lain")))}}))
        owner (host/open! base)]
    (try
      (host/step! owner)
      (is (empty? @seen) "Missing required input leaves inference pending")
      (doseq [[name content] [["h4:weather-mode" "fixture"] ["h4:request" "first"]
                              ["h4:capabilities" "h4:weather-rain-probability"] ["h4:observation" "initial"]]]
        (adapter/publish! owner name 1 content))
      (is (await! #(= "Take an umbrella" (observed owner 'h4:answer))))
      (is (await! #(>= (count @seen) 2)) "Compiler observation reaches model")
      (is (= "guide" (:guide (first @seen))))
      (is (= "design" (:design (first @seen))))
      (is (value/nothing? (observed owner 'h4:unrelated)))
      (let [before (count @seen)
            output (session/binding-cell (host/current-session owner) 'h4:model-input)]
        (adapter/publish! owner "h4:capabilities" 2 "h4:weather-rain-probability; late capability")
        (is (await! #(> (count @seen) before)) "Capability change reaches existing model")
        (is (= output (session/binding-cell (host/current-session owner) 'h4:model-input)))
        (is (str/includes? (:capabilities (last @seen)) "late capability")))
      (host/extend-source! owner "(-> (umbrella-for-v4 \"Rotterdam\" \"2026-11-04\") h4:answer-2)")
      (is (await! #(= "No umbrella needed" (observed owner 'h4:answer-2))))
      (host/extend-source! owner
                           (str "(h4:publish " (pr-str "h4:observation") " 100 "
                                (pr-str "high-revision supervisor update") ")"))
      (is (await! #(= "high-revision supervisor update" (observed owner 'h4:observation))))
      (let [bad-source (pr-str (list '-> (list 'umbrella-for-v4 "Amsterdam" "2026-11-04" "UTC") 'h4:answer-live))]
        (host/extend-source! owner (str "(define malformed (h4:install " (pr-str bad-source) "))")))
      (is (await! #(str/includes? (str (observed owner 'h4:observation)) "invalid arity"))
          "Automatic compiler feedback advances beyond supervisor revision 100")
      (is (empty? (host/failures owner)))
      (finally (host/close! owner)))))
(defn -main [& _]
  (let [r (run-tests 'lain-harness.live-v4-test)]
    (shutdown-agents)
    (System/exit (if (zero? (+ (:fail r) (:error r))) 0 1))))

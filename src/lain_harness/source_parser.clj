(ns lain-harness.source-parser
  "Native incremental source reader. Cursor mutation is invocation-local; no persistent state or IO."
  (:require [clojure.edn :as edn] [clojure.string :as str]
            [propagators.compiler-2.runtime.session.program.source :as source])
  (:import [java.io PushbackReader StringReader]))
(defn- incomplete? [e]
  (some #(str/includes? (or (ex-message %) "") "EOF")
        (take-while some? (iterate ex-cause e))))
(defn read-next [text finish?]
  (let [normalized (str/replace text #"(\(\s*)::(?=\s)" "$1zz")
        position (atom 0)
        reader (proxy [PushbackReader] [(StringReader. normalized)]
                 (read [] (let [c (proxy-super read)] (when (not= -1 c) (swap! position inc)) c))
                 (unread [c] (proxy-super unread (int c)) (swap! position dec)))
        eof (Object.)]
    (try
      (let [form (edn/read {:eof eof} reader) end @position]
        (cond
          (identical? eof form) {:empty? true}
          (or finish? (< end (count text))
              (and (pos? end) (contains? #{\) \] \} \"} (nth text (dec end)))))
          (let [raw (subs text 0 end) forms (source/read-source-forms raw)]
            (when-not (= 1 (count forms)) (throw (ex-info "Expected one complete form" {})))
            {:source raw :form (first forms) :end end})
          :else nil))
      (catch Exception e
        (if (and (incomplete? e) (not finish?)) nil
            (throw (ex-info (if (incomplete? e) "Incomplete final Lain form" "Invalid Lain form")
                            {:buffer text} e)))))))

(ns lain-harness.stream
  "Incremental EDN/Lain form framing; no host evaluation."
  (:require [clojure.edn :as edn] [clojure.string :as str]
            [propagators.compiler-2.runtime.session.program.source :as source])
  (:import [java.io PushbackReader StringReader]))
(defn framer [] (atom {:buffer "" :index 0 :closed? false}))
(defn- incomplete? [e]
  (some #(str/includes? (or (ex-message %) "") "EOF")
        (take-while some? (iterate ex-cause e))))
(defn- next-form [text finish?]
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
(defn feed!
  "Emit each complete form synchronously; a failed admission stops parsing."
  [state chunk finish? emit!]
  (locking state
    (when (:closed? @state) (throw (ex-info "Code stream is closed" {})))
    (swap! state update :buffer str chunk)
    (loop []
      (when-let [next (next-form (:buffer @state) finish?)]
        (if (:empty? next)
          (swap! state assoc :buffer "")
          (do
            (emit! (assoc (select-keys next [:source :form]) :index (:index @state)))
            (swap! state #(-> % (update :buffer subs (:end next)) (update :index inc)))
            (recur)))))
    (when finish? (swap! state assoc :closed? true))))

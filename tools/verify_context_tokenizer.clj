(load-file (str (System/getenv "LAIN_HARNESS_ROOT") "/tools/offline_uuid.clj"))
(require '[lain-harness.context-transport :as transport])
(let [root (System/getenv "LAIN_EVIDENCE_DIR")
      record! (fn [id kind v] (spit (str root "/" id "-" (name kind) ".edn") (pr-str v)))
      provider (transport/transport record!)
      request {:id "tokenizer-check" :cancelled? (constantly false)
               :messages [{:role "system" :content "Return only OK."}
                          {:role "user" :content "Unicode check: λ 你好 😀. Reply OK."}]}
      count (transport/token-count request)
      response (provider request)
      result {:tokenizer-count count :provider-prompt-tokens (get-in response [:usage :prompt_tokens])
              :settings transport/settings :template transport/template
              :response response :verified? (= count (get-in response [:usage :prompt_tokens]))}]
  (spit (str root "/tokenizer-verification.edn") (pr-str result))
  (prn result)
  (assert (:verified? result)))
(shutdown-agents)

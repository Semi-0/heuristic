# Pi transport session extension — November 16

The active model definition is [model-refined.lain](../model-refined.lain). It and [soul-refined.lain](../soul-refined.lain) are unchanged. The old lib/model.lain reducer/FSM is not used.

[pi_session.clj](../src/lain_harness/pi_session.clj) supplies a separate session extender using the existing model-io-session/declaration API. Its transport drives [tools/pi/bridge.mjs](../tools/pi/bridge.mjs), using installed Pi SDK 1.4.0. No kernel changes or deployed-service replacement.

```clojure
(host/extend! owner
  (pi/declaration root "examples/lain/printshop-runtime.lain"
    {:bridge (str root "/tools/pi/bridge.mjs")
     :evidence evidence-directory
     :inspect (pi/inspector owner 'generated-environment)}))
```

This selects the backend when declaring the session; it does not hot-swap an already-running model worker. The original Lain shape remains:

```lain
(define m (model))
(define running-runtime
  (runtime runtime-environment
           (struct :input (model:input m) :output model-result)))
```

Lain owns requests, phase progression, lexical child, completed-response admission, repair policy and terminal outcome. Pi performs only the local tool loop inside a requested stage. It cannot compile/admit source independently. Its sole tool, inspect_lain, reads bounded visible binding names or immediate value kind and up to eight input/output edges. Callable bodies and arbitrary runtime objects are not dumped. No shell, file-edit, project skills or unrelated global extensions are exposed.

Each Lain request gets a scoped Pi session and subprocess, closed at completion/cancellation; no private cross-stage conversation is accumulated. Earlier source and conversation still come from the Lain environment. Text-only requests use an empty system context; explicit messages currently support the system/user snapshot pair used by the experiment, not arbitrary multi-role history arrays. Unsupported shapes fail explicitly.

A Pi before_agent_start hook projects the exact Lain system prompt. Every provider payload checks that it was preserved. Tool definitions are protocol metadata describing the same capability documented in the environment. SDK-default prompt additions were caught by control tests and removed through the documented hook. Automatic compaction and retries are disabled in this first adapter: this is a bounded inspection-loop experiment, not a test of all Pi features or a context-overflow fix.

Generation settings: local qwen3.8-flash-next, temperature 0, max output 8192, thinking medium/enabled, requested thinking budget 4096. Pi may make up to four inference calls and eight inspection calls per Lain request; direct transport makes one. Budget differences must be included in comparisons. Partial, error or length-finished Pi responses are not admitted as completed source.

## Verification and experiment

- Real Pi SDK with a synthetic HTTP provider: inspection tool call/result followed by final response; exact Lain prompt and restricted tool list verified.
- Compiled Lain session via the separate extender: same model definition, one real Pi tool call against synthetic provider, output delivery and clean owner closure passed.
- Three focused transport tests / six assertions passed: incomplete response rejection, cancellation, observation callback forwarding.
- New Clojure extender and tests formatted; lint reports zero errors/warnings. Node syntax check passed.
- One matched five-question retained print-shop episode per backend launched, direct then Pi. Both get the same additional environment documentation for the optional transport tool. Historical Lain phase/repair orchestration and model files stay unchanged; evaluator remains independent. No real outcome claimed yet.

Local evidence: [/home/lain/reports/heuristic-pi-20261116/](/home/lain/reports/heuristic-pi-20261116/manifest.json). Raw HTTP, Pi payload/events, source, admissions, errors and evaluations are preserved. The provider proxy observes idle before actual requests; this is not an exclusive reservation. Trials run under the resource watchdog. No commits or pushes.

Failed development controls were fixture/integration issues: SDK-added prompt metadata, initially inactive custom tool loadout, missing data-record facilities in the compiled test fixture, and a close exception masking cancellation. All were corrected before the real comparison. This is not a claim of a full runtime test-suite pass.


## Terminal result — November 17

The real comparison finished: direct completed all five questions correctly with four later callable reuses; Pi performed six successful inspections but failed generalization before any implementation because :decision was a string rather than the required keyword. No reliability/reuse improvement demonstrated in this pair. Both owners closed, all HTTP calls succeeded, and no trials were restarted. [Reviewed results](/home/lain/reports/heuristic-pi-20261116/RESULTS.md).

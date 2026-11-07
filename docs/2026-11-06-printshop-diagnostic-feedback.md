# Strict procedure with diagnostic feedback

This condition extends the standalone strict procedure with a host-published `errors` binding. Five print-shop questions run in one persistent lexical child and worker. Each question gets one analysis request and one implementation request. Provider configuration and questions are unchanged from the strict-procedure condition. This is a single exploratory session, not a causal comparison.

Before each analysis, the host inspects previous implementation sources, visible runtime bindings, previous output ports and the last admission receipt. It publishes structured diagnostic records as EDN text. Records distinguish unresolved operator-position bindings, blocked outputs, runtime contradictions and rejected admissions. Unknown future outputs are excluded. No oracle values or corrected formulas are fed to the model.

The model is instructed to inspect these observations before choosing an operator, describe relevant diagnostics and repairs in its existing analysis fields, and verify earlier and current cases when repairing. It receives no extra repair prompts or calls. A rejected implementation is recorded and the next scheduled question can proceed; provider and host failures still stop the trial. Analysis-format and operator-selection gates remain unchanged.

Records retain actual cell references, direct runtime input edges, supports and source action/form references. These are diagnostic observations. The `errors` binding is an explicitly refreshed snapshot, not a new reactive error-propagation algebra. Source references identify uses, not proven causes; generic graph edges include observation machinery. Runtime contradictions are reported without claiming their cause has been identified. Original records remain in `diagnostics.edn` and all original model source remains in evidence, even if a later snapshot clears.

Validation includes controlled examples for unresolved operator references, benign unknown bindings, blocked outputs, contradictions, rejected admission receipts, source attribution and cleared states. The full controlled session checks phase gating, visibility, pricing, identity and reuse. Two previously established mutation failures remain the baseline: quantity correction and subsequent question restoration can leave total unknown. These controls do not count as model success.

Model timing uses context/response file timestamps to approximate provider round-trip latency; it excludes environment setup, admission and mutation checks and is not pure reasoning time.

## Results

The controlled session completed all ten synthetic responses. All 35 initial pricing observations, callable reuse, phase ordering, complete diagnostic visibility and source/history checks passed. Its only failures were the two established baseline mutation failures. The collector controls also passed. Controls are synthetic and do not count as model successes.

The real session completed exactly ten provider requests in one persistent child and worker. All five implementations were admitted, and original analysis and implementation text matched the saved responses. Diagnostics in every analysis context were verified by parsing their EDN section and comparing the full structured value; literal string comparison would incorrectly fail when unordered sets/maps print differently. No requests were repeated.

| Outcome | Observation |
| --- | --- |
| Diagnostic records before questions 1–5 | 0, 9, 18, 27, 36; all blocked outputs |
| Callable creation | `compute-quote`, in question 1 |
| Existing callable invocation | Questions 2–5, same runtime identity/declaration and captured environment |
| Callable reuse connected to an answer | 0/4 |
| Correct numerical output fields | 0/35; all answer ports unknown |
| Usable text replies / self-checks | 0/5 each |
| Repair attempts | None observed in source; later analyses repeatedly choose reuse without addressing diagnostics |
| Correct dependency behavior | Not demonstrated: outputs remain unknown, so unchanged/withdrawn unknowns are vacuous |

The model consistently reversed the connections, writing `(-> answer-port computed-value)` instead of connecting the computation into the answer port. It also omitted the rush charge from the subtotal in its operator, despite correctly stating that charge in the pricing rules. Its later analyses repeated that subtotal mistake and described tests that shared it. The host did not supply either diagnosis or corrected formulas to the model.

All outputs stayed unknown through the no-model mutation checks. The evaluator records numerical corrections, recoveries and missing support checks individually; invariance of unknown outputs must not be interpreted as a successful network. Conversation history contains the five actual user messages and no usable assistant output messages; generated source and separate provider thinking remain preserved.

This run exercises feedback about blocked outputs, not recovery from an actual compiler rejection or runtime contradiction. Those categories were tested with controlled records only. Unresolved-operator detection is limited to unknown bindings visible in the child; it does not introspect every nested lexical scope. Numeric port records often have empty direct source-reference lists because generated source names the public quote slots rather than their backing observation bindings. Direct runtime input edges are retained, but this version does not trace a minimal causal path from a source action to a blocked output.

## Timing

| Condition | Provider requests | Sum of provider round-trip time |
| --- | ---: | ---: |
| Struct baseline | 5 | 174.016 s |
| Strict procedure | 10 | 370.109 s |
| Strict procedure + diagnostics | 10 | 416.118 s |

Diagnostics were associated with 12.4% longer provider time than the previous strict run, without a usable solution. This is one session per condition, not randomized or interleaved, and does not establish a causal slowdown. It is not time-to-correct-solution. Accumulating unresolved records increases context size substantially, in addition to accumulated source history.

The result supports a narrow conclusion: exposing blocked-output observations and asking the model to inspect them did not cause repair in this session. A stronger follow-up could require the analysis to account for each unresolved diagnostic before selecting reuse, with more precise links between public ports, source connections and runtime dependencies. That follow-up has not been run, and general-methodology integration remains deferred.

## Reproduction and evidence

- Runner: `tools/run_printshop_diagnostics.sh` (`control` or `live`, fresh absolute `LAIN_EVIDENCE_DIR` required).
- Parent: `examples/lain/printshop-diagnostics.lain`.
- Collector and controls: `tools/printshop_diagnostics.clj`, `tools/test_printshop_diagnostics.clj`.
- Evaluation: `clojure -M tools/evaluate_printshop_diagnostics.clj <evidence-directory>`.
- Timing: `python3 tools/compare_printshop_diagnostics.py`.
- Controlled evidence: `runtime/printshop-diagnostics-control/`.
- Real-model evidence: `runtime/printshop-diagnostics-live-20261106/`, including original contexts, responses/thinking, source, admission receipts, graph identities/edges, supports, snapshots, diagnostic history, evaluation, integrity and timing comparison.

Each evidence directory includes the probe, collector, evaluator and runner source. The runner uses an isolated archived checkout, the existing companion runtime and unchanged provider settings. No deployed service or unrelated working-tree file was modified.

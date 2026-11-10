# Autonomous repair continuation, November 9–10

The final real-provider trial completed all five questions in one worker and lexical child. **All 84 evaluator checks, nine conversation/stability checks and eight phase/source integrity checks pass.** The model generated `quote-pricing` once and reused that same callable for the next four questions. It made 15 real completion calls, with no repair calls or truncated responses in that final trial.

| Condition | Real completion calls | Completed questions | Outcome |
|---|---:|---:|---|
| Original timestamped repair guidance | 5 | 0/5 | All source admitted; contradictions remained after two genuine repair attempts. |
| Compact feedback and live parent diagnostics | 8 | 2/5 | First-response format repair succeeded; later keyword status rejected by the old string-only schema. |
| Label contract and documented branch limits | 11 | 3/5 | Question-four generalization contained a prose prelude; correctly rejected. |
| Same environment, 8,192 total / 4,096 thinking tokens | 15 | 5/5 | All acceptance checks pass. |
| Separate recovery reference | 0 (16 controlled requests) | 5/5 | All 84 reference checks pass on the unchanged runtime. |

Failed trials are retained separately. This is an exploratory campaign with changed conditions, not a reliability estimate or a randomized comparison.

The campaign follows [timestamped repair guidance](2026-11-08-timestamped-repair-guidance.md). The original campaign base is `98f848c`, with the core diagnostic/phase changes checkpointed at `288bb65`; the companion runtime is `2773dffc1cf5a2b129559aa445d66d09f96d4dd7`. Kernel source and deployed services are unchanged. Bulk evidence stays local under ignored `runtime/autonomous-20261109/`.

## First real-provider trial

`live-1` made five real requests through the existing worker: analysis, generalization, implementation, repair one and repair two. Every request carried explicit system/user messages assembled by Lain. Every provider response completed with `stop`, without truncation. No source was rewritten or replaced with the controlled solution.

The model constructed `quote-for-order`, invoked it, connected seven breakdown fields and a text reply, and attached both shared-rule and question-one premises. All eleven implementation forms were admitted. Observations nevertheless showed production `160` and rush `0`, with discount, subtotal, delivery, tax, total and reply contradictory. The expected premise links were present.

Lain sent both repairs with the actual UTC receipts and failed-output observations. The model first changed the callable to accept scalar order fields, then passed the existing input cells directly. All three responses admitted successfully. Discount became `0`, but subtotal and downstream outputs remained contradictory. Lain exhausted its declared two-correction budget, stopped with `:failed`, and closed the owner cleanly. This establishes real delivery of repair feedback and real repair attempts, not successful recovery.

Analysis and source suggest concerns about compound inputs, but that is the model's attempted diagnosis, not an established runtime cause. The first callable also captures question one in its definition; later reuse would require reviewing that dependency. The trial did not reach question two, so no actual later-order isolation result is claimed for it.

## Evidence-driven harness changes

The first repair context contained 196,940 characters; the second contained 291,698, compared with 25,788 initially. The same source, accepted forms and attempt history appeared repeatedly. The revised Lain context uses a generic path projection to render status, failed source, relevant receipt metadata, accepted forms, actual outputs, immediate edges, supports and remaining budget. It omits repeated nested attempt histories and binding overviews. Full records remain inspectable.

A second wiring issue was separate from prompt size: temporary phase views exposed feedback in the description, while generated source inherited the original parent. A name visible in the rendered phase view was therefore not sufficient proof that admitted source could read the same diagnostic. Lain now publishes completed attempt feedback as identified events into a parent reducer, exposed as `repair-details`. The parent `inspection-feedback` alias in the print-shop environment reads that live value. Phase snapshots remain immutable and compact. Success updates diagnostic status to `:resolved`.

The regression admits source that actually reads `repair-details` through `inspect:cell` in the same lexical child. It observes the preceding failed repair, then checks the final full attempt history and resolved status. It also checks that compact alerts omit recursive history, expose immediate edge/value/input/output data, and preserve actual timestamps and accepted forms. Seven recovery tests pass 72 assertions.

The language introduction also documents core `if` with a generic conditional example, alongside `select`. Neither example supplies a pricing implementation. The main runtime, existing model and completed-response compiler remain unchanged; native additions only project data and inspect immediate edges. Phase selection, feedback publication, retries and termination stay in Lain.

## Controlled comparisons

The saved real implementation is also compiled independently through high-level source admission and the public session API. [`tools/audit_printshop_generated.clj`](../tools/audit_printshop_generated.clj) compares whole-file and sequential-form admission, producer extensions and explicit public cell messages, inherited lexical targets with TTMS aliases, and managed-owner admission. No domain cells are pre-seeded.

All eleven cases of that smaller reproduction compute the correct initial `212.76`. A diagnostic source-read error during editing was excluded; the final audit uses a frozen script copy and completes all cases. This includes the original `select` source; merely using `select`, sequential admission, public input messages, or an inherited alias is not sufficient to reproduce the live initial contradiction. The larger session interaction remains unresolved.

On quantity correction from 80 to 120, the smaller original-source variants lose the downstream tax/total result, while the explicitly labeled `select`-to-`if` counterfactual yields `277.56`. This is controlled diagnostic evidence and agrees with the earlier branch-reconvergence boundary. It is not model-generated repair or permission to patch the kernel. Eight additional simple scalar conditional/premise comparisons pass for both `select` and `if`.

## Full recovery control: 84/84

The separate `recovery-control` completed all five questions and **all 84 evaluator checks pass**. It deliberately begins with invalid source, retains the valid prefix, repairs through Lain, and invokes the same retained `quote-fn` for questions two through five. All 35 initial numerical fields match. Question/rule supports, order isolation, quantity correction, promotional-cap changes, withdrawal/restoration, unrelated-change invariance, source history, phase order and stable worker/session identity pass.

This is a new control on the unchanged runtime, distinct from the historical 84/84 run that used subsequently reverted kernel patches. It uses the recorded support-only publication contract, not an untested claim about every TTMS payload or state-aware source contract. It makes 16 controlled transport requests and zero provider calls. The owner closes cleanly. Nine additional [history/stability checks](../tools/verify_printshop_history.clj) pass: completed rounds, real nonempty replies containing the computed totals, ten exact ordered messages, each round prefix, explicit roles, environment-only system content, the exact earlier delivered messages in every request, and unchanged earlier values/supports as later questions are added. The existing phase/source integrity checks also pass.

Reproduction:

```sh
LAIN_EVIDENCE_DIR="$PWD/runtime/fresh-printshop-recovery-control" \
LAIN_OUTCOME_TIMEOUT_MS=660000 LAIN_PROPAGATION_TIMEOUT_MS=600000 \
  bash tools/run_printshop_runtime.sh recovery-control
```

## Second real-provider trial

The second fresh real-provider trial is recorded independently. The second real variant uses compact feedback, the live parent diagnostic binding and generic conditional examples. It allows longer local propagation and round observation deadlines; model/provider token settings are unchanged. The source manifest and local diff pin the exact variant used at launch. Because several context/wiring conditions changed, this is an exploratory continuation, not a controlled causal estimate of prompt compaction alone.

Its initial implementation response ended with `length` and contained prose. Admission rejected it before installing any forms. The model's first repair returned complete Lain source: a reusable quote base, a shared-rule wrapper, a question-one wrapper, connected breakdown ports and a premise-linked reply network. It used core `if`, computed `212.76`, satisfied required premise checks, and Lain advanced to question two in the same session. This is actual real-model recovery from a failed response format; it does not yet demonstrate repairing an already admitted contradictory network.

Question two then invokes the retained `quote-pricing-shared` and `reply-shared` callables with question-two wrappers, computes its quote, and advances to question three. Its actual analysis request contains the first question, the delivered `212.76` reply, and the second question in the history binding. The second trial then stopped at question-three analysis. The provider response was complete and valid EDN, but used `:status :ready` rather than a string. Every other required field was present with the expected type, and the selected operator and steps described direct reuse. The implementation-phase repair loop was not reached: the strict planning validator stopped the session as `:invalid-analysis`. This is a protocol failure, not a demonstrated failure of the quote computation.

The declared status contract is now a nonempty string or an EDN keyword, both natural labels. The validator and context agree; original provider records are not rewritten. Missing or numeric statuses remain invalid. The other planning prose fields remain required strings. Focused tests cover both accepted label forms and rejected alternatives. That failure required a fresh continuation because the second owner closed cleanly at its terminal policy outcome; it is not silently reopened or presented as one uninterrupted successful session. A controlled reference never substitutes for real-model acceptance. Full input-to-output causal provenance and durable reopening remain unfinished.


## Review commands

For a completed campaign directory:

```sh
clojure -M tools/verify_printshop_three_stage.clj runtime/autonomous-20261109/recovery-control
clojure -M tools/verify_printshop_history.clj runtime/autonomous-20261109/recovery-control
```

The history verifier requires all five completed rounds; it does not credit absent data as successful invariance. The older numerical evaluator prints checks for later stages even when a trial stops early. Those unexecuted checks in `live-1` are not separate observed numerical failures or successful isolation evidence.


## Third trial and remaining scope

The third fresh session uses the tested status-label contract and explicitly documents the measured changing-support limit of `select`, with core `if` as the tested alternative. The model remains free to construct its network; no quote implementation or numerical oracle is supplied. Provider settings remain unchanged. Its exact local source manifest and diff are under `live-3-status-label`.

Current verification after the label change: nine recovery tests / 79 assertions pass; runtime composition passed two tests / 15 assertions before that isolated label change; four Clojure files passed formatting and lint at that checkpoint. The final style check covers all six Clojure files changed in this campaign. The unchanged-runtime reference has 84/84 domain checks plus nine history/stability and eight phase/source integrity checks passing.

A Lain `:done` round or `:resolved` diagnostic means the declared outputs and premise requirements are computationally ready. Domain correctness is independently evaluated; these labels alone do not prove a correct formula. The parent still seeds the conversation reducer in this experiment. This campaign does not establish model construction of that history bootstrap, durable reopening, or full causal provenance. Malformed planning records still terminate the current policy; accepting a keyword status is a declared type-contract correction, not general planning repair.


Third-trial result: the first three questions completed through nine real calls, with totals `212.76`, `533.30`, and `1199.88`. The first two analysis/generalization pairs all use `:status :ready`; the widened declared contract accepts these original records directly. The model defines a fifteen-parameter `quote-pricing` network with a captured shared-rule premise and constructs separate question-specific applications. There were no implementation repair requests. The session stopped after its eleventh call: question-four generalization returned a complete response with prose before the EDN map. The native reader selected its leading `0`, so the planning validator correctly rejected the non-map. The owner closed cleanly. Three correct answers are not five-round acceptance.

## Generation-budget diagnosis and fourth trial

The malformed generalization response's thinking field tokenizes to 2,047 tokens through the same local provider's tokenizer, against a configured thinking limit of 2,048. A clean generalization comparison has 1,540 thinking tokens. This is consistent with the thinking cutoff leaving unfinished reasoning in the content field; it is evidence for a configuration boundary, not proof of a general provider defect. The response text is preserved unchanged. We do not strip a prelude or extract a preferred map from the answer.

The probe now accepts `LAIN_MODEL_MAX_TOKENS` and `LAIN_MODEL_THINKING_TOKENS`, retaining the earlier 4,096/2,048 defaults for reproduction. It records actual provider options beside the evidence and validates them before opening the owner. The fourth fresh trial uses 8,192 total tokens / 4,096 thinking tokens. All environment instructions, Lain phase logic, compiler and kernel source match trial three. This changes generation limits, not semantic task context or source admission requirements. That fourth variant completed successfully; its results are below.

The complete control passes the history verifier; the earlier incomplete two-round real trial is deliberately rejected by the same verifier, confirming that missing later observations cannot count as full acceptance.


## Final real-provider acceptance

The five delivered totals are **212.76, 533.30, 1199.88, 249.91 and 1405.30**. All 35 initial breakdown fields match the independent oracle. Generated code computes from the existing input cells, not fixed answer literals.

The generated interface is a five-parameter `quote-pricing` network over quantity, rush, membership, discount cap and the pricing struct. It captures `shared-rule`, returns the seven breakdown fields plus a computed reply, and publishes each question's results through its own premise-wrapped identity operator. The same `quote-pricing` declaration, reference and captured environment are retained for four subsequent runtime applications connected to the corresponding outputs. The host records these facts; it neither selects the replies nor supplies a pricing implementation to the real model.

After all five replies, the existing network passes these changes without another model request:

- Correcting quantity one to 120 updates its total to 277.56.
- Removing/restoring the promotional cap updates order five to 1443.10 / 1405.30, leaving orders one through four unchanged.
- Withdrawing/restoring quantity two makes its result unknown / 533.30.
- Withdrawing/restoring question one makes its result unknown / 277.56.
- An unrelated delivery-note change preserves every quote's value and support.

The provider count stays at 15 throughout. Every published breakdown and reply has the required question/rule support; no result borrows a foreign order's support. All ten ordered user/assistant messages match actual input questions and delivered reply ports. Each later request contains the exact preceding messages through the Lain history binding. Generated source and provider thinking are not counted as conversation replies. Later questions preserve earlier live values and supports, not only their textual history. The observed worker/session/child identities remain stable through all snapshots; the owner closes cleanly at the end.

Final local evidence: `runtime/autonomous-20261109/live-4-generation-budget/`. Its `acceptance.edn` records 84 domain checks, nine history/stability checks, eight integrity checks, four reuses, 15 provider calls, ten delivered messages and zero repair attempts. Original source, raw provider responses/thinking, request contexts, source receipts, graphs, snapshots, source hashes and recorded generation options remain local. The runtime patch is empty. Only implementation, tests/tools and this readable record are published.

Reproduction with fresh local evidence:

```sh
LAIN_EVIDENCE_DIR="$PWD/runtime/fresh-printshop-live" \
LAIN_MODEL_MAX_TOKENS=8192 LAIN_MODEL_THINKING_TOKENS=4096 \
LAIN_OUTCOME_TIMEOUT_MS=1800000 LAIN_PROPAGATION_TIMEOUT_MS=600000 \
  bash tools/run_printshop_runtime.sh live
clojure -M tools/verify_printshop_three_stage.clj runtime/fresh-printshop-live
clojure -M tools/verify_printshop_history.clj runtime/fresh-printshop-live
clojure -M tools/verify_printshop_acceptance.clj runtime/fresh-printshop-live
```

The numerical evaluator records its full report even when a real trial fails; check every `:pass` value in `evaluation.edn` rather than using its process exit alone. The history verifier asserts complete five-round acceptance. The [combined acceptance verifier](../tools/verify_printshop_acceptance.clj) requires all domain, history and integrity checks plus four actual reuses to pass.

This establishes a working real-model construction/reuse condition and live dependency maintenance. It does **not** establish reliable repair of an already admitted contradictory network: the first real trial failed that test. Recovery from the truncated implementation did succeed in trial two, while malformed planning records still stop the current policy. A future planning-repair loop should remain in Lain. Source validity/withdrawal for replacing faulty admitted computations, full causal provenance, agent construction of the history bootstrap, and durable reopening remain separate unfinished work.

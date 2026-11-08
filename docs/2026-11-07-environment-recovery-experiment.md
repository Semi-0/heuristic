# Environment-visible recovery in the print-shop experiment

Historical report: the implementation described below used host-selected phases and retries. The current architecture and verification status are in [the subsequent orchestration audit](2026-11-07-single-lain-orchestrator-audit.md). Old scheduler and request helper sources are preserved under `runtime/orchestration-audit-20261107/before/`.


The previous fresh trial stopped on malformed source without a subsequent model
request. This condition gives the model the actual failure observations and a
bounded opportunity to correct its own network in the same lexical child.

## Preserved architecture

`soul-refined.lain`, `model-refined.lain` and `lib/compiler.lain` are unchanged.
The runtime still describes its environment input, connects the existing model,
creates one child, and admits completed responses through the existing compiler.
No compiler backend, deployed service, source rewriting or host pricing solution
is introduced.

The runnable print-shop entry connects the runtime's compilation port to the
seed's `admission-feedback` cell. The seed exposes the live receipt independently as `admission-result`.
`inspection-feedback` aliases the stable `recovery-state` snapshot, which contains
observed failures, admitted-prefix receipts, child binding states, the current
question's required output values and remaining budget. `agent-observations`
is a separate model-writable port. The environment also supplies `inspect:cell`
and a textual example of filling an undefined input in a partially evaluated
network. Examples are documentation, not installed running solutions.

`experiments/request-once.lain` defines the generic request construction.
Print-shop phase and correction sites use one shared environment description.
Sites are instantiated on demand through the session extension API, using only
the generic Lain callable, the existing worker/view and a fresh identity. Each site freezes one request with a unique identity. Its session extension identity
is stable, so replaying the same site does not enqueue another provider call. Request
text is entirely constructed in Lain; the transport adds no diagnostic prompt,
source correction, history or answer.

The experiment scheduler in `src/lain_harness/experiment_recovery.clj` waits for
actual admission results, observes required outputs, publishes feedback and raises
the next Lain trigger. It permits at most two corrections per question. Rejected
source and admitted source leaving required output ports unknown or contradictory
can trigger correction. The host supplies observed states, not expected prices.
An incorrect but fully known result is left to the independent evaluator; it does
not produce an oracle-driven repair prompt. Malformed analysis/generalization
records and infrastructure failures remain outside this implementation recovery
loop.

Earlier accepted forms remain installed. Every correction receives the failed
source and receipts for all prior accepted prefixes in that question. The model
is instructed to reference existing names and submit the missing/corrected forms.
The history preserves all written implementation responses, including rejected
ones. Per-round source dataflow analysis uses only actual admitted forms across
attempts; the original provider responses remain separately recorded unchanged.

## Focused verification

`LAIN_OFFLINE_UUID=1 tools/test_module.sh experiment-recovery` passed two tests
with twenty assertions. A controlled first response admits `retained` and a
network depending on unknown `missing`, then fails on an extra parenthesis.
The first correction is syntactically valid but leaves the network unfinished.
The second supplies the missing input and the earlier result becomes 11. Checks
verify failure visibility in the next request, retained prefix, session/child and
worker continuity, and no duplicate provider call when the same request extension is replayed. Another control
exhausts its budget and verifies that final failure observations remain published.
These tests use the ordinary compiler defaults, not an increased propagation
allowance. Their synthetic outputs are not evidence of model recovery ability.
Evidence: `runtime/experiment-recovery-tests-20261107/`.

## Full conditions and reproduction

```sh
LAIN_EVIDENCE_DIR="$PWD/runtime/recovery-control-new" tools/run_printshop_runtime.sh recovery-control
LAIN_EVIDENCE_DIR="$PWD/runtime/recovery-live-new" tools/run_printshop_runtime.sh live
clojure -M tools/verify_printshop_three_stage.clj runtime/recovery-live-new
```

The full control deliberately returns `(define control-retained 7))` for the
first implementation and uses the established known quote function on correction.
The live condition uses fresh real-provider responses throughout. Neither
condition retries transport calls or strips prose from responses. Both retain the
five print-shop questions, three planning/implementation phases, provider settings
and explicit local 300000-step / 180-second propagation allowance. Correction
requests are additional calls. A final failure is published even at budget
exhaustion, then the session is closed after recording evidence.

The focused tests also use the implementation-response gate from the full
experiment: changing the requested response identity withdraws the old live
admission result, while the published failure snapshot must remain available.

The integrity checker accounts for extra correction phases and checks that each
correction context contains the preceding failed source and receipt information.
The numerical, premise, mutation and actual closure-reuse evaluator is retained;
its phase-count and source-accounting checks now include correction attempts.

## Setup development failure

The initial eager setup predeclared all phase and correction request networks.
Both the control and live-labelled runs exhausted the 180-second local propagation
allowance while compiling request sites, before any provider request. Evidence is
retained under `runtime/printshop-recovery-control-20261107/` and
`runtime/printshop-recovery-live-20261107/`; these are setup failures, not model
trials. Request sites were changed to on-demand instantiation of the same generic
Lain callable. The host still supplies no request text or task-specific advice.
The runtime and its propagation defaults were not changed.

The first on-demand full control reached its deliberately rejected response, then
caught an incorrect call arity in the host binding-overview adapter. This was
corrected to the existing `overview` API (`network` and `[environment selection]`).
Its evidence remains under `runtime/printshop-recovery-control-demand-20261107/`.
The companion live-labelled setup was stopped before any provider request and is
marked interrupted under `runtime/printshop-recovery-live-demand-20261107/`.

A subsequent control still exhausted setup at the runtime assembly under the same
180-second allowance; its companion live-labelled setup also exhausted setup with zero
provider calls. They are retained under `runtime/printshop-recovery-control-final-20261107/`
and `runtime/printshop-recovery-live-final-20261107/`. The seed's reference-binding
construction was then flattened to direct native `extend-env`/`env:reference-slot`
operations in one let chain. Binding names, order and referenced input cells are
preserved; the eliminated work was constructing a higher-order wrapper per seed
slot. This changes only seeding implementation, not the main orchestration,
provider settings, semantic capabilities or recovery policy.

## Feedback visibility correction

The flattened-seed control completed all five questions, with 35 initial numerical
passes, four actual reuses and the two established baseline mutation failures.
However, its context-integrity check failed: after changing implementation-id, the
live admission result could become unknown. Combining that result and the stored
failure in one data:record made the whole inspection-feedback record pending.
The host had recorded the failure, but the next model request did not contain it.
This condition is development evidence, not successful model-visible recovery.
It is retained under `runtime/printshop-recovery-control-flat-20261107/`.

The live companion was interrupted after three requests and two responses, before
an implementation was received; it is retained under
`runtime/printshop-recovery-live-flat-20261107/`. No successful live outcome is
claimed for that interrupted run.

The corrected seed exposes the stable failure snapshot directly as
inspection-feedback, independent of the transient live admission-result binding.
The focused fixture now reproduces the same response gating and checks failure
visibility after the requested response ID changes. The record includes the
actual prior admission result, source, successful-prefix receipts, current child
bindings/output observations and remaining budget. This is a snapshot of facts,
not a replacement host prompt or a host-authored source correction.

## Verified full control

The stable-snapshot control completed under
`runtime/printshop-recovery-control-snapshot-20261107/`. It made sixteen synthetic
calls (fifteen ordinary phases plus one correction), retained the deliberately
admitted prefix, and completed all five quotes. All 35 initial numerical fields
matched, and the established quote closure was reused in four later questions.
The two baseline quantity-correction/question-restoration failures remain.

Every integrity check passed: the actual inspection-feedback section of the
correction request contains the parser diagnostic, exact failed source, successful
prefix receipts and remaining budget; histories contain only written implementation
responses; plans are unchanged; the same session, child and worker are observed
through failure, correction and subsequent questions. See `summary.edn`,
`integrity.edn` and `correction-context-check.json`. The latter verifies the
actual request text, not merely a host-side failure record. This controlled
response sequence establishes the feedback plumbing, not real-model repair skill.

## Fresh real-model result

The fresh condition under `runtime/printshop-recovery-live-snapshot-20261107/`
completed all five questions with seventeen actual provider calls: fifteen ordinary
phase calls and two corrections on question one. All 35 initial numerical fields
matched the independent oracle. Totals were 212.76, 533.30, 1199.88, 249.91 and
1405.30. The host did not edit, strip prose from, or replace any provider source.

The first implementation contained only three tail forms: connections from
`quote-result` to quote-1's tax and total ports, and the text reply connection.
It never supplied the computation producing quote-result. The source was admitted,
but all eight required output ports remained unknown. The environment snapshot
reported admitted source with unresolved outputs and the surviving bindings.

The first corrective response identified the missing computation, but prefixed its
code with English prose. Sequential admission accepted three bare symbol forms
(`Looking`, `at`, `the`) before rejecting the next invalid form. Those harmless,
unresolved references remain in the child; no rollback or manual cleanup occurred.

The second corrective response supplied 22 valid Lain forms: ten calculations,
seven writes to quote-result's slots, and five missing connections to quote-1.
The original three downstream connections were retained. Comparing admitted ASTs
found zero original forms replayed in the successful correction. The existing
partial network then produced all seven fields and its text reply, with total
212.76. This demonstrates real-model repair of a partially evaluated network,
beyond the synthetic plumbing tests.

Questions two and three used separate calculations. On question four the model
defined a four-parameter `compute-quote` network; question five invoked that same
closure for the promotional order. The independent evaluator confirmed retained
reference, declaration and captured-environment identity plus a new invocation on
the answer path. This is one actual callable reuse, in question five; copying
similar source for earlier questions is not credited as callable reuse.

All integrity checks passed, including the actual inspection-feedback sections
of both correction requests, unchanged planning records, implementation-only code
history, correction budget and stable observed worker/session/child throughout
failed and successful attempts. Seven implementation responses are retained,
including failed/incomplete code; five successful question outcomes are separate.

Dependency acceptance remains unfinished: all five question-support checks and
all five shared-rule support checks failed. Quantity correction, quantity
withdrawal, question withdrawal and question restoration also failed. Recovery
was driven by admission and current-output readiness, not oracle answers or these
later counterfactual mutation checks. Correct initial prices and successful repair
do not establish correct premise maintenance. The controlled quote function still
has its two previously recorded baseline mutation failures; no backend fix is
claimed.

See `summary.edn` for counts, attempt statuses, retained-form comparison, reuse and
remaining failures; `integrity.edn` for request/identity checks; `context-4.txt` and
`context-5.txt` for actual model-visible failure observations; and
`generated-1-repair-2.lain` for the unmodified successful repair.

## Final implementation verification

The focused module passed two tests and twenty assertions with the full
implementation-response gate and the stable snapshot. The main runtime, original
model, completed-response compiler adapter and native admission implementation
match their committed versions byte for byte; hashes are recorded in
`runtime/experiment-recovery-tests-20261107/architecture.json`. The deployed service
was not changed or restarted. Default compiler budgets are unchanged. Large
experiments retain their explicitly documented local resource allowance.

## Subsequent orchestration audit

The real recovery run above used host-selected phases and retries. It demonstrates model repair within one worker and child, but does not establish Lain-only orchestration. The subsequent correction moves that policy into Lain and enriches native admission receipts with retained forms; consequently the earlier byte-for-byte admission-adapter claim applies only to the historical run. See [the orchestration audit](2026-11-07-single-lain-orchestrator-audit.md) for verification status and the fourteen-check breakdown.

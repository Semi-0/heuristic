# Three-stage generalization on the print-shop questions

This condition applies the inventory experiment's three-stage methodology to the
original five print-shop questions: analyze the problem, design or select a
computational interface, then implement and apply it. Generalization remains
optional; a named network and model-written tests are not required.

The task context supplies the original progressive pricing rules, rush/member
conditions, discount caps, setup, delivery threshold, tax and rounding convention.
Inputs are the existing order-N and pricing structs. The seven quote output slots
and text ports remain unchanged. No solved pricing implementation or oracle total
is included in a real-model request.

The record protocol now permits :compose. A selected candidate's availability is
recorded, not used to block implementation merely because no single callable was
named. Decisions such as :reuse with :operator "none" likewise remain observable
without being credited as actual callable reuse. The independent evaluator checks
retained declaration/reference/captured-environment identity, new runtime
invocations and source dataflow to the answer. Primitive composition, newly
constructed closures and actual reuse are distinct observations.

One exploratory real-model session was completed: five questions and up to fifteen
calls, with no repair prompts, source rewriting, repeated requests or fallback.
Only implementation responses enter source admission/history. Analysis and
interface records are supplied unchanged through environment bindings. A malformed
planning record or rejected source still stops the session; lack of a selected
callable alone does not. Model self-check ports remain optional.

## Controls and limits

The known quote function is a separate synthetic control, never supplied to the
real model. Its second analysis deliberately chooses :compose and no named
callable, exercising the nonblocking selection behavior. Later controlled
implementations invoke the same verified function. The evaluator retains the
two previously established quantity-correction/question-restoration failures
rather than claiming all runtime mutation behavior is correct.

A development control caught an adapter atom-name typo before any provider call;
its log remains under runtime/printshop-three-stage-control-20261107. The final
controlled condition is retained separately. Local propagation settings remain
300000 steps / 180 seconds, and provider settings match previous conditions.
No service restart or deployment is involved.

This is an exploratory comparison, not a randomized complexity study. Task,
capabilities and structured inputs differ from inventory, and the composition
record protocol has also been corrected. Any observed change cannot be attributed
solely to the extra complexity of pricing rules.

## Reproduction

```sh
LAIN_EVIDENCE_DIR="$PWD/runtime/print-three-control-new" tools/run_printshop_three_stage.sh control
LAIN_EVIDENCE_DIR="$PWD/runtime/print-three-live-new" tools/run_printshop_three_stage.sh live
clojure -M tools/verify_printshop_three_stage.clj runtime/print-three-live-new
```

Use fresh evidence directories and allow access to the configured localhost model
endpoint. The runner uses an isolated harness export and records contexts, original
responses/thinking, source, receipts, graphs and snapshots. The configured companion
runtime revision is recorded; local checkouts and deployed services are preserved.

- [Parent and context](../examples/lain/printshop-three-stage.lain)
- [Probe](../tools/probe_printshop_three_stage.clj)
- [Evaluator](../tools/evaluate_printshop_three_stage.clj)

## Observed result

The real session completed all fifteen calls in one worker/session. All 35 initial
numerical quote fields matched the independent oracle. The five totals were
212.76, 533.30, 1199.88, 249.91 and 1405.30. In its first implementation the model
created `compute-quote`, a four-argument network over quantity, rush status,
membership and discount cap. The next four implementations invoked that same
closure. The evaluator confirmed retained declaration, reference and captured
environment identity, with new invocations connected to each answer: 4/4 reuse.

The first interface record described primitive composition while also identifying
a useful parameterized network; the implementation chose to define that network.
The source and runtime evidence establish reuse more strongly than that planning
label. Later interface records explicitly selected `compute-quote`.

Dependency acceptance remains incomplete. All five results lacked the expected
question premise and shared-rule premise. Quantity correction and subsequent
withdrawal/restoration checks produced contradictions rather than the expected
updated or unknown values. Four mutation checks failed in addition to the ten
premise checks. The controlled reference network also has two established
quantity-correction/question-restoration failures (unknown results), so the real
condition's failures need comparison against that baseline rather than attribution
to a single cause. Correct initial answers and actual closure reuse do not establish
correct premise maintenance.

The integrity checker confirmed phase order, implementation-only source history,
unchanged planning records, and stable observed worker/session identities.
Original evidence: `runtime/printshop-three-stage-live-20261107/`; control:
`runtime/printshop-three-stage-control-final-20261107/`. These are the isolated
pre-refactor orchestration condition. Saved-response replays through the restored
runtime are recorded separately and do not count as new provider trials.

## Continuation through the restored runtime

After separating the reusable runtime from seed and trigger modules, the saved
responses replayed successfully with the same 35 initial numerical passes, four
closure reuses and fourteen premise/mutation failures. A fresh provider trial then
stopped on the first implementation: content mixed prose with source and began
with an extra closing parenthesis. Sequential admission retained the leading
expression, then rejected the remainder before the proposed reusable network.
There were three provider calls and no completed quote. No source was repaired
or substituted. Details and evidence links are in
[Clear runtime composition](2026-11-07-clear-runtime-composition.md#fresh-provider-trial-after-separation).

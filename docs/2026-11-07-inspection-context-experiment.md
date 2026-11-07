# Inspection examples and explicit response format

Three fresh real-model sessions tested the optional partial-network methodology
with worked inspection examples and contrasting valid/invalid response formats.
Each session made two provider requests in one persistent worker and lexical child.
There were no repair prompts, retries, substituted answers or extra model calls
for corrections and withdrawals. Corresponding responses were byte-identical.

The questions, inputs, provider configuration and evaluator match the earlier
partial-inventory experiment. The context adds finite environment inspection,
cell edge/value/input/output inspection, native error-layer inspection, the latest
admission status and an explicit feedback port. Documentation demonstrates a
computation waiting for data, an invocation waiting for a callable, and correct
output direction. Examples are literal documentation, not installed solutions.
The dedicated undefined-closure propagator mentioned in discussion has not been
located: the exposed existing operator is environment:overview, which reports
visible unknown bindings without claiming every unknown is a missing closure.

## Results

| Check | Outcome |
|---|---|
| Raw forms admitted, no fences or envelopes | 3/3 |
| Correct output connection direction | 3/3 |
| Total 240 while damage is unknown | 3/3 |
| Publication alone produces usable 216, margin 16, fulfillment true | 3/3 |
| Second question produces usable 342, margin -8, fulfillment false | 3/3 |
| Damage correction produces usable 351, margin 1, fulfillment true | 3/3 |
| Called any advertised inspection operator | 0/3 |
| Reused an earlier callable in the second answer | 0/3 |
| Numeric conclusions include own question and shared-rule support | 0/3 |
| Numeric conclusions withdraw with their question | 0/3 |
| Numeric shipment-2 results withdraw with damage | 0/3 |
| First reply updates when missing damage arrives | 0/3 |
| Shipment independence and unrelated-note invariance | 3/3 |

Both rounds defined direct arithmetic rather than a reusable inventory callable.
The first round created an answer-1 callable for a static explanatory string,
wrapped with question-1 support. The second round created a separate answer-2
callable and did not invoke answer-1. Neither response called inspect:cell,
inspect:premises, inspect:errors, or environment:overview. Thus inspection
availability did not produce inspection behavior in these sessions.

Question premises were attached to reply callables, not numeric conclusions.
The numeric support sets contained the mathematical inputs but omitted question
and shared-rule premises. Withdrawing question 1 left its numeric results intact.

Text initially described the missing input correctly. It remained static after
publication. The second reply interpolated changing numbers but embedded the
claim that fulfillment was impossible. After correction it reported 351 usable,
margin 1, and also said the order could not be fulfilled, with a shortage of -1.
Correct numeric observations therefore do not establish correct reactive replies.

After shipment-2 damage withdrawal, numeric outputs retained 351, 1 and true,
while its text reply became unknown. This repeats the direct-arithmetic withdrawal
failure observed in earlier conditions. Its runtime cause is not established by
this experiment; it must not be attributed solely to model strategy. No native
runtime error repair was attempted or demonstrated. The context offers inspection
capabilities; it does not automatically publish the evaluator's failed checks.

## Verification and limits

Controlled preview sessions checked partial completion to 12, late callable
completion to 5, feedback value 5, directory changes from unknown to known,
and empty native error evidence on a healthy cell. These are controlled operator
checks, not model successes. The previously established independent inventory
evaluator was retained unchanged.

Live integrity checks confirm fresh sessions, stable session/child identities,
one worker per trial, exactly six provider requests, retained source history,
and identical corresponding source. Every first context includes the new format
and inspection examples and known inspection capabilities. Mutation checks cause
no additional requests. Sessions closed normally.

This condition changes several documentation/interface elements together. Three
identical trials at temperature zero cannot establish a causal improvement from
any one example, or general model reliability. Formatting also worked in the
original partial-network trials; these results do not show a new formatting gain.

## Files and reproduction

- Parent: [inspection-inventory.lain](../examples/lain/inspection-inventory.lain).
- Literal guide: [inspection-context.lain](../lib/inspection-context.lain).
- Native error adapter: [inspection_experiment.clj](../src/lain_harness/inspection_experiment.clj).
- Runner: [run_inspection_inventory.sh](../tools/run_inspection_inventory.sh).
- Evidence: [evaluation log](../runtime/inspection-inventory-live-20261107/evaluation.log),
  [integrity](../runtime/inspection-inventory-live-20261107/integrity.edn),
  [inspection usage](../runtime/inspection-inventory-live-20261107/inspection-usage.edn),
  [actual first context](../runtime/inspection-inventory-live-20261107/trial-1/context-1.txt),
  [first source](../runtime/inspection-inventory-live-20261107/trial-1/generated-1.lain),
  [second source](../runtime/inspection-inventory-live-20261107/trial-1/generated-2.lain).

```sh
LAIN_EVIDENCE_DIR="$PWD/runtime/inspection-live-new" tools/run_inspection_inventory.sh live
```

Use a fresh evidence directory. The runner overlays experiment files into an
isolated committed harness export and records source snapshots and revisions.
LAIN_INSPECTION=1 registers the optional native adapter in the existing probe;
the earlier experiment's default path is unchanged. Existing local model edits
and deployed services remain untouched. The companion runtime contains local
changes, recorded with the evidence; this is not a clean-upstream compatibility
claim.

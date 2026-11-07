# Explicit analysis, look-back and implementation

This condition keeps the two inventory questions and inspection/example context,
but adds an explicit analysis before each implementation. The agent must identify
knowns, unknowns, units, relationships, earlier candidates and their execution
status, choose reuse/adapt/construct, and propose construction and verification
steps. Reuse is considered explicitly but is not mandatory.

Analysis returns a structured EDN record. Implementation receives that record
unchanged in an environment binding. Only implementation responses enter source
admission and generated-code history. The phase gate checks required fields and,
for reuse/adapt, that the selected operator is actually callable. It does not
validate the mathematical plan or prove that the model inspected every candidate.
The questions and expected values are unchanged; no oracle result or repaired
formula is supplied by the host. There are no repair prompts or retries.

## Results

Three fresh real-model sessions completed four requests each, twelve total.
Corresponding implementation sources were identical. All sources were admitted.

| Outcome | Trials |
|---|---:|
| Complete structured analysis and implementation phases | 3/3 |
| Second analysis explicitly reviews first-round computations | 3/3 |
| Initial numerical observations correct | 3/3 |
| Partial network completes after damage publication, without inference | 3/3 |
| Numerical correction to 351 usable, margin 1, fulfillment true | 3/3 |
| Creates a parameterized inventory callable | 0/3 |
| Reuses an earlier callable | 0/3 |
| Calls an inspection operator in generated source | 0/3 |
| Generates executable independent checks | 0/3 |
| Numeric conclusions carry required question and shared-rule support | 0/3 |
| Numeric conclusions withdraw with their question | 0/3 |
| Numeric shipment-2 results withdraw with damage | 0/3 |

The first analysis chooses construction and proposes boundary checks, including
zero damage and 20% damage. Implementation constructs direct arithmetic on the
shipment's input cells. It does not construct a parameterized callable or execute
those proposed checks.

The second analysis identifies total-1, usable-1, margin-1 and fulfillment-1 as
relevant earlier computations, but correctly notes they are wired to shipment 1.
It chooses construction with operator "none", then implements the same relations
on shipment 2's inputs. This is evidence of an explicit look-back decision, not
reuse of an existing computation. Reusing shipment-1 result cells directly would
have produced the wrong answer. The remaining abstraction question is why the
agent neither constructs a general operator initially nor generalizes one later.

Both implementations follow the high-level construction decision. Verification
and dependency intentions are not fully realized: there are no generated checks,
question/rule closures, inspection calls or diagnostic feedback writes. Structured
planning makes these omissions reviewable; it does not enforce semantic validity.

The first reply remains a static explanation of missing damage after damage
arrives. The second reply interpolates the corrected values but retains a hardcoded
claim that the order cannot be fulfilled. Numeric correctness is separate from
reactive text correctness.

Damage withdrawal leaves numeric results available, reproducing the failure seen
in direct-arithmetic forms in the earlier inspection condition. Its runtime cause
is not established here. The known reusable control passes this transition, so
this experiment does not justify blaming either every TTMS computation or only
the model. Question withdrawal failures are also recorded separately from
independence: shipment 2 remains independent of shipment 1, and unrelated-note
changes preserve values/supports.

## Controlled verification and resource limit

The first controlled setup exhausted the 30-second propagation timeout while
installing the parent, before any provider request. Its record is retained under
runtime/planned-inventory-control-20261107. The local allowance was increased to
300000 steps and 180 seconds, matching the earlier strict print-shop experiment.
Provider settings and timeout were unchanged. This resource change is part of the
condition and must be included when interpreting comparisons.

The subsequent controlled run passed every evaluator check, including phase
ordering, implementation-only history, actual callable reuse, exact support sets,
correction, question withdrawal, damage withdrawal, recovery and independence.
The control's static first reply does not become reactive; that is a separate
reported outcome rather than a passing evaluator assertion. Controlled source
and controlled analyses are never included in live contexts.

Formatting/lint checks passed with zero warnings and errors. Live integrity
verification checks four requests per session, phase ordering, unchanged plans in
implementation requests, stable identities, one worker per session and retained
implementation-only source history. All sessions close normally. Corrections and
withdrawals make no further model requests.

## Evidence and reproduction

- [Parent](../examples/lain/planned-inventory.lain)
- [Probe and phase gate](../tools/probe_planned_inventory.clj)
- [Evaluator](../tools/evaluate_planned_inventory.clj)
- [Live evaluation](../runtime/planned-inventory-live-20261107/evaluation.log)
- [Integrity](../runtime/planned-inventory-live-20261107/integrity.edn)
- [First analysis](../runtime/planned-inventory-live-20261107/trial-1/analysis-1.edn)
- [Second analysis](../runtime/planned-inventory-live-20261107/trial-1/analysis-2.edn)
- [First implementation](../runtime/planned-inventory-live-20261107/trial-1/generated-1.lain)
- [Second implementation](../runtime/planned-inventory-live-20261107/trial-1/generated-2.lain)
- [Passing control](../runtime/planned-inventory-control-2-20261107/evaluation.log)

```sh
LAIN_EVIDENCE_DIR="$PWD/runtime/planned-control-new" tools/run_planned_inventory.sh controls
LAIN_EVIDENCE_DIR="$PWD/runtime/planned-live-new" tools/run_planned_inventory.sh live
clojure -M tools/verify_planned_inventory.clj runtime/planned-live-new
```

Each run uses an isolated harness export and the configured companion runtime;
local edits and deployed services are preserved. Evidence retains the actual
contexts, separate provider thinking, analyses, unchanged implementation source,
admission receipts, identities, graphs, supports and mutation snapshots.

The comparison is exploratory: this condition changes procedure and number of
model calls together. Three deterministic trials do not establish a causal
improvement or general reliability. Relative to optional guidance, the observable
gain is an explicit, sensible review of earlier shipment-specific computations.
It did not produce computational reuse or better final correctness in these trials.

# Analysis, interface generalization, and implementation

This condition adds an explicit computational-interface stage to the two inventory
questions. Model-written tests are not required; the host evaluates computation,
identity and support behavior independently.

For each question:

1. Analysis identifies knowns, unknowns, units, relationships and existing operators.
2. Generalization identifies the problem class, variable parameters, captured
   assumptions and proposed interface. It may reuse, adapt or construct an operator,
   or explain why a new callable is unnecessary.
3. Implementation receives both records unchanged, constructs or invokes the
   chosen interface, and connects the current answer to the existing output ports.

All instructions, records and capabilities are ordinary environment bindings.
Planning responses are retained separately and never admitted as source.
Generated implementation source alone enters the child and code-history reducer.
The questions, observation ports, input mutations and provider settings are the
same as the prior planned-inventory condition. A complete session would make six
requests, with no extra inference for input corrections or withdrawals.

## Host validation and controls

The first development attempt incorrectly checked the provisional analysis
selection as if it were final. It stopped at analysis-2 when that record selected
reuse but named no callable. The attempted run and interrupted next trial remain
under runtime/three-stage-inventory-live-20261107; they are not pooled with the
fresh final condition.

The corrected host checks candidate existence only after GENERALIZATION.
Its controlled source deliberately returns a provisional reuse/none analysis,
then selects the real stock1 callable in the interface stage. All six controlled
requests and all evaluator checks pass: preserved callable identity, later
invocation connected to the answer, numeric results, exact support sets,
correction, question/damage withdrawal, recovery and independence. This is an
operator/phase control, not model acceptance.

The final interface contract still requires a named callable for :reuse or :adapt.
:construct with :operator "none" is allowed for direct expressions. This distinction
matters when interpreting a plan that calls primitive composition "reuse" without
selecting a single callable. Such a plan can be mathematically sensible yet fail
the record protocol. That failure does not show inability to use primitive operators.

## Reproduction

```sh
LAIN_EVIDENCE_DIR="$PWD/runtime/three-stage-control-new" tools/run_three_stage_inventory.sh controls
LAIN_EVIDENCE_DIR="$PWD/runtime/three-stage-live-new" tools/run_three_stage_inventory.sh live
clojure -M tools/verify_three_stage_inventory.clj runtime/three-stage-live-new
```

Use fresh directories and a process permitted to reach the configured localhost
provider. The runner exports committed harness sources into isolation and overlays
the new parent, probe and evaluator. No deployed service is changed or restarted.

- [Parent context](../examples/lain/three-stage-inventory.lain)
- [Probe and phase handling](../tools/probe_three_stage_inventory.clj)
- [Evaluator](../tools/evaluate_three_stage_inventory.clj)
- [Final controlled evaluation](../runtime/three-stage-inventory-control-final-20261107/evaluation.log)

## Results from the corrected three-session condition

The three fresh sessions made fifteen provider requests, five each. Corresponding
interface records and first implementation sources were byte-identical. All
observed session/child and worker identities remained stable, source history
contained implementation responses only, and both unchanged planning records
were present in each reached implementation request. Sessions closed normally.

| Observation | Trials |
|---|---:|
| Identifies the shipment-fulfillment problem class | 3/3 |
| Identifies all four shipment-varying inputs | 3/3 |
| Explicitly considers and declines a named wrapper | 3/3 |
| Defines a new network closure in the first implementation | 0/3 |
| Computes total 240 before damage is known | 3/3 |
| Existing network completes to 216 usable, margin 16, true after damage publication | 3/3 |
| Reaches the second generalization record | 3/3 |
| Completes the second implementation | 0/3: stopped by the final-record contract |
| Demonstrates reuse of a learned callable | Not established |

The model's first interface decision identifies boxes, items per box, damage
percentage and order quantity as variable parameters. It explicitly argues that
four direct expressions are simpler than a named wrapper for this small problem.
Implementation follows that choice; no network definition is emitted. This is an
observed design decision, not evidence that the model cannot recognize the class
or use generic operators.

For question 2 it again proposes direct composition of existing arithmetic
operators, calls that reuse, and names no single selected callable (:operator
"none"). The structured record uses :decision :reuse, conflicting with the
interface's named-callable contract. The host therefore stops before the second
implementation. The first implementation and its results remain admitted.

This limitation must not be hidden behind a generic "reuse failed" label. The
model is describing reuse of primitive operators, a behavior earlier arithmetic
experiments already established. The current host record represents a selected
callable, not a composition of several primitives. The result does not establish
that its proposed second arithmetic answer would fail. No question-2 runtime
answer, mutation checks or end-to-end six-call acceptance were reached. Missing
later-stage checks in evaluation.log are missing observations, not additional
observed arithmetic failures. The first reply also stays a static missing-damage
explanation after damage is supplied.

The added stage made class identification and the decision against a wrapper
explicit. It did not elicit a parameterized callable on this task. Generalization
was intentionally optional, and the context preferred simple interfaces; the
outcome is consistent with that instruction. Three deterministic trials and a
changed procedure/call count do not establish a causal or general model ability
claim.

A future interface should distinguish primitive composition from referencing a
single retained callable, without making primitive reuse a gate failure. A separate
question is whether a problem with more repeated structure makes a new reusable
operator worthwhile. Neither change was silently introduced into these trials.

## Recorded evidence

- [Live evaluation](../runtime/three-stage-inventory-live-final-20261107/evaluation.log)
- [Integrity and interface summaries](../runtime/three-stage-inventory-live-final-20261107/integrity.edn)
- [Question-1 interface](../runtime/three-stage-inventory-live-final-20261107/trial-1/generalization-1.edn)
- [Question-2 interface](../runtime/three-stage-inventory-live-final-20261107/trial-1/generalization-2.edn)
- [Actual implementation](../runtime/three-stage-inventory-live-final-20261107/trial-1/generated-1.lain)
- [Actual initial context](../runtime/three-stage-inventory-live-final-20261107/trial-1/context-1.txt)

The evidence retains original contexts, planning records, separate provider
thinking, unmodified source, admission receipts, graphs and available snapshots.
Source review and integrity checks confirm the counts above. Focused formatting
and lint checks passed with zero errors or warnings. No model-generated tests or
extra repair calls were requested.

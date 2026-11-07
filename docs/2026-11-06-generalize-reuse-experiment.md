# Generalizing operators for related problems

This condition replaces the look-back paragraph with the agreed guidance:

> Inspect the operators and computations already available in your environment. Can any solve this problem or a useful part of it? If an existing operator almost fits, consider adapting or generalizing it. If something is missing, consider constructing an operator that solves a meaningful class of similar problems, with varying quantities supplied as inputs. Keep assumptions and dependencies explicit, and check the operator on the current problem. Prefer the simplest useful generalization; avoid adding abstraction without a clear purpose.

Only `methodology-cell` differs from the [look-back parent](../examples/lain/lookback-inventory.lain).
The [new parent](../examples/lain/generalize-inventory.lain) retains the same two
inventory questions, inputs, optional observation ports, support-aware operators,
persistent lexical child and worker, and corrected source/conversation reducers.
The methodology does not name inventory operators or require a particular abstraction.

Three fresh sessions use the existing provider settings and unchanged probe and
evaluator. Each completed trial makes two provider calls. No repair prompts,
evaluator feedback, retries or fallback solutions are provided. Original contexts,
source, separate provider thinking, admissions, identities, graphs and snapshots
are retained in [the evidence directory](../runtime/generalize-inventory-live-20261106/).

## Results

Three fresh real-model sessions completed, with exactly six provider calls and no
repairs. Corresponding rounds produced byte-identical source in all trials.
[Integrity checks](../runtime/generalize-inventory-live-20261106/integrity.edn)
verified distinct sessions, persistent workers, identical parent sources, and
preserved source and four-message conversation histories.

| Outcome | Partial-network guidance | Look-back guidance | Generalization guidance |
|---|---:|---:|---:|
| Correct initial numeric observations for both questions | 3/3 | 3/3 | 3/3 |
| Partial network construction and incremental completion | 3/3 | 3/3 | 3/3 |
| Created new first-round callable operators | 3/3 | 0/3 | 0/3 |
| Reused a first-round callable in the second answer | 0/3 | 0/3 | 0/3 |
| Correct numeric values after damage correction | 3/3 | 3/3 | 3/3 |
| Included shared-rule premise | 3/3 | 0/3 | 3/3 |
| Included own question premises | 0/3 | 0/3 | 0/3 |
| Damage withdrawal makes dependent results unknown | 3/3 | 0/3 | 0/3 |
| Shipment 2 remains independent of shipment 1 | 3/3 | 3/3 | 3/3 |
| Unrelated-note invariance | 3/3 | 3/3 | 3/3 |

The new response used `ttms-premise-closure` on **computed values**, for example:

```lain
(define total-1 (ttms-premise-closure (* boxes-1 items-per-box-1) shared-rule))
```

This attached the shared-rule premise in the running implementation, but did not
create a callable. There were no `network [parameters]` definitions. The second
response repeated the same formulas with shipment 2's inputs and did not reference
first-round bindings. Thus neither new callable construction nor reuse of the
existing first solution was observed. Primitive arithmetic operators were used,
as required by the unchanged interface.

Question 1 computed 240 total while usable stock, margin and fulfillment were
unknown. Publishing 10% damage yielded 216, 16 and true without inference. Question
2 gave 342 usable, margin −8 and false, and the 2.5% correction produced 351, 1 and
true. Withdrawing question 1 did not withdraw its conclusions. Withdrawing shipment
2's damage premise also left 351, 1 and true available; the support snapshot still
listed damage. This is an observed runtime behavior failure; its cause was not
diagnosed in this experiment.

The first text interpolated the computed total but kept its missing-damage
explanation after publication. The second text interpolated changing values but
hardcoded non-fulfillment. After correction it described a “shortage of -1.0 items”
and still said the shipment could not fulfill the order. Initial numeric correctness
therefore does not establish correct reactive text.

The [evaluation log](../runtime/generalize-inventory-live-20261106/evaluation.log)
records every check separately. Exact generated sources are available for
[question 1](../runtime/generalize-inventory-live-20261106/trial-1/generated-1.lain)
and [question 2](../runtime/generalize-inventory-live-20261106/trial-1/generated-2.lain);
the other two trials generated identical source.

## Speed comparison

| Condition | Mean Q1 | Mean Q2 | Mean sum |
|---|---:|---:|---:|
| Partial-network | 29.39 s | 12.56 s | 41.95 s |
| Look-back | 23.59 s | 11.67 s | 35.26 s |
| Generalization | 14.85 s | 12.54 s | 27.39 s |

The new summed mean is 22.3% below look-back guidance and 34.7% below the original
condition. There was no observed computational reuse to explain this difference.
These are approximate provider response times, not pure reasoning times or a
controlled causal speed estimate. Individual timestamps and durations are in
[timing-comparison.json](../runtime/generalize-inventory-live-20261106/timing-comparison.json).

## Reproduction

```sh
LAIN_EXPERIMENT_SOURCE="$PWD/examples/lain/generalize-inventory.lain" \
LAIN_EVIDENCE_DIR="$PWD/runtime/generalize-new" tools/run_partial_inventory.sh live
```

The runner exports committed harness code into an isolated directory and overlays
only the selected parent, probe and evaluator. The model settings, timeout and
runtime are the same as the earlier experiments; unrelated edits and deployed
services are preserved.

## Interpretation

Numerical correctness, partial network construction and incremental completion,
creation of callable operators, actual callable reuse, and support correctness are
separate outcomes. Callable reuse requires retained identity, a new invocation and
a connection to the second answer; copied formulas do not suffice. The existing
evaluator's source dataflow is conservative, so generated sources and invocation
evidence are also reviewed.

Response times use original context and response file timestamps, which bracket
the provider call. They include thinking, generation and transport, but exclude
startup, admission and mutation checks. Conditions are not randomized or interleaved;
three trials per condition cannot isolate a causal effect on speed or strategy.
The earlier conditions are documented in the
[partial-network report](2026-11-06-partial-network-experiment.md) and
[look-back report](2026-11-06-lookback-reuse-experiment.md).

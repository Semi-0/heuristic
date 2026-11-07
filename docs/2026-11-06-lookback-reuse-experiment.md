# Looking back for reusable solutions

This condition replaces the previous optional partial-network paragraph with:

> Look back at earlier problems and solutions available in your environment. Is any previous solution relevant to this problem? Consider whether you can use it directly, adapt part of it, or apply its method. Check that its assumptions and dependencies fit the current problem.

Only `methodology-cell` differs between the [new parent](../examples/lain/lookback-inventory.lain)
and the corrected [previous parent](../examples/lain/partial-inventory.lain).
The general methodology still asks “Have you seen a related problem?” Executable
Lain remains the response interface, but the methodology no longer proposes a
partial Lain network or specifies how to reuse a solution.

The experiment uses the same two inventory questions, separate input and question
premises, shared rule premise, optional observation ports, persistent worker and
lexical child, source/history reducers, mutation sequence, and provider settings.
The history adapter includes the previously verified fix that retains each initial
reply in history while leaving output ports reactive. The previous live condition
used the older adapter; its unchanged-source replays verified that this fix preserved
all observed answer values and semantic supports.

Three fresh real-model trials are run without repair prompts, retries, feedback,
or fallback answers. A completed trial makes two provider requests. The prior
five controlled evaluator scenarios were re-evaluated successfully before starting.
No control source or prior experiment solution is included in model contexts.

## Results

All three trials completed with six provider calls in total. Corresponding rounds
produced byte-identical source across trials. Session identities were distinct;
each trial retained one worker, its child, both sources and all four messages.
The [integrity checks](../runtime/lookback-inventory-live-20261106/integrity.edn) passed.

| Observed behavior | Previous guidance | Look-back guidance |
|---|---:|---:|
| Correct initial numerical observations for both questions | 3/3 | 3/3 |
| Partial network construction and incremental completion | 3/3 | 3/3 |
| Created first-round callables | 3/3 | 0/3 |
| Reused an existing callable in the second answer | 0/3 | 0/3 |
| Correct values after the 2.5% correction | 3/3 | 3/3 |
| Included own question premises | 0/3 | 0/3 |
| Included the shared-rule premise | 3/3 | 0/3 |
| Withdrew dependent results when damage was withdrawn | 3/3 | 0/3 |
| Shipment 2 remained independent of shipment 1 | 3/3 | 3/3 |
| Unrelated-note invariance | 3/3 | 3/3 |

The new first response directly defined total, usable, margin and fulfillment,
without a callable or premise closure. The second response repeated those formulas
with the new shipment's inputs. It did not reference first-round bindings. This
is consistent with applying the same method, but the evidence cannot distinguish
looking back and adapting a method from independently producing the same formulas.
It does not establish reuse of an existing computation.

The network still completed from unknown damage to 216 usable items, margin 16
and fulfillment true without another request. The second answer correctly gave
342 usable, margin −8 and fulfillment false, then updated to 351, 1 and true after
the correction. On damage withdrawal, however, those latter values stayed available.
Their recorded support sets included the damage input; this is an observed
withdrawal failure, not evidence that the mathematical formula omitted damage.
Its runtime cause was not diagnosed in this experiment. Question withdrawal also
failed, and neither question nor shared-rule premises were attached.

The first text reply correctly described missing information but did not state the
computed total and did not update after publication. The second text interpolated
its damage rate and numbers, but hardcoded that the order could not be fulfilled.
After correction it said “a shortage of -1.0 items” and still asserted non-fulfillment.
Correct numeric observations therefore do not imply a correct reactive text answer.

See the [per-check evaluation](../runtime/lookback-inventory-live-20261106/evaluation.log)
and exact sources for [question 1](../runtime/lookback-inventory-live-20261106/trial-1/generated-1.lain)
and [question 2](../runtime/lookback-inventory-live-20261106/trial-1/generated-2.lain).
The remaining two trials produced the same sources.

## Timing measurement

The timing comparison uses the original modification timestamps of `context-N.txt`
and `response-N.edn`. The unchanged probe writes these immediately before and after
the provider call. The difference approximates provider round-trip response time,
including thinking, generation, transport and small file-write overhead. It excludes
JVM startup, source admission and later mutation tests. It is not a direct measure
of internal reasoning speed.

[compare_inventory_timing.py](../tools/compare_inventory_timing.py) preserves the
underlying timestamps, individual durations, source character counts, means and
medians in the comparison JSON. The conditions were not randomized or interleaved;
server load, prompt caching and output length were not controlled.


Times in seconds:

| Trial | Previous Q1 | Previous Q2 | Previous sum | Look-back Q1 | Look-back Q2 | Look-back sum |
|---|---:|---:|---:|---:|---:|---:|
| 1 | 30.03 | 13.43 | 43.46 | 24.22 | 12.19 | 36.41 |
| 2 | 29.08 | 12.08 | 41.15 | 23.30 | 11.40 | 34.70 |
| 3 | 29.07 | 12.16 | 41.24 | 23.25 | 11.42 | 34.67 |
| **Mean** | **29.39** | **12.56** | **41.95** | **23.59** | **11.67** | **35.26** |

Mean summed response time was **15.95% lower**. Median summed time was 41.24 s
previously and 34.70 s with look-back guidance. The new output was shorter and
omitted callable wrappers; these runs do not isolate why response time changed.
There was no observed computational reuse to explain a speedup. Raw measurements
are in [timing-comparison.json](../runtime/lookback-inventory-live-20261106/timing-comparison.json).

## Evidence and reproduction

The [runner](../tools/run_partial_inventory.sh) now accepts `LAIN_EXPERIMENT_SOURCE`;
its default remains the previous condition. The probe and evaluator are unchanged.

```sh
LAIN_EXPERIMENT_SOURCE="$PWD/examples/lain/lookback-inventory.lain" \
LAIN_EVIDENCE_DIR="$PWD/runtime/lookback-new" tools/run_partial_inventory.sh live
```

The recorded run is in [runtime/lookback-inventory-live-20261106](../runtime/lookback-inventory-live-20261106/).
Each trial retains exact contexts, unchanged source, separate provider thinking,
admission receipts, identities, connection graphs, values, supports, and request
counts. The comparison condition is documented in the
[previous experiment report](2026-11-06-partial-network-experiment.md).

Reuse is assessed separately from numerical correctness and dependency correctness.
Applying an earlier method or copying formulas can be useful, but does not establish
reuse of an existing callable. That stronger claim requires a preserved callable
identity, an actual new invocation, and a connection from its result to the answer.
Three trials under each condition are exploratory evidence, not a causal estimate
of the effect of the changed guidance.

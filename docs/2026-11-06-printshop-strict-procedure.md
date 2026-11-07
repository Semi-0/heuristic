# Strict analysis before implementation

This condition tests the strict procedure on its own, before any later integration
with the general methodology. The previous methodology binding is removed and
replaced with a standalone four-step procedure: ground the problem, inspect
available operators, choose an approach, then implement and independently test it.
The five print-shop questions, rules, structs, worker, lexical child and provider
settings remain unchanged.

Each question has two requests. Analysis returns a concise EDN record containing
outputs, facts, unknowns, units, rules, branches, operator candidates, a decision,
a selected/proposed operator name, and planned tests. The host checks completeness
and that a selected reuse/adapt binding is actually callable. It records that
binding's identity. The record is then supplied unchanged to the implementation
request. The phase transition gives no evaluator feedback or corrected formula.

Only implementation replies enter source admission and the generated-code reducer.
Analysis replies are retained separately, with exact contexts and provider thinking.
The user/final-reply reducer retains ten conversational messages on completion.
The phase gate makes prerequisites observable but does not prove that an analysis
is semantically correct or that the model exhaustively inspected the environment.
Actual reuse is assessed separately through callable identity, runtime invocations
and source dataflow to the quote output.

There are ten provider requests in a completed session, no repair prompts, retries
or fallback answers. This is one exploratory session, not a causal or statistical
comparison. The extra requests and changed procedure are both part of the condition.

## Live results

The standalone strict procedure changed abstraction behavior in this session.
The first implementation created `compute-quote-1` with explicit `[order pricing]`
parameters and a returned breakdown struct. Analyses 2–5 selected that existing
callable, and every later implementation invoked it with the new order:

```lain
(define result-2 (compute-quote-1 order-2 pricing))
```

Runtime invocation records, preserved declaration/captured-environment identity,
stable callable reference and source dataflow to the quote outputs confirm reuse
in all four subsequent questions. This is stronger evidence than a source-name
match or repeated formulas. Production and rush fields computed correctly in all
five rounds, confirming partial execution of those invocations.

| Outcome | Previous one-phase session | Standalone strict procedure |
|---|---:|---:|
| Provider requests | 5 | 10 |
| New parameterized quote callable | None | 1 |
| Later questions invoking that callable | 0/4 | 4/4 |
| Correct initial final totals | 2/5 | 0/5 |
| Usable model self-check results | 5 true, including 3 wrong quotes | 0/5; all unknown |
| Initial quote breakdown values matching oracle | 26/35 | 10/35 |

The previous rush-sign error is corrected in the new source: the subtotal adds
production and rush, subtracts discount, then adds setup. However, the running
network did not produce complete quotes. Discount and downstream fields were
unknown in question 1 and contradictory in questions 2–5. The mechanism causing
those failures was not diagnosed; it must not be attributed solely to prompting
or to the runtime without further isolation.

The analysis records proposed more specific checks for tier allocation, flags,
discount caps, thresholds and tax placement. The generated checks remained partly
based on the implementation's expressions, and all stayed unknown. A definite
capability mismatch is that the code called unadvertised `and`, which appears as
an unknown binding in the [next context](../runtime/printshop-strict-live-20261106/context-3.txt).
That prevents those combined tests from completing. This observation does not
explain the separate quote-field failures. The model continued reusing the callable
while prior reply/test ports remained unusable, so operator discovery did not
establish execution readiness or correctness.

No usable text replies were fabricated: the conversation reducer retained the
five user messages, while the unavailable reply ports produced no assistant
messages. Both phases' exact replies and provider thinking are preserved, as are
all five admitted implementation sources. [Integrity checks](../runtime/printshop-strict-live-20261106/integrity.edn)
confirm phase ordering, ten provider requests, unchanged analyses/source,
persistent identities, source filtering and preservation of the actual history.

See the [full evaluation](../runtime/printshop-strict-live-20261106/evaluation.log),
[analysis records](../runtime/printshop-strict-live-20261106/analyses.edn),
[first callable](../runtime/printshop-strict-live-20261106/generated-1.lain), and
[second invocation](../runtime/printshop-strict-live-20261106/generated-2.lain).
The later question-withdrawal check can appear to pass merely because question 1
was already unknown; it is not evidence of successful retraction of a computed quote.

## Response times

| Question | Analysis | Implementation | Sum |
|---|---:|---:|---:|
| 1 | 41.49 s | 68.78 s | 110.27 s |
| 2 | 34.14 s | 35.69 s | 69.83 s |
| 3 | 31.57 s | 30.11 s | 61.68 s |
| 4 | 33.49 s | 30.38 s | 63.86 s |
| 5 | 41.10 s | 23.37 s | 64.47 s |

Analysis consumed 181.78 s and implementation 188.33 s,
for **370.11 s total**, versus 174.02 s previously
(112.7% higher, approximately 2.13 times the provider time).
This includes ten calls instead of five and a changed procedure, and the output
length and context differ. It is not a clean estimate of the cost of analysis alone
or a demonstrated reuse speedup. Setup, gate processing, source admission and
mutation checks are excluded. [Raw timing evidence](../runtime/printshop-strict-live-20261106/timing-comparison.json).

Integration with the general methodology has not been tested. The present result
establishes callable creation and reuse in this one run, while correctness and test
execution remain unresolved. Execution failures should be isolated before using
this result to judge further prompt integration.

## Controlled validation

The [controlled session](../runtime/printshop-strict-control/evidence.edn) completed
all ten synthetic phase responses, with five implementation admissions and five
retained analyses. It selected `quote-fn` in analyses 2–5 and invoked the preserved
callable in each corresponding implementation. All 35 initial quote values matched
the oracle. The [control evaluator](../runtime/printshop-strict-control/evaluation.log)
passed phase order, source filtering, histories, persistent identity and reuse checks.
The only failed behavioral checks were the same quantity-correction and later
question-restoration failures established before this condition.

In live evidence, `context-N.txt` and `response-N.edn` use request sequence 1–10.
`analysis-N.edn` and `generated-N.lain` use question number 1–5. Analysis files are
plain model-returned EDN; implementation source is unchanged executable Lain.
The evidence record's `phase-responses` preserves all ten replies, while `responses`
contains only implementation replies for compatibility with the quote evaluator.
The gate checks record shape and callable existence, not whether a proposed test
is meaningful; test quality is reviewed from the source and observed results.

## Files and reproduction

- [Parent](../examples/lain/printshop-strict.lain)
- [Probe and phase gate](../tools/probe_printshop_strict.clj)
- [Evaluator](../tools/evaluate_printshop_strict.clj)

```sh
LAIN_EVIDENCE_DIR="$PWD/runtime/strict-control-new" tools/run_printshop_strict.sh control
clojure -M tools/evaluate_printshop_strict.clj runtime/strict-control-new
LAIN_EVIDENCE_DIR="$PWD/runtime/strict-live-new" tools/run_printshop_strict.sh live
clojure -M tools/evaluate_printshop_strict.clj runtime/strict-live-new
```

Controls use the existing known quote function, never exposed in live contexts.
The earlier struct experiment established two controlled mutation failures when
order 1 crosses the free-delivery threshold; these are retained separately from
phase-gate validation and initial quote correctness. The strict control evaluator
expects exactly that known failure set, rather than calling all runtime behavior
correct. See the [previous report](2026-11-06-printshop-struct-experiment.md).

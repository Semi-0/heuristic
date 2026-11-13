# Dependency-aware reuse under a changed policy

This follows the durable accumulation and missing-output repair experiments. The
question is whether a model can reject an incompatible saved whole computation,
reuse an applicable component, and preserve separate policy/question dependencies
for old and revised interpretations.

## Question

A stored delivered-event quote adds a fixed delivery fee after surcharging pieces
and labor. Its catalog status remains `:current`, but a new policy requires the
same fractional surcharge on delivery too. The model must inspect assumptions,
choose the most specific retained component valid under both policies, and use it
once as a common result. It then constructs historical and revised calculations
with separate policy and question premises. It must explain why status alone does
not make the saved whole quote applicable.

This is a guided problem with an explicit dependency contract, not an experiment
in discovering the need for provenance without instructions. The environment
provides documentation and generic examples for existing premise closures, but
no solved pricing network or expected numerical answer. The controlled fixture
is not included in real-model context.

| Condition | Shared event | Historical quote | Revised quote |
|---|---:|---:|---:|
| 100 pieces × 6; 6 hours × 30; 5%; delivery 40 | 819 | 859 | 861 |
| Delivery becomes 60 | 819 | 879 | 882 |
| Quantity becomes 120 | 945 | 1005 | 1008 |
| Surcharge becomes 10% | 990 | 1050 | 1056 |

The evaluator then withdraws and restores each policy and each question
independently. Withdrawing an old premise must make only the historical result
unknown. Withdrawing a new premise must make only the revised answer/reply
unknown. The shared result must remain usable throughout. Withdrawing an unrelated
note must leave every price unchanged. These updates require no more inference.

## Architecture and boundaries

- `environments/catalog-policy.lain` provides the changed question, role,
  capabilities, documentation, premise inputs, and parent output ports.
- `experiments/catalog-policy.lain` retains the existing discovery → selection →
  saved-source admission → execution structure and bounded Lain repair policy.
- Saved source is admitted using the existing `incremental-compile` into lexical
  children. The existing model remains the sole worker.
- Policy/question facts and revisions enter through the existing public
  `session-publication/declaration`: extensions declare producers and source
  connections, without pre-seeded domain cells. Numeric external inputs use the
  public publication API. No low-level network construction is used.
- The existing `premise-experiment` adapter exposes TTMS operators and premise
  inspection. No support algebra or kernel behavior is changed.
- The host probe records provider requests, source receipts, selected source,
  callable identities, support sets, snapshots and independent acceptance checks.
  It supplies inputs and mutations; it does not choose agent phases, retry,
  construct a live solution in the real run, or supply expected answers to the model.

The restored arithmetic component remains pure. Policy/question closures govern
new derived computations. The public numeric publication API also preserves numeric source supports. Exact
support checks require all five numeric event inputs on the shared base, and all
six numeric inputs plus the corresponding policy/question pair on each quote.
Numeric updates and policy/question withdrawal are tested separately. This does not resume an old suspended
TTMS graph or establish complete historical causal provenance.

## Results and verification

**The thinking-enabled real-model trial passes all 41 checks.** The two preceding
non-thinking trials fail numerical acceptance and remain recorded below.

| Trial | Provider calls | Acceptance |
|---|---:|---|
| Corrected control (`control-3`) | 0 | 41/41 |
| Fresh, no methodology (`live-1`) | 3 | 30/41; delivery principal omitted |
| Fresh, generic methodology (`live-2-methodology`) | 3 | 30/41; same omission |
| Fresh, methodology + thinking (`live-3-thinking`) | 3 | 41/41 |

The successful model queried catalog metadata, selected `:event-cost/v1`, and
explicitly rejected the assumption of `:delivered-event-cost/v1` despite its
`:current` status. Only the event callable and its linear/percentage dependencies
were restored. Source admission was byte-exact. Application inspection found one
retained event call, two retained linear calls, and one retained percentage call.
The model computed the shared amount once, then constructed separate premise
closures for historical and revised quotes.

All numbers in the table above were observed in the successful run. Exact support
sets matched the numeric inputs plus the appropriate policy/question pair; the
shared amount contained neither policy nor question. All four independent
policy/question withdrawal-and-restoration sequences passed, as did unrelated
note withdrawal. There were no further model calls or source admissions during
input changes and start replay. The owner closed successfully. No repair request
was needed in any of the three live trials: the initial failures had known but
incorrect outputs, outside the existing missing-output repair contract.


The corrected control `runtime/policy-20261113/control-3` passes all 41 checks,
with three controlled responses, zero provider calls, and a closed session owner.
Both controlled and live modes follow the same compiled Lain orchestration;
controlled responses establish the runtime path, not real-model acceptance.

An earlier control (`control-2`) passed every numerical, mutation, withdrawal,
restoration and isolation check but failed four support assertions. The evaluator
incorrectly expected numeric external inputs to be support-free. Inspection showed
their actual source identities, consistent with the existing public publication
implementation (`session/publication-input` → `publish-value` →
`publication/next-source-datum`). The evaluator was corrected to require exact
numeric-plus-policy support sets; no runtime change or weakened subset check was
used. This was a harness expectation error, not a kernel or model failure.
An earlier runner preflight (`control-1`) stopped on a Clojure syntax error before
executing the experiment; no provider call occurred.

The first real trial (`live-1`) made three provider calls and passed 30 of 41
checks. It correctly selected `:event-cost/v1`, rejected the incompatible delivered
quote, reused the saved callable once, and preserved the required exact supports.
However, its revised function computed `base + delivery * surcharge`, omitting
the delivery principal. Initial outputs were 819 shared, 859 historical and 821
revised instead of 861. Eleven numeric snapshot checks failed. The session closed;
no generated source was patched or replaced. The existing missing-output repair
policy did not detect this known-but-wrong answer, as expected from its documented
scope.

For a second real trial, the existing generic `lib/problem-methodology.lain` text
was added to the role binding. It asks the model to identify the problem, abstract
its structure, reduce it, then solve/reconstruct and verify. No expected answer,
solved pricing expression or controlled fixture was added. The evaluator,
transport settings and kernel stayed unchanged. This is a context revision
between trials, not autonomous correction of the first trial or a controlled
causal comparison of prompting strategies.

The methodology-only trial (`live-2-methodology`) repeated the same omission and
again passed 30/41 checks. A final comparison (`live-3-thinking`) keeps that task
context and evaluator unchanged while enabling the existing provider's supported
thinking mode (`medium`, 4,096 thinking-token budget, 8,192 total output tokens).
No new model implementation, host semantic instruction, or solved formula is
introduced. Raw provider thinking is recorded separately from executable source.

Public-session API tests pass 3 tests / 36 assertions. Runtime-composition tests
pass 2 tests / 15 assertions. Source-restoration and selection tests pass 4 tests /
20 assertions. The Clojure autoformatter and linter report zero errors and warnings
across all five changed Clojure files, including the prior uncommitted experiments.
The standalone probes now have distinct, path-matching namespaces so batch lint
can distinguish them. Shell syntax checks pass. `.lain` files are validated by
actual compilation and execution; they are not passed through the Clojure linter.

The prior durable-reuse/repair milestone was committed separately as `cf7f4db`.
The changed-policy experiment is a separate commit, with raw databases and large
evidence kept local. The companion runtime remains at
`2773dffc1cf5a2b129559aa445d66d09f96d4dd7`; its tracked source is unchanged.

## Reproduction

```bash
LAIN_POLICY_MODE=control \
LAIN_ACCUMULATED_TRIAL=/absolute/path/closed-successful-accumulation \
LAIN_EVIDENCE_DIR=/absolute/path/new-policy-control \
bash tools/run_catalog_policy.sh
```

Use `LAIN_POLICY_MODE=fresh` and another fresh evidence directory for the real
model. Add `LAIN_POLICY_THINKING=1` to request the supported thinking mode; the
default remains disabled so the failed non-thinking conditions are reproducible. The runner copies the closed database and snapshots source; it preserves
the original evidence and does not restart the deployed service. Bulk records
remain local under ignored `runtime/policy-20261113/`.

## Limits and next work

One successful thinking-enabled trial does not establish broad reliability or a
causal advantage of thinking mode. The two failed live trials demonstrate that
correct retrieval and exact premise support can coexist with incorrect arithmetic.
All support comparisons and numerical expectations were independent host checks;
the model did not receive their expected answers or post-failure corrections.

The policy and question premise contract was explicitly requested. The experiment
does not establish automatic inference of every dependency, durable restoration of
TTMS state, or general rejection of all stale catalog entries. Historical and
revised results are newly constructed derivatives of a restored pure component;
the previous process's suspended result graph is not resumed. These new derived
closures are not registered/persisted as a fourth durable module.

The next reliability question is whether Lain can expose a failed semantic
validation as environment feedback and repair an admitted but incorrect answer.
That requires a broader contract than output availability; it was not added or
claimed in this experiment. No kernel work was required for the milestone tested
here.

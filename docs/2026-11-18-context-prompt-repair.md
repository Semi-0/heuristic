# Context prompt repair — November 18

Prompt defects corrected; full experiment remains unaccepted. The first revised
probe recovered omitted evidence and delivered the correct quote without callable
reuse. Stronger reuse wording in a second probe exhausted retrieval instead.
These are exploratory diagnostics, not repeated matched acceptance sequences.

Follow-up to the [negative pilot](2026-11-17-queryable-context.md). Prompting is a
plausible contributor, not an established sole cause: Qwen guessed an interface
without requesting retrieval; the resulting late application error also exposed
a host failure-containment limitation.

The revised [environment](../environments/context.lain) explains the retrieval
feedback boundary, replacement of working evidence, row projection, callable
inspection fields, direct reference resolution, and vector-shaped evidence refs.
It instructs the model to retrieve unknown interfaces rather than guess. A complete
query-only example supplies no domain answer or relevant operator selection.
The role now describes history conditionally, correctly for both primary arms.
The [Lain policy](../lib/context-policy.lain) includes the example in its snapshot;
stage selection, evidence selection and retry counts are unchanged.

Verification completed under the watchdog; historical checkpoints follow. The new controlled test executes
the exact documented example and checks its results reach the next request.
A diagnostic indexed probe will replay the original shared preparation into a
fresh session and attempt only the first continuation. The driver now records its
selected task subset; the default remains all five tasks. This diagnostic is not
a new matched comparison or an acceptance run. No interrupted run is restarted.

Settings remain direct Qwen, thinking enabled, 8,192 output tokens and 12,000 input
tokens including template. Tokenizer calibration precedes the new provider probe.
Evidence: `/home/lain/reports/heuristic-context-20261118/`.

Late asynchronous application failures are not fixed by these prompt edits.
The public admission boundary catches extension failures, but a callable resolved
later can fail during subsequent propagation. The existing terminal archive path
preserves that failure; it does not provide in-session recovery. A safe containment
change needs separate public-session lifecycle tests, not a host retry loop.
LifelongAgentBench remains paused. No kernel/service changes, commits or pushes.

## Controlled verification

The guarded suite passed: **16 tests, 67 assertions, zero failures/errors**,
including the exact prompt example, eviction/reuse, pagination, budget rejection,
incomplete-response handling and bounded retrieval. Log: `checks.log` in the
above evidence directory. This is focused verification, not the full repository
suite. Shell syntax and diff whitespace checks also passed.

Tokenizer calibration is queued under the watchdog in `tokenizer/`; no indexed
probe has started yet. Calibration and task calls will be counted separately.

Calibration passed (30 counted input tokens = 30 provider input tokens; 38 output
 tokens). The indexed diagnostic was launched on November 18 under the watchdog,
with original preparation replay and selected tasks `[0 1]`, into `indexed-probe/`.
No fresh preparation call is charged. It subsequently terminated; see the final outcome below.

The first real response completed (HTTP 200, 4,360 input / 4,123 output tokens)
and selected `:retrieve`, issuing four catalog queries for descriptions,
interfaces, assumptions and evidence IDs. Lain admitted the source and requested
continuation with the returned evidence (4,744 input tokens). This establishes
retrieval selection on this diagnostic, not yet a correct quote or callable reuse.

The first diagnostic retrieved source chunks and delivered the correct $533.30,
but generated fresh arithmetic rather than applying the earlier callable. Indexing
is pending. This is a partial improvement, not indexed-context acceptance.
A second prompt revision explicitly requires applying a suitable live callable
and preserving its id/interface across retrieval rounds; rewriting source arithmetic
is distinguished from reuse. It still does not name or select a pricing operator.

First diagnostic is terminal: q1 correct, index valid, five new calls, no repairs,
23,776 input / 18,447 output tokens, 475.459 seconds provider plus availability
wait. Three retrieval replies; execution rebuilt arithmetic, so no reuse claim.
All sent requests were within budget. Original preparation replay had no new
provider calls. Calibration cost is separate.

Second revision's focused example check passed (1 test / 4 assertions). A fresh
`indexed-reuse-probe/` was launched serially under the watchdog on November 18,
again selecting `[0 1]` and replaying original preparation. This is a distinct
prompt diagnostic, not a restart or a matched performance comparison.

## Final outcome

Both diagnostic runs terminated and their owners closed. No automatic restart.
The second probe is unresolved from the retrieval limit; indexing remained invalid
after its single correction. Both recovered exact evidence absent from the initial
prompt. Offline topology comparison found **no new applications of preparation
callables** in either run. Neither had a terminal host failure or sent an over-budget
request. This does not establish that late application failure containment is fixed.

| Diagnostic | Correct continuation | Index | Calls | Input / output tokens | Provider + wait seconds | Repairs |
| --- | --- | --- | ---: | ---: | ---: | ---: |
| indexed-probe | True | valid | 5 | 23776 / 18447 | 475.459 | 0 |
| indexed-reuse-probe | False | invalid | 7 | 34395 / 24972 | 661.107 | 1 |

Counts exclude replayed preparation and the separate calibration (one call,
30 input / 38 output tokens). The original preparation was reused unchanged;
these are prompt diagnostics, not newly prepared matched controls. Detailed stage
costs, evidence reads and topology checks are in
[summary.json](/home/lain/reports/heuristic-context-20261118/summary.json).
The reporting tool now accepts optional condition directory names, retaining its
original defaults. Its guarded offline run passed (`report.log`).

The current candidate retains the stronger reuse instruction; one unsuccessful
sample does not establish that wording caused regression. It is not accepted as
an end-to-end fix. Next: expose the remaining retrieval allowance, improve retention
of model-selected evidence without host operator selection, strengthen query/index
contract examples, and separately test public-session error containment. Then run
a fresh matched sequence. Changed assumptions and composition were not tested here.

Recorded harness HEAD: `00c1eadb49582fcac26df1cd2a333ba7610b7755`.

Recorded runtime HEAD: `2773dffc1cf5a2b129559aa445d66d09f96d4dd7`.

Each probe retains frozen source and SHA-256 manifests; working-tree edits are not represented by HEAD alone. No commits, pushes, kernel edits or service replacements.

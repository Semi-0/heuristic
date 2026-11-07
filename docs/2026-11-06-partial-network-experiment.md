# Optional partial networks in two inventory questions

This experiment offers partial network construction as a general technique within
problem-solving guidance. It does not instruct the model to build a callable or
reuse one. Each fresh trial asks two inventory questions in one persistent model
worker and lexical child. Numerical correctness, partial network construction and
incremental completion, callable reuse, and dependency correctness are evaluated
separately.

## Results from three fresh real-model trials

All three trials completed, with exactly two provider requests each. They produced
byte-identical source in each corresponding round. Each trial used a fresh session
and a fresh session-scoped child, with one persistent worker throughout its two
questions and subsequent mutations.

| Outcome | Trial 1 | Trial 2 | Trial 3 |
|---|---|---|---|
| 240 total before damage; other requested quantities unresolved | Yes | Yes | Yes |
| Connected partial computation before damage | Yes | Yes | Yes |
| Publication alone produces 216 usable, margin 16, fulfillment true | Yes | Yes | Yes |
| Question 2: 342 usable, margin −8, fulfillment false | Yes | Yes | Yes |
| Correction to 2.5%: 351 usable, margin 1, fulfillment true | Yes | Yes | Yes |
| Callable reuse in the second answer | No | No | No |
| Own question premises included in conclusions | No | No | No |
| Withdrawing question 1 withdraws its conclusions | No | No | No |
| Shipment 2 remains independent of shipment 1 | Yes | Yes | Yes |
| Shipment 2 damage withdrawal and recovery | Yes | Yes | Yes |
| Unrelated-note value/support invariance | Yes | Yes | Yes |
| First text reply updates after damage publication | No | No | No |

There were no missing numeric/boolean observation connections and no invented
initial damage rate. Each first response built four rule-supported callables and
connected total, usable, margin and fulfillment. Each second response constructed
four new callables with copied formulas; the original callable identities persisted
but were not invoked by the second answer.

Numeric results carried their input facts and `shared-rule`, but neither question
premise. Thus the arithmetic and damage-dependent propagation worked, while
question ownership failed. This is separate from cross-question contamination:
shipment 2 did not inherit shipment 1's facts or question.

Text quality was weaker than the numeric network. The first explanation correctly
identified missing information, but hardcoded its numeric claim instead of deriving
the text from `total-1`, and remained stale after publication. The second reply
interpolated computed numbers but hardcoded “5%” and “falls short by 8 items.” After
the correction, it displayed usable 351, margin 1 and fulfillment true alongside
that stale shortage sentence. These are not fully correct reactive text replies.

The exact contexts, unmodified source, separate provider thinking, admissions,
identities, graphs and snapshots are in
[trial 1](../runtime/partial-inventory-live-20261106/trial-1/evidence.edn),
[trial 2](../runtime/partial-inventory-live-20261106/trial-2/evidence.edn), and
[trial 3](../runtime/partial-inventory-live-20261106/trial-3/evidence.edn).
The [evaluation log](../runtime/partial-inventory-live-20261106/evaluation.log)
lists each failed check rather than collapsing outcomes into a score.

## History adapter correction

The original real-model runs exposed a parent adapter problem after the model
requests finished: reactive replies were inserted repeatedly under the same
conversation event ID. When shipment 2's reply changed, the reducer's final value
became contradictory. Both request contexts and both admission-time conversation
histories were intact, and are retained in the original evidence. Generated-source
history remained intact throughout. This defect belongs to the host, not the model.

The final parent uses `cell:first` only on each round's reply **as it enters
conversation history**. Output ports remain live, and their changes and withdrawals
remain observable. This preserves the initially delivered message without
reinserting changed text under an existing event ID. No generated source was
modified, and no additional real-model requests were made.

The corrected parent is validated with the five controls and three explicitly
labelled, unchanged-source replays. Replays are runtime verification, not additional
model trials or model successes. The original real-model evidence is not overwritten.
[Replay integrity checks](../tools/verify_partial_inventory_runs.clj) compare source,
all stage values and semantic supports, and verify persistent workers and retained
histories. The original live trials and the corrected replay results must be read
as separate records. All [integrity checks](../runtime/partial-inventory-replay-20261106/integrity.edn)
passed: the unchanged sources produced the same values and semantic supports at
every stage, while both source and reply reducers retained their histories. The
[corrected-control evaluation](../runtime/partial-inventory-history-controls/evaluation.log)
passed all five controls; the [replay evaluation](../runtime/partial-inventory-replay-20261106/evaluation.log)
retains the model's question-support failures.

## Implementation

- [Parent environment](../examples/lain/partial-inventory.lain): literal methodology,
  input facts and premises, optional observation ports, existing text ports,
  environment descriptions, actual source and conversation reducers, and sequential
  source admission. It contains no inventory formula or solved example.
- [Probe](../tools/probe_partial_inventory.clj): unchanged generated source, separate
  thinking, contexts, receipts, session/child/worker identities, callable declarations
  and invocations, full connection graphs, child values, and support snapshots.
- [Evaluator](../tools/evaluate_partial_inventory.clj): records individual checks,
  including missing observations; a behavioral mismatch does not stop the trial.
- [Isolated runner](../tools/run_partial_inventory.sh): exports committed harness
  code into a temporary directory and overlays only these experiment files. It
  preserves existing local edits and does not restart or deploy any service.

The provider configuration is the existing local Blazux transport:
`qwen3.8-flash-next`, temperature 0, maximum 4096 output tokens, thinking enabled,
medium reasoning effort, 2048 thinking-token budget, and a 600000 ms provider
timeout. Each completed trial makes exactly two requests; publication, correction,
withdrawal, restoration and unrelated-note changes make none. There are no repair
prompts, evaluator feedback, retries or model-result fallbacks.

Each trial records the 240-item first shipment before damage is known, then
publishes an estimated 10% damage rate. The second shipment starts at 5% inspected
damage, is corrected to 2.5%, and is exercised through question and damage-premise
withdrawal and restoration. An unrelated delivery-note revision tests invariance.

## Evaluator validation

Five controlled sources use the same admission and runtime path as the model:
connected partial computation, disconnected unknown output, genuine callable
reuse, copied computation, and accidental cross-question dependencies. The copied
control also invokes the original callable in an unused result, to ensure that an
invocation alone is not credited as answer reuse. Controlled responses are never
included in real-model contexts and do not count as model success.

Raw runtime graph reachability includes environment observation and topology
links. Therefore it is not accepted alone as evidence of computational dependence.
Partial completion combines source dataflow, connections, and publication behavior.
Reuse additionally requires a preserved callable reference, declaration and captured
environment, a new runtime invocation, and a source-dataflow path to an answer port.
The source-dataflow analysis is conservative and is not a general lexical program
proof; generated sources should be reviewed alongside these checks.

## Reproduction

From the harness root:

```sh
LAIN_EVIDENCE_DIR="$PWD/runtime/partial-controls-new" tools/run_partial_inventory.sh controls
LAIN_EVIDENCE_DIR="$PWD/runtime/partial-live-new" tools/run_partial_inventory.sh live
```

To verify the history correction with recorded sources, without provider calls:

```sh
LAIN_REPLAY_SOURCE_DIR="$PWD/runtime/partial-inventory-live-20261106" \
LAIN_EVIDENCE_DIR="$PWD/runtime/partial-replay-new" tools/run_partial_inventory.sh replay
clojure -M tools/verify_partial_inventory_runs.clj \
  runtime/partial-inventory-live-20261106 runtime/partial-replay-new
```

Use new evidence directories: the runner refuses to overwrite trial directories.
The `live` command makes real provider requests. The companion runtime can be set
with `LAIN_RUNTIME_ROOT`; by default it is the sibling `lain-harness-runtime`.
The runner records both git revisions and the runtime's local diff summary. This
uses the configured runtime checkout, not a clean-upstream compatibility claim.

## Limits

Three identical fresh trials explore chosen strategies under optional guidance;
they do not establish that this guidance caused improvement. Optional observation
ports can be absent, and the evaluator records that absence instead of constructing
an answer for the model. Text may become unknown when its dependencies are
withdrawn. Definitions can remain installed while their supported results withdraw.
The intended term is **partial network construction and incremental completion**,
not compiler-level partial evaluation or specialization. Durable reopening and
cross-session retrieval remain deferred.

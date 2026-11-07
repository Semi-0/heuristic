# Clear runtime composition

The main definition remains in `soul-refined.lain`. Importing it only imports the
model and completed-response compiler definitions; it creates neither a worker
nor a request. Its two arguments are an environment interface and one model.

```lain
(define describe-environment (slot :request))
(define runtime
  (network [environment model]
    (let [e (lexical-child ((slot :bindings) environment))]
      (-> (describe-environment environment) (model:input model))
      (struct :environment e
              :model model
              :compilation (incremental-compile (model:output model) e)))))
```

The `:bindings` port contains the lexical parent. The `:request` port contains
an environment-derived request prepared by the seed; it can remain unknown until
a trigger arrives. The runtime returns the live child and admission receipt.
Request selection, phase changes and model creation remain in runnable entries,
seed modules and experiment modules. This interface avoids capturing seed globals
inside an independently imported runtime definition.

`lib/compiler.lain` now delegates full completed responses to existing
`source:admit`, preserving finish status, request identity, sequential form
receipts and prefix retention. Legacy parser/FSM helpers are isolated in
`lib/compiler-streaming.lain` for existing imports. The refined runtime uses the
existing worker and admission implementation; no core backend change is needed.

The default runnable entry is `examples/lain/conversation.lain`. It loads
`environments/conversation.lain` and the optional construction trigger in
`experiments/conversation-bootstrap.lain`. `soul-session/open!` loads this entry.
The original `soul.lain` and deployed service are untouched.

The print-shop composition is `examples/lain/printshop-runtime.lain`, with its
seed in `environments/printshop-three-stage.lain` and phase requests in
`experiments/printshop-three-stage-requests.lain`. Planning records remain outside
source admission; a model interface exposes only completed implementation replies
to the same runtime. All fifteen request sites share one reactive environment
view; each trigger captures its own request. Documentation examples are text,
not preinstalled question-solving networks.

## Verification

`LAIN_OFFLINE_UUID=1 tools/test_module.sh runtime-composition` passed one test
with nine assertions. It verifies definition-only import, independent children,
parent isolation, rejection of a truncated response, and a second admitted
response through the same live runtime. The test uses a local 300000-step /
180-second propagation allowance; production defaults are unchanged.

Legacy model behavior passed five tests and thirty assertions after import
separation. Large conversation-seed and print-shop validation results are recorded
separately; these focused checks alone do not establish full conversation success.

The staged default seed and acknowledged-delivery checks subsequently passed two
tests with fifteen assertions under that same test-local allowance. During the
split, defining `model:request` in the seed left its invocation disconnected from
the live worker across file scopes. Moving that binding next to `m` in the runnable
entry restored both setup and construction requests. The seed advertises the
resulting callable reference and documentation; the generic runtime remains clear.

The saved-response print-shop replay completed all fifteen phases through this
runtime, retaining one worker and child and the same `compute-quote` reuse on four
later questions. All 35 initial numeric fields still matched; the same fourteen
premise/mutation checks failed. The replay used zero provider calls and is evidence
of composition compatibility, not a second successful model construction trial.
Evidence: `runtime/printshop-runtime-replay-shared-20261107/`.

An earlier replay setup exhausted its 180-second propagation allowance before any
request, recorded under `runtime/printshop-runtime-replay-20261107/`. Sharing one
reactive description removed fifteen duplicate observers from the phase request
module. This is a fixture change; the main runtime and provider prompt policy are
unchanged. The default large-seed suite also exceeded its standard 30-second
budget during development; the passing staged tests use an explicitly larger
local allowance and do not establish default-budget performance.

The historical `compiler` module currently reports 23 passing assertions and one
error: its immutable-session fixture uses the old `specification-extension` loader,
which does not snapshot `model-refined.lain` when loading the current root entry.
The committed pre-refactor root already imports that file, so this indicates a legacy
loader mismatch, not a completed-response admission result. A full baseline rerun
was not performed. The diagnostic is
retained in `runtime/runtime-composition-20261107/legacy-compiler.log`.

The fresh provider condition uses `tools/run_printshop_runtime.sh live` with a
fresh `LAIN_EVIDENCE_DIR`. Replays use `replay` and `LAIN_REPLAY_SOURCE_DIR` pointing
to original provider evidence. Both load `examples/lain/printshop-runtime.lain`.
The first fresh request matches the pre-refactor request exactly after normalizing
reference UUIDs (21030 characters each); the comparison is saved alongside the
new evidence. Further contexts include actual planning, admitted code and results.

## Fresh provider trial after separation

The fresh trial stopped after three actual calls (analysis, generalization,
implementation for question one). Its planning record proposed `print-quote`, a
reusable network accepting order and pricing structs, with `shared-rule` attached
through `ttms-premise-closure`. That plan was not executed successfully.

The completed implementation response began ` (- discount))`, followed by English
self-discussion and then a proposed network definition. The provider marked the
response `stop`, not truncated. Its source text contained 5603 characters; the
separate thinking field contained 7326. The transport reads provider content and
thinking separately; this record shows prose present in the content/source field.
It does not establish why the provider crossed that output-format boundary.

Sequential admission accepted the initial `(- discount)` expression, retained its
receipt, then rejected the unmatched closing parenthesis. The later `print-quote`
definition and output connections were never admitted. There was no successful
quote or observed callable reuse in this trial. The experiment stopped without
manual source cleanup, repair prompts, retries or a replacement implementation.
The generic evaluator reports missing downstream checks; those are unexecuted
outcomes after the reader failure, not measured arithmetic errors.

Evidence: `runtime/printshop-runtime-live-20261107/`. See `generated-1.lain`,
`failure-summary.edn`, `initial-context-comparison.json`, and `integrity.edn`.
Phase order, unchanged planning records and implementation-only written history
were verified. There was no completed-round worker snapshot, so worker stability
is not independently established for this failed trial. The earlier complete
replay supplies that composition check separately.

The history integrity checker now compares written source with implementation
phase responses, including rejected source. Successfully admitted implementations
remain a separate count. This distinction matters when a response is written and
journaled but rejected before a complete implementation is available.

Large evidence files are preserved as verified gzip archives. See
[runtime artifact restoration](../runtime/runtime-composition-20261107/ARCHIVES.md)
for byte counts, hashes and the restore command before evaluating a fresh clone.

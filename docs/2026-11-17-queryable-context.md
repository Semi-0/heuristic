# Queryable context: implementation and negative pilot — November 17

## Final outcome — acceptance not met

The candidate context cycle is implemented in Lain and passes controlled checks.
The real-Qwen pilot **does not meet indexed-context acceptance**. No performance
benefit or reliability claim is supported. LifelongAgentBench remains paused.

The indexed model did not emit a retrieval decision. Its first continuation
response exhausted the output limit; its only correction guessed a three-argument
call to the four-argument pricing callable. That late application error terminated
the host. The run was preserved and not restarted. Full history delivered one
correct continuation with genuine callable reuse; later quote prompts exceeded
the input budget and were refused. Both isolated ablations remained unresolved.

| Condition | Outcome | New Qwen calls | Input tokens | Output tokens | Provider + idle-wait seconds | Corrections |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| Shared preparation | Initial quote correct; index valid | 3 | 15,559 | 10,557 | 270.4 | 1 |
| Indexed context | 0/4; q1 host failure, q2–q4 unreached | 2 | 12,230 | 12,464 | 319.6 | 1 |
| Full history | 1/4; q2–q4 budget-unresolved | 9 | 47,618 | 37,220 | 957.5 | 4 |
| Ablation: first continuation | Unresolved; index valid | 2 | 7,902 | 8,770 | 232.5 | 0 |
| Ablation: composition | Unresolved; index invalid | 4 | 19,203 | 18,267 | 475.8 | 2 |

Preparation is counted once. Its three complete responses were replayed in each
primary comparison session without new generation. The table excludes tokenizer
calibration (one call, 30 input / 38 output tokens). Total task/preparation cost:
20 calls, 102,512 input / 87,278 output tokens. Corrections are subsets of the stage
costs, not additional costs to sum again. Lower indexed cost reflects early failure
and unreached tasks, not efficiency. Output counts include reasoning tokens.

| Condition | Work/execution input / output | Indexing input / output | Correction subset input / output |
| --- | ---: | ---: | ---: |
| Shared preparation | 4,179 / 1,571 | 11,380 / 8,986 | 6,171 / 4,508 |
| Indexed context | 12,230 / 12,464 | 0 / 0 | 8,280 / 4,272 |
| Full history | 17,437 / 4,922 | 30,181 / 32,298 | 22,892 / 14,626 |
| Ablation: first continuation | 3,816 / 4,158 | 4,086 / 4,612 | 0 / 0 |
| Ablation: composition | 8,254 / 8,561 | 10,949 / 9,706 | 10,517 / 9,242 |

A valid index means its registration and evidence references passed the contract;
it does not certify the truth of agent-authored claims.

No real-provider work request returned a `:retrieve` decision. Queries/reads inside
indexing are counted as indexing. Full-history q2 indexing read its current task
and overflow observation; neither was missing from its initial indexing prompt.
The indexed condition has no demonstrated recovery of omitted evidence.

### Verification and limits

- Controlled suite: 14 tests / 60 assertions passed. Follow-up genuine/fabricated
  index-receipt controls: 2 tests / 8 assertions passed. Existing catalog and HTTP
  transport regressions: 5 tests / 30 assertions passed. These are focused suites,
  not a claim about the entire repository test suite.
- Exact Unicode/long-text archive round trips, conflicting identities, idempotence,
  compiled pagination, stale live references, bounded inspection, overflow,
  incomplete-response rejection, bounded correction/retrieval and omitted-source
  callable recovery were exercised through public session extensions.
- Serving-tokenizer count matched provider usage for calibration and every real
  task request. Largest sent prompt was 8,829 tokens. Full-history work prompts
  at 12,009 / 14,228 / 16,745 tokens were measured and refused before sending.
- Within the full-history session, one **new** application of the initial callable
  retained its declaration and captured environment, used `[240 true true 40]`,
  produced `533.30`, and had a graph connection to the delivered output. Application
  identity sets were compared against earlier snapshots within that same session,
  never across restorations. No successful indexed continuation reuse was observed.
- Indexed handling of changed assumptions and quote composition remains unverified:
  those tasks were unreached. Full-history versions of those tasks were budget
  rejections. Continuation indexing succeeded for 2/4 full-history tasks, 1/1 first
  ablation, and 0/1 composition ablation; indexed continuation indexing was unreached.
- All trials ran serially under the watchdog, waited for Qwen availability and
  retained failures. No interrupted trial was automatically restarted. Main model
  and runtime files, kernel, deployed services and LifelongAgentBench data were
  preserved. No commit or push.

### Interpretation and remaining issues

The controlled mechanism works; the real model did not reliably use its retrieval
or indexing contracts. Before another pilot, make the retrieval feedback contract
explicit (operation results become the next `working-evidence`), improve verified
single-operation query/projection and typed-metadata examples, and investigate
late application failure containment using supported session surfaces. Do not
supply a solved retrieval/pricing network or choose the operator from the host.
The common role wording says previous details are omitted even when the full-history
control supplies them; that wording also needs clarification before a new comparison.

Full history here is an uncompressed serialized transcript including indexing,
failed attempts and diagnostics, not an optimized chat-history representation.
Its overflow is evidence about this configured control, not a general superiority
claim for indexed memory. A successful future pilot would establish feasibility;
performance benefit would still require repeated matched sequences.

The indexed host failure exposed an observation-retention gap: its in-memory
operation journal was not exported on failure. Raw responses, HTTP outcomes,
admission journal and immutable Datomic artifacts survived. Offline reconciliation
archived the exact fatal trace as `:run/fatal` and reconstructed costs/status from
those artifacts; it did **not** restore a live network. The missing operation journal
is explicitly marked incomplete. Subsequent driver code flushes every catalog
journal append and archives terminal host failures. Archival retention remains
separate from live graph garbage collection or suspended-state restoration.

### Source and evidence

- [Lain context policy](../lib/context-policy.lain), [model environment](../environments/context.lain),
  [composition](../experiments/context.lain), [storage](../src/lain_harness/context_store.clj),
  [inspection](../src/lain_harness/context_inspection.clj), [IO/validation](../src/lain_harness/context_runtime.clj),
  [transport/tokenizer](../src/lain_harness/context_transport.clj), [trial driver](../tools/probe_context.clj).
- [Evidence directory](/home/lain/reports/heuristic-context-20261117),
  [machine-readable summary](/home/lain/reports/heuristic-context-20261117/summary.json),
  [core source equality](/home/lain/reports/heuristic-context-20261117/core-source-comparison.json).
  All conditions have matching core source hashes; the later driver change only
  improves retention. Each trial retains its frozen source and SHA-256 manifest.
- [Controlled checks](/home/lain/reports/heuristic-context-20261117/checks-28.log),
  [index receipt checks](/home/lain/reports/heuristic-context-20261117/checks-29.log),
  [regressions](/home/lain/reports/heuristic-context-20261117/checks-30.log),
  [offline reconciliation](/home/lain/reports/heuristic-context-20261117/recovery.log).
  Earlier failed/interrupted diagnostics remain below and in their original logs.
- [Offline reconciliation tool](../tools/recover_context.clj) and
  [summary/reuse evaluator](../tools/report_context.clj). One report invocation used
  the wrong working directory and failed before analysis; `report-final-2.log` is
  the successful corrected analysis. No model request was made by these tools.

## Historical implementation checkpoints

The checkpoints below preserve earlier states and are superseded by the final outcome above.

## Source and scope

Read the runtime's `how-to-think-in-lain.md` (including section 24), session API,
Heuristic AGENTS/design/status/plan/issues, catalog discovery, cumulative closures,
and source-restoration evidence. Inspected the catalog experiment composition,
`soul-refined.lain`, `model-refined.lain`, model transport and public extension API.

Starting Heuristic HEAD: `00c1eadb49582fcac26df1cd2a333ba7610b7755`.
Runtime checkout HEAD: `2773dffc1cf5a2b129559aa445d66d09f96d4dd7`.
Existing uncommitted documentation, Pi and scheduler work was preserved.
No deployed service, runtime kernel, main model/runtime composition, LifelongAgentBench
download, or previous experiment artifact was changed. No commit or push.

## Implemented draft

[context_store.clj](../src/lain_harness/context_store.clj) adds schema to the
existing Datomic catalog through `install!`, with a public `facilities` extension:

| Lain capability | Contract |
| --- | --- |
| `context:archive id kind text` | Exact immutable text; identical replay succeeds; conflicts fail |
| `context:read id offset limit` | Unicode codepoint offsets; up to 2,048 codepoints with continuation |
| `context:index metadata-text` | Agent claims, validated evidence references, explicit version/supersession edges |
| `context:query query-text rules-text cursor limit` | Existing restricted query grammar; up to 32 rows; pinned database basis |
| `context:resolve id` | Existing catalog live reference, checked against this owner's graph |
| `context:inspect reference cursor limit` | Immediate node value summary and up to 32 input/output edges |

All these operations dispatch through an injected `EffectInterpreter` after
commit, rather than performing storage IO during pure activation. Operation
errors become diagnostic records. No adapter selects a relevant operator,
constructs a model request, retries, restores missing references, or selects stages.
The legacy catalog registration now rejects reuse of evidence/claim identities.

Evidence records have `:catalog/author :observation`: this means an observed
artifact, not that its contents are true. Index records have `:catalog/author
:agent`; supporting references are required and registration never certifies
correctness. Existing callable metadata may be annotated without overwriting its
registered fields. Claims cannot overwrite evidence identities. Archives support
arbitrary text kinds, but automatic archival of each task artifact is **not wired**.

[context_inspection.clj](../src/lain_harness/context_inspection.clj) exposes callable
declaration/captured-environment identities and argument counts without expanding
them. Text values have a bounded prefix; structured values remain omitted behind
the reference. Edge continuations reject changed topology. Query continuations
retain their original database basis across later writes. Empty query results
explicitly do not establish absence. Archive chunk fields cannot be retrieved via
this query surface; callers use explicit chunk reads.

Scope limits: one serial owner/store writer per experiment; the locking contract
is not a coordinated multiprocess transaction protocol. Query pages bound returned
rows, but the adapter sorts the full matching relation in host memory. Evidence
reads currently reconstruct stored text before slicing it. Neither is a claim of
bounded database working memory. Journals and archived evidence deliberately grow.
No live graph garbage collection or suspended-state restoration is introduced.

## Verification checkpoint

Added [controlled tests](../test/lain_harness/context_store_test.clj) for compiled
archive/read/index/query/inspection, exact long Unicode round trips, replay and
conflicts, invalid references, stale live references, immutable claim versions,
complete duplicate-free pagination across later writes, and effects waiting for
commit. These tests have **not run**.

The watchdog status read returned healthy/admission true. The registered launcher
then failed before starting a JVM because the sandbox cannot connect to the user
systemd bus: `Failed to connect to bus: Operation not permitted`. Attempted unit:
`lain-test-5b2d820abb6440e6acca69a1e1fb1703.service`. Launcher exit: 2.
This is an infrastructure block, not a test failure or a model failure. No bypass,
automatic restart, or provider request occurred. No controlled assertion passed
in a JVM. Lightweight delimiter checks and `bash -n` passed; these do not establish
Clojure compilation or runtime behavior.

Reproducible guarded check, from Heuristic:

```sh
bash tools/run_context_checks.sh
```

The launcher reserves 2,048 MiB / two CPUs and gives the JVM a 1,200 MiB heap.
Run only in an environment permitted to use the watchdog's systemd launcher.

## Remaining implementation and acceptance

1. Run/fix the controlled storage and topology checks before composing model work.
2. Add the Lain-owned context policy: task/archive dependencies, query/inspection
   results as environment bindings, four retrieval rounds, execution, validation,
   post-task indexing, one diagnostic correction per stage, unresolved outcomes.
3. Add concise language explanations and **verified** operation examples; do not
   include a solved retrieval/pricing network. Retain role, task, capabilities and
   latest diagnostic in every request, evict historical detail at task boundaries.
4. Wire matching serving-tokenizer counting including chat template; verify it
   against provider usage before enforcing 12,000 input tokens. Preserve 8,192
   output tokens and the existing Qwen thinking configuration. Count failure must
   prevent sending. No tokenizer verification or budget enforcement exists yet.
5. Implement shared Qwen preparation, source restoration into independent matched
   full-history/indexed sessions, two targeted ablations, and the five print-shop
   tasks with irrelevant/obsolete entries and changed assumptions. Do not name the
   reusable callable in questions or inject the retrieval answer from the host.
6. Record all source, responses, receipts, HTTP outcomes, observations and costs;
   assess callable declaration/environment reuse **within** each session and its
   connection to delivered output. Gate serial real calls on Qwen availability.

Correctness/reuse/tokens/latency/repairs/indexing costs: **not measured**.
Controlled calls: 0. Real Qwen calls: 0. Completed continuation tasks: 0.
Keep LifelongAgentBench expansion paused; its earlier adapter timeout and failed
controls remain separate. A successful pilot would establish feasibility only;
performance benefit requires repeated matched sequences.

## Resumed verification — November 17

The user enabled host access and requested continuation. The exact reservation
left by the sandbox-denied launch was reconciled after observing its unit absent,
inactive and MainPID zero. Evidence: `/home/lain/reports/heuristic-context-20261117/launch-reconciliation.json`.
No service was restarted. The first guarded JVM attempt exposed a launcher
classpath omission. The corrected launcher passed four storage/inspection tests,
30 assertions (`checks-2.log`). This supersedes the earlier verification block.

Lain policy, environment and native measurement adapters are now under controlled
integration testing. Initial syntax issues and a 30-second propagation deadline
failure are preserved in `checks-3.log` through `checks-5.log`; these are not model
failures. No real Qwen acceptance request has been made. The test session allowance
is being increased to 180 seconds while checking finite topology construction.

Controlled integration `checks-6` was deliberately stopped after capturing thread
dumps: seven minutes elapsed, no model requests, timeout diagnostics, and sustained
named-network comparison during propagation. The owned watchdog unit closed with
exit 75 / interrupted true, not a pass. This run was not restarted. Subsequent
checks use source snapshots and a narrower diagnostic, with environment/model
references replacing whole interface values at orchestration boundaries. This is
an implementation investigation; no kernel defect or fix is established.

## Request preparation checkpoint

`checks-7` was deliberately interrupted (exit 75); checks 8–15 are retained
failed integration diagnostics except the passing isolated effect-chain and
measurement-chain checks 10 and 14. No provider calls occurred. Check 16 failed
to launch because the script was invoked without bash. Check 17 passes the
compiled request-only test: exact request archival followed by token measurement,
with zero provider calls. The policy now takes `cell:first` snapshots of async
receipts before field projection. Check 18 tests the complete single attempt;
acceptance remains pending.

Full-attempt checks 18 and 19 terminated with propagation-budget exhaustion
before any provider request (60-second propagation allowance, 1,952 and 1,987
steps respectively). Scoped declarations alone did not resolve construction
cost. Check 20 tests a separate execution network behind the counted packet.
All are frozen controlled sources, not retries of interrupted provider trials.
The retrieval allowance is now four retrieval outputs plus a final execution
attempt; boundary journals retain inspection and resolution results explicitly.

Check 20 passed the isolated complete attempt (1 test, 2 assertions). Check 21
finished: 11 tests, 40 passing assertions, 2 errors. Storage, request preparation,
isolated admission/validation and overflow controls pass; complete task/indexing
and incomplete-response/correction controls time out. Stage-boundary one-time
snapshots are now being checked in focused check 22. No Qwen calls yet.

Check 22 reached and counted the indexing request but did not send it. Check 23
passed the complete task/index cycle (1 test, 5 assertions) after giving the
model one shared temporal ingress. Each attempt's unchanged main runtime
publishes its request through a local port into that ingress. Stage snapshots
also remain necessary. Check 24 failed while reading a new retrieval test due
to an extra closing delimiter; fixed before check 25 (full controlled suite).
No real provider calls have occurred at this checkpoint.

Check 25 passes: 12 tests, 53 assertions, zero failures/errors. This includes
complete indexing, source eviction with catalog query/live-callable application,
incomplete-response rejection with one correction, and overflow without sending.
Check 26 adds complete chunk round trips and pagination through compiled Lain,
stale resolution through the public extension, and validates registration targets
as live callables. It runs serially under the watchdog. Tokenizer preflight and
Qwen preparation remain unstarted.

Check 26 had a delimiter error in the added pagination test; check 27 passes
13 tests / 56 assertions after correction. It verifies complete compiled chunk
round trips, complete compiled pagination, stale live-reference rejection and
live-callable registration. Check 28 adds the retrieval-limit terminal outcome
and checks conversation retention for the full-history condition. Historical
working evidence and diagnostics are retained there once, without recursively
embedding earlier full history. Every real request will also compare the serving
token count with provider usage after preserving its response.

Check 28 passes all 14 tests / 60 assertions, including termination at the
retrieval limit. Check 29 passes 2 focused tests / 8 assertions for positive
indexing and rejection of fabricated index receipts: a receipt must identify
an actual stored evidence-backed claim. No kernel change.

Tokenizer preflight queued 2026-11-17T00:42:42.962982+00:00 under
the watchdog, evidence `tokenizer/` and `tokenizer.log` in the report directory.
This is one real-provider calibration call, separate from shared preparation and
comparison costs. Preparation and comparison have not started yet.

Tokenizer preflight terminated with `/tokenize` HTTP 400 before any generation
call. The equivalent read-only payload succeeds with HTTP/1.1. The measurement
adapter is corrected to use the same explicit HTTP/1.1 protocol as the model
transport. A new preflight uses `tokenizer-http1/`; this is a corrected calibration,
not a restarted interrupted trial. Initial error report is retained beside its log.

Tokenizer calibration passed: 30 counted input tokens = 30 provider prompt tokens,
HTTP 200, 38 output tokens (34 reasoning), Unicode and matching chat template.
The successful calibration is separate from task costs. Shared preparation queued
2026-11-17T00:43:50.663733+00:00 in `preparation/`, serial under
the watchdog. Its Qwen source and indexing response will be replayed into fresh
comparison sessions; later callable comparisons are strictly within each session.

Preparation construction delivered $212.76 correctly. Initial indexing emitted
record-shaped evidence references instead of lookup-reference vectors; native
registration rejected them and Lain requested its single diagnostic correction.
The failed source/receipts remain archived. Input counts so far: construction
4,179, indexing 5,209, indexing correction 6,171; all within 12,000.

Shared preparation completed successfully: correct initial quote, valid index
after one bounded correction, three Qwen calls. Cost: 15,559 input tokens,
10,557 output tokens; provider/availability elapsed 270.388 seconds, total guarded
process 5m06s. Every prompt was below 12,000. Original failed index remains archived.
Indexed condition queued 2026-11-17T00:49:38.100227+00:00 in
`indexed/`. It replays the three preparation responses through public admission
into an independent session, then evicts prior source/conversation at each task
boundary. The replay generates no new q0 provider calls.

Indexed restoration replayed all three preparation responses successfully. Its
first continuation (`q1-work`) exhausted 8,192 output tokens, finish reason
`length`, and admitted no source. Its reasoning/content speculated about the
callable interface instead of emitting a retrieval record. Lain requested one
correction with the rejected response and diagnostic (8,280 input tokens). This
failure is preserved; the condition continues under its declared bounded policy.
No host answer, operator selection, extra correction or source replacement was
injected. The initial continuation prompt had only 3,950 input tokens.

Indexed condition terminated with a host error after its q1 correction,
`Closure application has invalid arity`: Qwen resolved the four-argument callable
but called it with three arguments without retrieving its interface/evidence.
The corrected response was complete; this was a late application failure after
resolution, not output truncation. No q1 answer or q2–q4 continuation was delivered.
The host closed cleanly. This condition is terminal and will not be restarted.
Raw responses, HTTP usage, admission journal, fatal trace and persistent Datomic
archive are retained in `indexed/`. Full-history control is independently running
from the same preparation, with the same model/settings/policy and task sequence.

This exposes an observation-retention gap: operation journals were exported only
on successful completion. Future runs now flush the catalog journal on every
append and archive fatal host observations before closing. This changes retention,
not model instructions or Lain phase decisions. Existing failed evidence will be
exported from its retained database; no live references or network are restored.

Full-history q1 delivered the correct $533.30 quote after one correction for an
undefined result binding, reusing the restored callable with four arguments.
Its indexing stage also used its one correction. Q2's work prompt measured
12,009 tokens and was refused before transport; indexing the unresolved outcome
continues with 3,913 tokens. This is a successful budget guard, not a completed
quote or a budget violation by a sent request.

Full-history control completed and closed cleanly: q1 correct, q2–q4 unresolved
from input-budget rejection at 12,009 / 14,228 / 16,745 measured tokens. All sent
requests stayed within budget. Continuation indexing: q1 invalid, q2 valid after
correction, q3 invalid, q4 valid. Nine new provider calls; q0's three responses
were replayed. Guarded duration 21m07.864s, peak memory 1.4G.
Ablation q1 queued 2026-11-17T01:19:39.260264+00:00 in `ablation-1/`,
with no preparation source, prior evidence, callable or distractor entries.
Ablation q4 remains queued after it. No interrupted trial is being restarted.

Ablation q1 completed cleanly: unresolved without a numeric answer, valid failure
index, no correction, two provider calls. Cost 7,902 input / 8,770 output tokens;
232.531 seconds provider/availability elapsed, 4m20.630s guarded duration.
Ablation q4 queued 2026-11-17T01:24:03.612418+00:00 in `ablation-4/`,
again without prior evidence or callable access.

Ablation q4 completed and closed cleanly: unresolved, invalid index after one
work correction and one index correction, four provider calls. Its first complete
response contained malformed syntax after a catalog-query prefix; correction
then used an absent callable and invalid query assumptions. No quote was delivered.
All sent requests remained within budget. Guarded duration 8m38.730s.
All real-provider work is terminal. Catalog and HTTP regression check 30, offline
failure reconciliation and aggregate reporting follow; no further Qwen calls.

## November 18 follow-up

[Prompt repair diagnostics](2026-11-18-context-prompt-repair.md) demonstrated omitted-evidence retrieval and one correct continuation without callable reuse. Full acceptance remains unmet; the original pilot above is unchanged.

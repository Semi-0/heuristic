# Current status and evidence

The question-rooted context prototype has run serial and interleaved real-model trials against a full-history control. The report records compiler and TTMS outcomes, question latency, and recognition plus answer costs. [Experiment and results](2026-11-26-dependency-context.md)

Updated 2026-11-14. Reviewed source: heuristic `00c1ead`; historical trials retain their own runtime pins. This document summarizes recorded evidence; no model or runtime test was rerun for this documentation update. [Design](DESIGN.md) · [Plan](PLAN.md) · [Issues](ISSUES.md).

## What exists

The harness has a working Lain-owned environment → model → compilation path and bounded demonstrations of executable reuse, discovery, source-based persistence, dependency-aware updates and repair. It is not yet a generally reliable long-horizon harness.

The following 12 groups are a documentation inventory, not equal-weight completion percentages. Eleven have implemented mechanisms and experimental evidence; the broader lifecycle/scaling group remains partial. Evidence ranges from controlled checks to real-model trials, as specified below.

| Capability | Evidence and qualification |
| --- | --- |
| Single Lain orchestrator and existing model worker | Real staged trials; historical host-controlled trials are not current-design acceptance |
| Environment-supplied context and capabilities | Real trials use lexical bindings; exact contents depend on the seed |
| Completed-response admission and receipts | Controlled and live evidence; rejected forms preserve successful prefixes |
| Sequential questions, history and reply connections | Arithmetic and print-shop sequences; supplied infrastructure is not agent construction of a complete conversation network |
| Value, output and immediate-edge inspection | Used by real repair trials; full causal provenance remains incomplete |
| Generated callable reuse | Five-question print-shop: 15 real calls, four retained-callable reuses, 84 numerical/dependency/mutation checks plus 9 stability/history and 8 integrity checks |
| Datalog discovery and composition | Real catalog and cumulative composition trials; no general hybrid semantic retrieval claim |
| Durable source restoration | Separate process restores selected source/dependencies; directed accumulation producer and consumer each report all 16 acceptance flags true, with conditional flags identified |
| Dependency-aware reuse | Thinking-enabled changed-policy trial 41/41; two earlier non-thinking trials 30/41 |
| Missing-output repair | Real correction 23/23, one provider correction after replayed setup; fresh three-call trial also 23/23 without repair |
| Semantic candidate validation/repair | Real correction 47/47 after replayed setup; fully fresh three-call integration 43/43 without repair |
| General lifecycle, scalability and long-term management | Partial mechanisms; sustained reliability, safe durable effect replay and long-horizon benefit not accepted |

These are successes under recorded conditions, often after failed attempts or context revisions. Do not aggregate assertions into a reliability rate or claim the full repository suite is green.

## Primary reports

- [Arithmetic and premise-supported networks](2026-11-06-arithmetic-reuse-and-premises.md).
- [Single-Lain five-question continuation](2026-11-10-autonomous-repair-experiment.md).
- [Catalog discovery](2026-11-10-catalog-discovery-experiment.md) and [cumulative composition](2026-11-10-cumulative-catalog-closures.md).
- [Source restoration](2026-11-10-durable-source-restoration.md) and [agent-directed durable accumulation](2026-11-11-agent-directed-durable-accumulation.md).
- [Missing-output repair](2026-11-11-missing-output-repair.md).
- [Dependency-aware catalog reuse](2026-11-13-dependency-aware-catalog-reuse.md).
- [Semantic validation and bounded repair](2026-11-13-semantic-validation-repair.md).

## Current cumulative-history pilot

[Compact report](2026-11-14-cumulative-history-pilot.md). Both the historical retained arm and the fresh-only rerun now have 3/3 correct outputs; neither produced a reusable callable. Cumulative reuse remains unproved. Earlier source/callable reuse remains demonstrated; this pilot asks a different question about less-guided reuse on benchmark questions.

Original system-only requests reproduced HTTP 400, `No user query found in messages.` Corrected Lain seeds add a user message from current-task. Controlled checks passed; the first corrected real attempt sent no recorded HTTP call and was ultimately stopped by the thermal watchdog. `corrected-2` is now terminal: retained produced three admitted correct answers (three HTTP 200 responses), but no reusable callable. Later requests contain earlier generated source. Fresh made no recorded HTTP call and rejected an incomplete response before source admission; idle timeout is likely but not definitively captured. Runner exit 0 does not override fresh child exit 1. The completion notification was received and reviewed; no restart. See the [pilot review](2026-11-14-cumulative-history-pilot.md#terminal-review--2026-11-15).

## Verification boundary

November 13 reports record focused session API (36 assertions), runtime composition (15), source-selection checks where applicable, and formatting/lint checks. These are recorded runs, not checks repeated today. Backend/editor CI is separate from harness acceptance. The old experimental kernel variant is reverted and not an accepted dependency.

The [November 8 evidence map](research-status-2026-11-08.md) preserves corrections to earlier conclusions and later additions. The [archived root README](historical-root-readme-2026-11-14.md) preserves early migration/behavior measurements; its old current-status language is superseded here.

## Completion notifier — November 14

The [small scheduler](../tools/scheduler/README.md) is deployed as a bounded user service attached to corrected-2. Compiled policy tests cover all 64 Boolean input combinations; notification warning/replay controls pass. A real finished-test receipt led to Lain selecting wake and the installed Codex CLI accepting a message with --resume, with no warning. The queued smoke event subsequently arrived as a new user-channel turn after the implementation turn ended; the agent read its receipts and updated these documents. This verifies deferred delivery and follow-up review for this thread. Enqueueing into an already idle/disconnected client and recovery across daemon restarts remain untested. Evidence is local under `/home/lain/reports/heuristic-scheduler-20261114/`.

## Shared-clock queue run — November 15

The [executable live-queue draft](../tools/scheduler/live_queue/README.md) passed a controlled run on 3c3f643: one real clock, three tasks (one appended through session extension), exactly three notification attempts, receipt acknowledgment, replay suppression, continued ticking and owner-close cleanup. Alert selection and queue traversal run in Lain; notifications use a recording capability. This draft is not deployed and does not establish durable queue lifecycle or long-run retention. The earlier deployed monitor remains unchanged.

## Real queue IO and lifecycle follow-up — November 15

[The full IO run](../tools/scheduler/live_queue/FULL-RUN.md) passed using real receipt files and one Codex CLI notification. Extension replay, duplicate queue entry and a fresh owner with saved acknowledgment caused no duplicate send; closed-owner callbacks were rejected. The queued self-test event subsequently arrived in this conversation and both receipts were reviewed. The shared-clock version remains undeployed. That map-list version retains completed watchers; the disposable-session map-task implementation below supersedes the earlier proposal, but does not remove parent queue nodes.

## Disposable task scopes — November 15

Implemented and tested [map-task](../tools/scheduler/map_task/README.md) on clean runtime `3c3f643`, without kernel edits. The final controlled run passed: one shared clock; four child sessions created/retired and listeners subscribed/unsubscribed; three expected alerts; no polling after retirement; extension replay and duplicate completed identity created no extra child; a late callback returned `:closed`; parent shutdown cleaned up an unfinished child. All four weak owner references and final network references cleared after explicit GC. Formatting/lint: zero errors/warnings. This is controlled IO, not a new real Codex delivery trial or deployment. Evidence: `/home/lain/reports/heuristic-scheduler-20261115/map-task-2/`.

### Plural operator name and parent GC boundary

The public operator is now [`map-tasks`](../tools/scheduler/map_task/runtime.lain).
This symbol-only rename does not change the measured child cleanup behavior or
add parent-entry GC; previous tests used the singular name. Code review found
whole-session retirement/replacement but no public selective graph deletion API.
Complete per-task reclamation requires changing ownership so parent per-task nodes
are disposable too, or a runtime scope-removal facility. Whole-parent replacement
would retire its clock and active effects, so it was not substituted silently.
Kernel remains unchanged; no deployment or new JVM run.

## Disk-backed map-tasks effect — November 15

The [disk-backed implementation](../tools/scheduler/disk_tasks/README.md) supersedes
the map-list prototype. One managed parent effect admits bounded child sessions
from a disk queue; Lain workers choose completion/deadline, notification and
acknowledgment. An acknowledged completion record is synced to disk before the
child is retired. Completed identities are checked on disk, not accumulated in a
RAM index or parent result cell. The queue producer is a capability, not per-task
parent source extension. Public session APIs only; kernel unchanged.

Final controlled test on runtime `3c3f643`: 10 completed tasks, parent unchanged at
595 graph nodes and 2 effect receipts, zero live sampled weak owners/networks.
A concurrent waiting task did not block a deadline alert. Fresh-owner completed
replay was suppressed; unfinished disk records were not automatically restarted.
Injected persistence failure retained the active task. Format/lint: 0 errors and
0 warnings. Guarded combined job exited 0, 50.240 s wall, 48.698 s CPU, 288.9 MiB
peak. Evidence: `/home/lain/reports/heuristic-scheduler-20261115/disk-tasks-2/`.

This is controlled IO, not real notification integration or deployment. Remaining
gates: real durable notification adapter; process-kill/restart and reconciliation
tests; coordinated multiprocess producers; queue fairness and disk retention;
long-lived active-task growth and sustained-load measurements. Directory iteration
is not FIFO, concurrency defaults to four, diagnostics retain 16 samples. No claim
of whole-JVM bounded memory or exactly-once external delivery.

## Production replacement — November 15

The [single-writer disk scheduler](../tools/scheduler/disk_tasks/README.md) now has
real notification integration and deployment acceptance tests. Producers submit
immutable inbox requests; one effect executor owns queue changes. Priority then
admission-time ordering is explicit. Lain workers renew after 900 seconds, keeping
notification claims on disk and leaving the shared clock alive. Completed records
are intentionally retained, not deleted; six tasks measured 3,024 logical bytes
for archive plus replay records (504 bytes/task; 8 KiB/task filesystem allocation).

Tests passed: ten producer processes yielded six unique ordered deliveries; fresh
processes suppressed completed replay; SIGKILL during a synthetic send quarantined
the task; explicit operator-confirmed reconciliation completed it without resend.
Two controlled renewals produced one eventual alert. Parent remained 595 nodes /
2 receipts, and sampled retired networks were collected. Python Ruff and Clojure
format/lint passed. One real Codex smoke queued message
`01a1dcb9-aba0-75c3-8f00-1378658ae815` with exit 0 and no warning, persisted the result
and retired the child. The queued self-test subsequently arrived in this conversation and its recorded result was reviewed. This confirms delivery and follow-up in this thread, not disconnected-client or daemon-restart recovery.

The replacement service uses the same name, `heuristic-experiment-scheduler.service`,
with config `/home/lain/.local/state/lain-scheduler/config.json`. It monitors only;
no experiment-launch capability. The previous pilot is finished and not requeued.
Deployment health/result and backup unit are under
`/home/lain/reports/heuristic-scheduler-20261115/deployment/`. Read its result.json
for confirmed service state; pre-deployment historical sections above are superseded.

Remaining operational boundaries: ambiguous external delivery still needs verified
operator evidence; finite tests do not prove lifetime reliability or power-loss
behavior; priority can starve low-priority work; large backlogs and slow IO add
latency. Disk records deliberately grow. No kernel changes, commits or pushes.

## Lain ingress and separate observers — November 15

[The reactive scheduler](../tools/scheduler/reactive_scheduler/README.md) adds actual
`receive-tasks` and `observe-scheduler` networks while reusing the task runner.
New commands are versioned values from `inbox:subscribe`; Lain connects them to
`queue:enqueue`, then its admission receipt to `inbox:acknowledge`. Native runner
polling no longer reads/admit the inbox. The write effect waits for the exclusive
runner lease. `map-tasks` still selects the registered `:watch-task` Lain module;
it is not an arbitrary closure-taking operator.

Independent queue/runtime observation streams connect to `log:append`. Logs are
immutable JSON event files under `/home/lain/.local/state/lain-scheduler/queue/log/`.
A compiled negative control kept a request untouched without ingress; adding the
network admitted it. Attaching observers later drained prior events. An injected
sink failure did not stop a task. Command replay and duplicate-runner rejection
passed. The real smoke logged runtime-start, queue-admission, worker-create and
worker-retire, and queued one Codex message without warnings.

Retention is explicit: ten further tasks kept the warmed graph at 1459 nodes but
raised receipts from 11 to 61. Post-GC heap grew about 0.5 MB in this short controlled
run (not a long-run slope); serialized network growth was about 0.85 MB. Per user
preference, this small live history and disk records are retained; no bounded-parent
history claim. Observer failure retains the event until restart. Worker renewal,
priority among admitted tasks, durable replay and manual ambiguity reconciliation
remain unchanged.

Evidence: `/home/lain/reports/heuristic-scheduler-20261115/ingress-final/`,
`ingress-lease/`, `ingress-real/`. Formatting/lint, concurrent producers, fresh-process
replay and crash/reconciliation checks passed. Deployment health/rollback is in
`ingress-deployment/`; the previous disk scheduler is preserved. No experiment
launch/restart, kernel change, commit or push.

The ingress self-test event subsequently arrived in this conversation. Its successful receipt and four observer events were reviewed; production status showed an advancing clock, zero active tasks, and no scheduler/observer errors. This confirms follow-up delivery in this thread, not universal recovery guarantees. No experiments were launched or restarted.

## Fresh-only pilot follow-up — 2026-11-15T06:24:49.404387+00:00

The user authorized one fresh-arm rerun using the same corrected seed and settings.
It was registered as `lain-test-b0893239ea334e73b58ff95b0552ca6c.service`. The initial
idle-wait checkpoint is superseded by the completed terminal review below.
Per-call idle timeout is six hours instead of fifteen minutes; a separate copied
probe driver allows nineteen hours for the arm, preserving the ten-minute HTTP
timeout and all generation settings. The wrapper now records full exceptions from
both idle wait and transport. The harness/seeds remain byte-identical to corrected-2.

Retained will not be rerun: its historical 3/3 correct result is the comparison arm.
It produced no reusable callable; q1/q2 constructed separate networks, so cumulative
reuse is not yet shown. Evidence and automatic side-by-side report:
`/home/lain/reports/heuristic-cumulative-20261114/fresh-rerun-20261115/`.
The deployed Lain scheduler watches the terminal receipt. No automatic retry,
watchdog reset, kernel/heuristic implementation change, commit or push.

## Fresh-only pilot terminal review — November 15

The single authorized fresh rerun completed: three HTTP 200 responses with `stop`,
three admitted results, answers 3611 / 89 / UNKNOWN, 3/3 correct, zero reusable
callables, clean owner closure. Historical corrected-2 retained also had 3/3 correct
and zero reusable callables; it was not rerun. Retained q1/q2 built separate networks.
Thus correctness is demonstrated for both arms, but cumulative callable reuse and
a retained-history benefit are **not** demonstrated. The earlier fresh failure
remains a separate no-call attempt.

Seed/harness hashes and recorded generation settings were independently verified
unchanged. Six-hour idle limits and full exception capture were installed; no
exception occurred. Job receipt: exit 0, not interrupted, 12 min 19.995 s wall,
56.279 s client CPU, 432.5 MiB peak client memory. These exclude server resources;
noncontemporaneous arm timings are not a performance comparison.

Side-by-side report:
`/home/lain/reports/heuristic-cumulative-20261114/fresh-rerun-20261115/RESULTS.md`.
The scheduler delivered the terminal event and receipts were reviewed. No additional
attempt, restart, kernel change, commit or push. Next scientific question is whether
a task/context elicits a reusable abstraction; no new experiment is authorized here.


## Staged cumulative pilot — November 15

Staged pilot prepared; controlled two-arm wiring check running. Real-provider acceptance pending. No 150-item run launched. Evidence and executable Lain: [/home/lain/reports/heuristic-cumulative-20261114/staged-20261115](/home/lain/reports/heuristic-cumulative-20261114/staged-20261115/README.md).

Checkpoint 2026-11-15T08:50:17.768948+00:00: controlled retained/fresh checks passed (nine stages each; shared versus separate callable identities verified). Real-Qwen two-arm pilot launched as `lain-test-5b43220bebde45628f6a4dad4867c960.service`; acceptance and scale gate pending.


## Staged pilot terminal review — November 15

Staged pilot terminated unsuccessfully: retained 7/9 calls, fresh 5/9; all HTTP 200/stop. Planning format failed at retained q3 analysis and fresh q2 generalization. No 150-item run launched. See [reviewed evidence](/home/lain/reports/heuristic-cumulative-20261114/staged-20261115/RESULTS.md). Prior running checkpoints are superseded.


## Print-shop scale campaign — November 16

Print-shop scale campaign prepared; retained/fresh controlled compatibility checks running. Real campaign not started yet. Planned 150 questions per arm in 30 independent five-question episodes. [Frozen plan and evidence](/home/lain/reports/heuristic-printshop-scale-20261116/PLAN.md).

Scale checkpoint: control run active (known first quote 212.76 observed); historical saved source matches copied baseline. Lain scheduler completion review registered. Real 150-question-per-arm campaign has not yet started. [Checkpoint](/home/lain/reports/heuristic-printshop-scale-20261116/CHECKPOINT.md).


### Scale launch checkpoint — November 16

Compatibility controls passed (retained 5/5 quotes, four later callable reuses; fresh 1/1; all fields/premises correct). Real campaign launched at 2026-11-16T00:04:35.966877+00:00 as `lain-test-eabf07756a6f4dcc9c7c59d2dac3953c.service`. 150 questions per arm, 30 five-question episodes; real acceptance pending. [Evidence](/home/lain/reports/heuristic-printshop-scale-20261116/CHECKPOINT.md). Earlier prepared/control-running checkpoints are superseded.


## Print-shop scale terminal review — November 16

Print-shop scale stopped early on evaluator EDN parsing after 92 evaluated / 93 executed sessions; no watchdog interruption. Real retained reuse observed in 33 later rounds across 10 episodes. Matched correctness: retained 49/75, fresh 60/75. No restart; launch/running checkpoints superseded. [Reviewed report](/home/lain/reports/heuristic-printshop-scale-20261116/RESULTS.md).


## Pi model extension — November 16

Separate Pi model session extender implemented and controlled checks passed. Small direct-versus-Pi five-question comparison launched at 2026-11-16T22:06:23.381399+00:00; real results pending. See [implementation and evidence](2026-11-16-pi-model-extension.md).


## Pi comparison terminal review — November 17

Pi comparison completed and reviewed: direct 5/5 correct, four later quote-pricing reuses, one implementation repair. Pi made six successful inspection calls but stopped at question-one generalization on string ":construct" instead of keyword :construct; no source admitted. No improvement demonstrated; no rerun. [Full evidence](/home/lain/reports/heuristic-pi-20261116/RESULTS.md). Previous running checkpoints are superseded.

## Queryable context — November 17 checkpoint

Partial draft only; [report](2026-11-17-queryable-context.md). Storage/inspection
extensions and controlled tests are written. The watchdog launcher could not
connect to the systemd user bus from this sandbox (exit 2); no JVM or Qwen call
started. Static delimiter and shell syntax checks are not runtime verification.
Full context-cycle implementation and acceptance remain incomplete.

### Queryable context resumed — November 17

The host-access block is resolved. Four controlled storage/inspection tests pass
30 assertions; the new Lain context policy is undergoing controlled integration
checks. Initial policy installation exceeded its 30-second propagation allowance.
No Qwen acceptance trial yet. [Checkpoint](2026-11-17-queryable-context.md).

Request preparation now passes a compiled controlled check (`checks-17`), with
archive-before-count and no provider calls. Full attempt/cycle checks and real
Qwen acceptance remain pending; prior failed diagnostics are preserved in the
[dated report](2026-11-17-queryable-context.md).

Complete task/index cycle passes focused check 23 (5 assertions). Full controlled
suite and a new evicted-source/live-callable retrieval check are running in
check 25. The composition now has one shared model input, with per-attempt
lexical environments and immutable stage snapshots. No Qwen call yet.

Check 25 passed all 12 controlled tests / 53 assertions. Check 26 verifies the
remaining compiled chunk/pagination and stale-reference cases. Real Qwen
preparation remains gated on tokenizer verification; no provider call yet.

Controlled gates pass: check 28 (14 tests / 60 assertions) and check 29
(positive/forged-index controls, 2 tests / 8 assertions). Serving-tokenizer
preflight is now running under the watchdog; real preparation is queued after it.

Tokenizer calibration passed (30 measured = 30 provider input tokens). Initial
HTTP/2 observation failure is preserved; HTTP/1.1 adapter matches the established
model transport. Shared Qwen preparation is now running, with no comparison
continuations started yet.

Shared preparation succeeded: correct initial quote, valid index with one
correction, 3 provider calls. Indexed continuation condition is now running
from source replay in its own session. Full-history and two ablations remain.

Indexed condition failed terminally on continuation 1: truncated first response,
then an incorrect-arity application in its sole correction caused host failure.
Continuations 2–4 were unreached; indexed acceptance is not met. Full-history
control is running independently. No indexed restart.

Full-history control completed: 1/4 continuation quotes correct, 3 budget
rejections, 9 new provider calls; all sent requests within budget. Continuation
indexing succeeded for 2/4. First targeted ablation is running in an empty prior
context; the second follows serially. Indexed acceptance remains failed.

First ablation completed: unresolved quote, valid index, 2 provider calls and no
repairs. Second ablation (final composition question) is now running without
prior evidence/callable access. Cost/reuse reconciliation and final regressions
follow after it; no more real trials are planned in this pilot.

All planned Qwen runs are terminal. Final ablation: unresolved, invalid index,
4 calls and two bounded corrections. Regression checks and offline evidence/cost
reconciliation are in progress. No further generation or trial restart planned.

## Queryable context final — November 17

Implementation and controlled verification are complete; **real indexed acceptance
failed**. [Final report](2026-11-17-queryable-context.md).

- Shared preparation: correct initial quote and valid index after one correction.
- Indexed: first continuation failed after its one correction; three later tasks
  were unreached. No restart and no successful omitted-evidence recovery.
- Full history: one correct continuation with measured original-callable reuse;
  three quote requests were refused at the 12,000-token input boundary.
- Ablations: both unresolved; the first indexed successfully, the second did not.
- Context checks 14/60, focused index checks 2/8, and catalog/HTTP regressions 5/30
  all pass. Tokenizer/provider counts agree. Every sent prompt stayed in budget.
- All runs are terminal and closed. Failure reconciliation and cost/reuse analysis
  are complete. Twenty task/preparation Qwen calls plus one calibration call;
  no performance or reliability benefit established. LifelongAgentBench stays paused.

No commit, push, deployment or kernel change. Frozen sources, failed controls,
HTTP records, immutable archives and source/reuse observations remain in
[/home/lain/reports/heuristic-context-20261117](/home/lain/reports/heuristic-context-20261117).

## Context prompt repair — November 18

See [repair and verification](2026-11-18-context-prompt-repair.md). Prompt feedback,
history wording and operation examples were corrected. Focused checks passed
(16 tests / 67 assertions, plus 1 test / 4 assertions after stronger reuse wording).
The first Qwen diagnostic retrieved omitted source and delivered $533.30 with a
valid index but rebuilt arithmetic. The second exhausted retrieval; no callable
reuse is demonstrated. Full acceptance and performance benefit remain unproved.
Remaining work: explicit retrieval allowance in the model environment, reliable
retention of agent-selected evidence, query/index contract compliance, and late
application-error containment through public session surfaces. Then repeat the
matched sequence; LifelongAgentBench remains paused.

The [2026-11-21 planner delegation pilot](2026-11-21-planner-delegation.md) observed recognition with zero dispatches for the explanatory task, followed by two concurrent implementation calls for the executable task. Invoice-dependent verification worked, with one advertised pricing interpretation missing from the planner's candidates.

The described-link trial completed in four scripted and four live sessions. Selected context passed 6/6 serial and 3/6 overlapping roots. All scripted historical-reference checks passed. [Stored descriptions and measurements](2026-11-26-dependency-context.md#stored-descriptions-for-dependency-links)

The xhigh trace trial finished at 19:36 UTC on 26 November. With the matching Pi Flash profile’s 32,768 completion-token allowance, all 24 live roots passed. All 48 returned traces passed checksum verification. One full-history selector exhausted the cap before the answer stage recovered. [Trial record](2026-11-26-dependency-context.md#pi-budget-xhigh-trace-trial)

Lain reused her original functions, selected the applicable tariff and cited the supporting records on all 18 scored answers. Dependency selection took 12 calls for six answers and used more generated tokens than full history. [Run and evidence](2026-11-26-reuse-context.md)

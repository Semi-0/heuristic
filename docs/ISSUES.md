# Remaining issues and limits

The four prompt-speed blocks cover two tasks and two sessions with stochastic generation. The 4B router chose one effort level for every order in the related six-case trial. Broader workloads and repeated samples per case are needed to estimate routing quality and production latency. [Matrix](2026-11-27-reranker.md) [Scope](2026-11-27-active-prompt.md)

Dependency recognition can select unnecessary evidence. The question-rooted prototype uses one small catalog, a host scheduler, structured fixture tools and one trial per condition. General retrieval precision and concurrent production latency remain open. [Experiment and results](2026-11-26-dependency-context.md)

Updated 2026-11-14. Entries distinguish observed failures from unproved scope. Do not treat old failures as current defects without their recorded conditions. [Status](STATUS.md) · [Plan](PLAN.md).

| ID | Issue / evidence | Next check |
| --- | --- | --- |
| H-01 | **Historical operational failure:** original corrected-2 fresh sent no HTTP; the separately authorized fresh-only rerun completed 3/3 correct with full wait/transport diagnostics installed | Preserve the failed attempt separately; both arms now complete, but neither demonstrates cumulative callable reuse |
| H-02 | **Unproved:** less-guided cumulative benefit; corrected-2 supplied prior source but produced zero reusable callables despite 3 correct outputs | Matched retained/fresh trials; actual callable identity, all calls/tokens/costs, correct and justified-unknown outputs |
| H-03 | **Known validation limit:** necessary price inequalities can accept other wrong formulas | Wrong-but-inequality-passing negative case and stronger executable contract |
| H-04 | **Unproved:** arbitrary contradiction repair, general planning/transport recovery, continuous semantic validation and post-delivery correction | Separate failure contracts and isolated controls, then real repair trials |
| H-05 | **Scope limit:** selected source restoration creates fresh runtime identities; not suspended TTMS/graph resumption or arbitrary-depth restoration | Specify identity/state/dependency/effect contract; deeper bundles and version mismatch tests |
| H-06 | **Missing general capability:** semantic/hybrid retrieval and reusable-subroot extraction/fork semantics | Bounded retrieval corpus, relevance/compatibility checks and explicit interfaces |
| H-07 | **Incomplete provenance:** admission/request identities and support checks do not establish every input-to-output causal relationship | Trace a bounded result through source, assumptions, effects and delivered output without conflating identity with causality |
| H-08 | **Recorded scaling limits:** behavior provenance and replay metadata grow; earlier 1,000-event workloads did not finish in budget | Isolated resource-admitted profiling; separate payload, retained evidence, graph size and CPU; do not infer heap leak from latency |
| H-09 | **Lifecycle gap:** full durable effect replay, dormancy, reactivation and comprehensive stale-ingress handling are not accepted end to end | Explicit run/event identities, cancellation/replay negative tests and external-action boundaries |
| H-10 | **Historical architecture criterion:** a successful model-built full conversation/history network is not established by supplied arithmetic/print-shop wiring | Revisit original three-round acceptance separately |
| H-11 | **Recorded runtime/inspection limitations:** conditional update behavior, ambiguous whole-record projection, unknown-slot display, name collisions and display replacement have condition-specific reports | Reproduce through public compiler/session APIs against the chosen runtime; do not patch kernel to make a harness trial pass |
| H-12 | **Verification gap:** focused passing checks and backend CI are not a full green harness suite | Maintain test scopes/runtime pins; classify legacy baseline failures separately |

Sources: [older evidence/corrections](research-status-2026-11-08.md), [behavior measurements](historical-root-readme-2026-11-14.md), [editor backlog](../TODO.md), [semantic repair limits](2026-11-13-semantic-validation-repair.md), [pilot](2026-11-14-cumulative-history-pilot.md).

## Issues advanced by later evidence

- Same-child missing-output repair is demonstrated; it is no longer wholly unimplemented. Broader contradictory/wrong-output recovery remains separate.
- Known-but-wrong numerical candidates can now trigger bounded Lain-owned repair before delivery under specific semantic contracts. This is not general error repair.
- Source-based durable executable reuse is demonstrated across processes. Exact historical execution-state restoration remains open.
- Callable reuse is demonstrated in guided trials. The pending new benchmark does not negate that evidence or establish broader cumulative benefit.

## Completion notification follow-up

The missing completion-to-agent mechanism now has a deployed Lain scheduler and a successful enqueue/resume smoke. The smoke event has now arrived in a subsequent turn and caused receipt review and documentation updates. Already-idle/disconnected destinations and daemon restart recovery remain untested. A crash during notification can leave a durable ambiguous `sending` claim; reconcile it manually rather than auto-resending. This does not remove model availability, thermal interruption or long-horizon reliability limits.

The new shared-clock queue has controlled evidence for append, completion, deadline, acknowledgment and replay suppression. Its remaining gaps are durable adapters/receipts, task removal, stale callbacks and long-run growth. It remains undeployed; the older monitor still performs production notification. See [run record](../tools/scheduler/live_queue/README.md).

## Real queue IO and lifecycle follow-up — November 15

[The full IO run](../tools/scheduler/live_queue/FULL-RUN.md) passed using real receipt files and one Codex CLI notification. Extension replay, duplicate queue entry and a fresh owner with saved acknowledgment caused no duplicate send; closed-owner callbacks were rejected. The queued self-test event subsequently arrived in this conversation and both receipts were reviewed. The shared-clock version remains undeployed. That map-list version retains completed watchers; the disposable-session map-task implementation below supersedes the earlier proposal, but does not remove parent queue nodes.

## Disposable task scopes — November 15

The [new map-task adapter](../tools/scheduler/map_task/README.md) retires independently owned child sessions; four final child networks became unreachable in a controlled test. It does not reclaim parent queue/effect nodes, completed-task receipts or diagnostic weak-reference arrays. Long-lived tasks can still accumulate observations. Fresh per-task compilation adds admission cost, not yet profiled at scale. Only registered worker modules are supported, not arbitrary closures. Durable IO integration, cross-process recovery, concurrent cancellation stress, and injected cleanup/admission failure tests remain unverified. No deployment or full-suite claim.

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

Staging increases inference budget from three to nine calls per arm, confounding structure with extra compute. Prior one-call trials did not produce reusable callables. Planning parser is the existing EDN contract, not a general source repair mechanism. Scale remains conditional; no reuse claim yet. Evidence and executable Lain: [/home/lain/reports/heuristic-cumulative-20261114/staged-20261115](/home/lain/reports/heuristic-cumulative-20261114/staged-20261115/README.md).


## Staged pilot terminal review — November 15

Real-model stage drift: retained q3 returned explanatory prose; fresh q2 generalization returned bare ANSWER text despite phase/contract being present. No transport/truncation/resource failure. Generalization proposed a callable but implementation omitted it. Long benchmark final-answer instructions competing with phase format are a hypothesis. Generated acceptance nil/zero fields on failure represent missing final snapshots; inspect raw HTTP/admissions for partial evidence. See [reviewed evidence](/home/lain/reports/heuristic-cumulative-20261114/staged-20261115/RESULTS.md). Prior running checkpoints are superseded.


## Print-shop scale campaign — November 16

Scaling at a five-question horizon does not establish long-horizon memory. Fresh input rounds are remapped to port 1 in independent copies of the original five-port parent. Session initialization differs by arm; separate it from inference latency. Model/protocol failures remain outcomes; resource/infrastructure failures stop the campaign. Post-run mutation checks are not repeated in this scale run. [Frozen plan and evidence](/home/lain/reports/heuristic-printshop-scale-20261116/PLAN.md).


### Scale launch checkpoint — November 16

Control reuse is synthetic evidence only. Real scale reliability/reuse remains unmeasured until campaign results arrive. The fixed 48-hour limit can leave a partial campaign; retain that distinction instead of calling a deadline a model failure. [Evidence](/home/lain/reports/heuristic-printshop-scale-20261116/CHECKPOINT.md). Earlier prepared/control-running checkpoints are superseded.


## Print-shop scale terminal review — November 16

Terminal evaluator failure: invalid EDN token compiler-2.implicit-return/7d643dbc-4bf2-3341-8f5f-9a71193637f9 in final fresh evidence. Three HTTP400 responses exceeded 131072-token context limit (two in that session); 24 responses finished length. Retained reused successfully in many rounds but failed more matched question slots overall. No causal speed/accuracy benefit established; campaign remains partial. [Reviewed report](/home/lain/reports/heuristic-printshop-scale-20261116/RESULTS.md).


## Pi model extension — November 16

First Pi adapter supports text or the explicit system/user snapshot pair, not arbitrary multi-role arrays. It deliberately disables Pi automatic compaction/retries and uses bounded inspection only; it cannot by itself fix the earlier EDN serialization bug or guarantee lower costs. Real comparison pending. See [implementation and evidence](2026-11-16-pi-model-extension.md).


## Pi comparison terminal review — November 17

Pi generalization emits :decision ":construct" (string), rejected by the existing keyword-only enum. :status string is allowed and is not the cause. No transport, truncation, watchdog or evaluator-serialization failure in this pair. Pi answered no questions; lower token totals are not an efficiency win. One pair does not establish general backend reliability. [Full evidence](/home/lain/reports/heuristic-pi-20261116/RESULTS.md). Previous running checkpoints are superseded.

## Queryable context blocked verification — November 17

The sandbox denies access to the systemd user bus, so the mandatory watchdog
launcher failed before JVM startup. No tests or provider trials ran. Do not bypass
the guard or count the static review as acceptance. The new storage/inspection
foundation remains unverified and lacks the Lain context cycle and tokenizer
integration. See [checkpoint](2026-11-17-queryable-context.md) for exact limits
and outstanding acceptance gates.

The sandbox/watchdog access issue above is resolved. Storage/inspection controlled
checks pass 30 assertions. Full-policy integration currently stalls before model
requests; thread dumps show named-network comparison during propagation. The
obsolete controlled check was cancelled with exit 75 and preserved. Revised
reference-based wiring is under test; no real Qwen trial has started.

The full-policy integration stall above is resolved in controlled checks.
One-time stage snapshots and one shared temporal model ingress allow execution,
indexing and correction to complete. Real-Qwen acceptance remains unmeasured
until tokenizer calibration and the matched pilot finish.

Real indexed pilot failed: Qwen speculated about the reusable interface instead
of returning a retrieval request, then invoked a four-argument callable with
three arguments. Late dynamic application failure terminated the host after the
single correction. The kernel is unchanged; future work must address retrieval
contract clarity and late-failure containment through supported session surfaces.
Acceptance is not demonstrated by the passing controlled mechanism checks.

## Queryable context final findings — November 17

The [pilot](2026-11-17-queryable-context.md) is terminal and fails indexed acceptance.
Controlled storage/orchestration success does not establish real-model recovery.

1. Qwen never emitted a retrieval decision in the real runs. It guessed an
   interface in indexed q1 and invoked the four-argument callable with three
   arguments. Late application failure terminated the host; no kernel workaround
   or silent source restoration was introduced.
2. Real indexing frequently violated metadata names, lookup-reference or query
   projection contracts. Bounded correction did not always recover it.
3. The full-history control includes serialized indexing/failure/diagnostic
   transcripts and overflowed from q2. It is not an optimized history baseline;
   no indexed performance benefit follows from its budget failures. Common role
   wording also inaccurately says details are omitted for that control.
4. The failed indexed run's in-memory operation journal was not exported. Raw
   responses, HTTP/admission records and Datomic archives survived; offline
   reconciliation preserves the fatal observation and marks the journal incomplete.
   Subsequent driver code flushes every catalog journal append and archives fatal
   observations. It does not recover a live network or retroactively fill missing
   operation records.

Source/runtime/capability hashes match across conditions. All sent prompts met
the input budget. Final regression, archive reconciliation and reuse analysis
passed; the unresolved research issues above remain open. LifelongAgentBench's
previous adapter timeout remains a separate issue, with its data/failed controls
preserved and no expansion performed.

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

In the [planner delegation pilot](2026-11-21-planner-delegation.md), a planner that accurately describes both candidate rules can satisfy an explanation request without dispatching verification jobs. The available slot count bounds this experiment. TTMS branch tags record the branch assumed by a verification path. Source admission and call records supply the model-side evidence.

Described dependency links make relations inspectable, but rendering the entire link index on every recognition call repeats descriptions. Per-question incremental retrieval and full prompt token budgeting remain untested. [Protocol trial](2026-11-26-dependency-context.md#stored-descriptions-for-dependency-links)

The reuse fixture supplies complete provenance edges and visible index labels. Its six matched decisions per arm measure one shop workflow. Long-history reliability and unsupervised edge recognition remain open. [Reuse experiment](2026-11-26-reuse-context.md)

The crossover uses two composite questions per history length, fixed templated background records and a small seeded file catalog. Archive citation errors and numerical errors are reported separately. [Scope](2026-11-27-context-crossover.md)

Project timing is a single trajectory per arm. A fixture supplies asynchronous session candidates and the hidden UI tests run with a simulated DOM. [Scope](2026-11-27-autocomplete-project.md)

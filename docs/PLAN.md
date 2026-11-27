# Plan and acceptance gates

The prompt-speed comparison measured all four conditions. A broader task mix is needed before selecting a production routing policy. [Experiment](2026-11-27-active-prompt.md)

The isolated question-rooted comparison checks executable pricing, bracket repair, three linked arithmetic turns, receipt routing and a historical quote after a TTMS revision. The report retains both the original attempts and the receipt-assisted comparison. [Experiment and results](2026-11-26-dependency-context.md)

Updated 2026-11-14. The [original proposal](lain-agent-harness-proposal.md) defines the research scope; this page translates its phases into current evidence and remaining gates. [Status](STATUS.md) records outcomes, [issues](ISSUES.md) records limitations. The ordering below is a working plan, not authorization to exceed module boundaries.

## Original five phases

| Phase | Demonstrated subset | Remaining acceptance |
| --- | --- | --- |
| 1. Persistent invocation representation | Live lexical history; selected executable definitions rebuilt in another process | Define durable identity/state/effect contract; distinguish reconstruction from exact suspended-state resumption |
| 2. Network discovery | Real Datalog metadata/dependency discovery | Semantic/hybrid retrieval, retrieval quality and larger catalogs |
| 3. Reusable subroots | Actual callable reference, instantiation and composition | General extraction, compatibility checks, stable version and fork semantics |
| 4. Dependency-aware reuse | Changed-policy selection, exact supports, withdrawal/restoration and isolated updates | Broader automatic assumption checks and stale/incompatible result rejection |
| 5. Reactive cumulative execution | Small reactive compositions update without extra inference | Long sequences, bounded resource growth and demonstrated cumulative benefit |

## Immediate sequence

1. **Complete the corrected retained-versus-fresh pilot.** Preserve earlier artifacts; use identical generation settings, Lain-built user messages and one attempt per arm when resource admission/provider availability allow. Require completed provider responses, raw HTTP status/body, admitted source, delivered answers and actual callable application evidence. Separate transport/setup failure from generated-code failure. The requested corrected-2 run is terminal: retained 3/3 correct with no reusable callable; fresh has no provider result. Review is complete. The separately authorized fresh-only rerun subsequently completed 3/3 correct with no reusable callable; see its terminal review below. Do not silently start another trial.
2. **Assess reuse independently of correctness.** A correct answer does not prove reuse. Check retained callable identity and applications, lack of redeclaration, unknown handling and earlier item-value isolation. Account for every provider call and all compilation/restoration work. This pilot is not an information-parity D-007 head-to-head comparison.
3. **Strengthen semantic validation.** Design a wrong computation that passes the current inequalities. Test a stronger executable contract, rejection before delivery, bounded real correction and repair exhaustion without supplying a solved formula.
4. **Repeat across fresh trials and broader tasks.** Freeze contexts/settings and report all attempts. Establish failure rates and matched retained/fresh cost comparisons before claiming improvement from cumulative memory.
5. **Expand persistence and scale separately.** Test larger/deeper source bundles, version mismatch, missing dependencies, replay and input revisions. Measure graph/observation growth, admission time, memory and retrieval costs. Define explicit acceptance before broadening to durable effects or suspended state.

## Full-harness acceptance still needed

- Repeatable end-to-end sessions with distinguishable transport, compilation, readiness, semantic and resource failures.
- Reliable discovery and applicable reuse across independent invocations, with exact source/provenance boundaries.
- Lifecycle rules for dormancy, reactivation, cancellation, stale external results and effect replay.
- Resource-bounded operation on longer workloads; passing focused controls plus representative real trials.
- Original environment-only conversation construction criterion: the model itself builds history/messaging connections, three acknowledged rounds, final recall, recorded earlier messages/replies in later requests, stable worker/child and replay safety. Infrastructure supplied by later experiments does not retroactively satisfy this criterion.

No duration or percentage-to-completion is assigned: these are research/engineering gates with unequal effort and uncertain outcomes.

Completion monitoring is now deployed for the current pilot; use its event to review terminal evidence and update these pages. The smoke event has now triggered a follow-up review after the implementation turn ended. Separately test an already idle/disconnected destination or daemon restart if those lifecycle guarantees are needed. Do not infer experiment success from a notification or automatically restart resource-stopped work.

The shared-clock/live-queue refinement has passed its first controlled compiled run. Review [its source and limits](../tools/scheduler/live_queue/README.md) before replacing the older monitor. Next gates are real adapter integration, task removal and late-callback behavior, durable receipt/queue recovery and bounded retention; do not treat the previous stop-per-job draft as accepted.

## Real queue IO and lifecycle follow-up — November 15

[The full IO run](../tools/scheduler/live_queue/FULL-RUN.md) passed using real receipt files and one Codex CLI notification. Extension replay, duplicate queue entry and a fresh owner with saved acknowledgment caused no duplicate send; closed-owner callbacks were rejected. The queued self-test event subsequently arrived in this conversation and both receipts were reviewed. The shared-clock version remains undeployed. That map-list version retains completed watchers; the disposable-session map-task implementation below supersedes the earlier proposal, but does not remove parent queue nodes.

## Disposable task scopes — November 15

The [map-task lifecycle test](../tools/scheduler/map_task/README.md) now passes, including collection probes for retired child owners/networks. Before deployment: connect the durable real IO boundary, test failure/ambiguous-send behavior and recovery, define parent queue compaction and completed-identity retention, and measure long-lived tasks and many sequential task admissions. General captured-closure transport and arbitrary shared-subgraph deletion are outside this implementation. The currently deployed older monitor is unchanged.

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

User authorized a staged pilot and, if successful, 150 questions. Gate scaling on correct answers, complete/admitted implementations, and actual retained callable reuse across the numeric questions. Frozen scale-selection.json chooses the first 150 of the existing 160 rule-family entries; preserve IDs and report pilot overlap separately. Evidence and executable Lain: [/home/lain/reports/heuristic-cumulative-20261114/staged-20261115](/home/lain/reports/heuristic-cumulative-20261114/staged-20261115/README.md).

Checkpoint 2026-11-15T08:50:17.768984+00:00: controlled retained/fresh checks passed (nine stages each; shared versus separate callable identities verified). Real-Qwen two-arm pilot launched as `lain-test-5b43220bebde45628f6a4dad4867c960.service`; acceptance and scale gate pending.


## Staged pilot terminal review — November 15

The staged cumulative pilot did not pass the gate. Keep the frozen 150-item selection pending; do not scale based on successful HTTP calls or planning claims. Potential next isolated experiments: disambiguate final answer versus current phase output, and check implementation against its own chosen interface. These have not been run. See [reviewed evidence](/home/lain/reports/heuristic-cumulative-20261114/staged-20261115/RESULTS.md). Prior running checkpoints are superseded.


## Print-shop scale campaign — November 16

User authorized scaling the successful print-shop condition. Freeze 30 matched five-question episodes (150 questions per arm), alternate arm order, preserve all failed episode tails in denominator. After controlled wiring compatibility, run the campaign without a new real-model success gate. A 48-hour wall bound, disk floor and watchdog may leave a documented partial campaign; no automatic restart. [Frozen plan and evidence](/home/lain/reports/heuristic-printshop-scale-20261116/PLAN.md).


### Scale launch checkpoint — November 16

Controlled compatibility gate passed; authorized scale campaign now launched without another small real-model gate. Review all outcomes and incomplete denominators at terminal/deadline; do not restart interrupted work automatically. [Evidence](/home/lain/reports/heuristic-printshop-scale-20261116/CHECKPOINT.md). Earlier prepared/control-running checkpoints are superseded.


## Print-shop scale terminal review — November 16

Scale campaign terminal and incomplete: retain all evaluated failures, 18 unreached retained episode tails, unstarted questions and one executed/unscored fresh session. Future work would need readable diagnostic serialization and bounded repair context before any separately authorized continuation. Do not treat 150 per arm as completed. [Reviewed report](/home/lain/reports/heuristic-printshop-scale-20261116/RESULTS.md).


## Pi model extension — November 16

Evaluate one five-question retained episode per backend on print-shop. Count actual callable identities/applications, answer correctness, repairs, tool usage and extra provider budget. Do not infer reliability from this one pair or expand automatically. See [implementation and evidence](2026-11-16-pi-model-extension.md).


## Pi comparison terminal review — November 17

Small Pi/direct comparison terminal, not pending. Do not scale based on integration success alone. A potential next comparison is bounded planning-validation feedback or shared typed plan submission under Lain; neither was implemented or run during review. [Full evidence](/home/lain/reports/heuristic-pi-20261116/RESULTS.md). Previous running checkpoints are superseded.

## Queryable context acceptance gate — November 17

Follow the [checkpoint and remaining work](2026-11-17-queryable-context.md). First
run `bash tools/run_context_checks.sh` where the watchdog launcher is accessible.
Then implement and verify the Lain policy, 12,000-token input limit with matching
chat-template counting, four retrieval rounds and one correction per stage. Run
the five-task matched print-shop comparison only after controlled gates pass.
Keep LifelongAgentBench expansion paused; preserve earlier failed controls.

Queryable-context host access was restored on continuation. Storage/inspection
checks passed 4 tests / 30 assertions; controlled full-policy checks are in progress.
Keep real comparison gated on complete policy and tokenizer verification.

Queryable-context controlled gates and matching-tokenizer calibration now pass.
Run shared Qwen preparation once, replay its complete responses into independent
indexed/full-history sessions, then execute the two isolated ablations. Preserve
all failures and count preparation/index/repair costs. Do not expand LifelongAgentBench
or claim a performance benefit from this single pilot.

## Queryable context review gate — November 17

The requested candidate and bounded pilot are implemented and reviewed in the
[final report](2026-11-17-queryable-context.md). Storage/session controls, tokenizer
verification, shared source replay, both primary conditions, two ablations and
cost/reuse reconciliation are complete. Indexed acceptance is **not** satisfied;
three later indexed tasks were unreached after the first continuation's host error.

Before another separately reviewed pilot: clarify that retrieval results become
the next working evidence, improve verified individual-operation query/metadata
examples, investigate late-failure containment through public session surfaces,
and review the full-history transcript representation. Preserve the original
failed pilot and its costs. Do not silently rerun it or insert a solved retrieval
network. Keep LifelongAgentBench expansion and its compatibility revisit paused
until the context mechanism passes review and acceptance. Performance benefit
would still require repeated matched sequences after a successful feasibility run.

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

For [planner delegation](2026-11-21-planner-delegation.md), score ambiguity recognition, assignment publication, concurrent execution, and evidence-dependent selection individually.

The described-link prototype checks whether stored premise references survive a later revision and whether the selected evidence packet contains the linked originals. A subsequent budgeted retrieval trial can measure which descriptions need to enter each question's context. [Experiment](2026-11-26-dependency-context.md#stored-descriptions-for-dependency-links)

Assess generalization on longer histories with naturally recorded dependency edges and cases where neither stored tariff applies. [Reuse experiment](2026-11-26-reuse-context.md)

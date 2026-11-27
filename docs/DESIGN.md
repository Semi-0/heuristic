# Current design

The isolated prompt-speed experiment can execute a model-selected archive read or numeric probe and a model-authored continuation in one dispatch. Both protocols resolve the same archive and execute expressions in the same restricted lexical frame. [Implementation](../experiments/dependency-context/active.clj)

An isolated question-rooted context prototype uses a shared Lain model worker, Datomic event and dependency records, lexical bindings and TTMS observations. A session-side driver resolves model-selected evidence and delivers revision-checked fixture receipts. [Experiment and results](2026-11-26-dependency-context.md)

Reviewed 2026-11-14 against heuristic `00c1ead`. This is an implementation map; the [original proposal](lain-agent-harness-proposal.md) remains the research specification. See [status](STATUS.md) for acceptance limits.

## One Lain orchestration path

1. A Lain seed installs task, role, language documentation, capabilities, memory and output ports as lexical bindings.
2. Lain captures an environment description and constructs the request, including explicit role-tagged messages where used.
3. The existing model worker sends the request and exposes source separately from provider thinking.
4. `soul-refined.lain/runtime` creates a lexical child and connects completed model output to `incremental-compile`.
5. Admission validates completion, admits forms sequentially and records identities/receipts. A failed form retains its accepted prefix.
6. Experiment-specific Lain policies observe outputs, select subsequent phases and request bounded repairs. Host probes supply external inputs and independently evaluate outcomes.

| Responsibility | Source |
| --- | --- |
| Small runtime composition | [soul-refined.lain](../soul-refined.lain) |
| Existing model and IO | [model-refined.lain](../model-refined.lain), [model_io.clj](../src/lain_harness/model_io.clj) |
| Completed-response compilation/admission | [compiler.lain](../lib/compiler.lain), [soul_session.clj](../src/lain_harness/soul_session.clj) |
| Environment seeds | [environments](../environments/) |
| Request construction example | [catalog-common.lain](../experiments/catalog-common.lain), `stage-in` |
| Language guide and examples | [language-context.lain](../lib/language-context.lain), [guide](how-to-think-in-lain.md), [examples](how-to-think-in-lain-examples.md) |
| Immediate value/edge observation | [experiment_observation.clj](../src/lain_harness/experiment_observation.clj) |
| Catalog and source restoration | [catalog.clj](../src/lain_harness/catalog.clj), [catalog_selection.clj](../src/lain_harness/catalog_selection.clj), [source_restore.clj](../src/lain_harness/source_restore.clj) |
| Missing-output repair | [output-repair.lain](../lib/output-repair.lain) |
| Candidate semantic repair | [semantic-repair.lain](../lib/semantic-repair.lain) |
| Public extension producers | [session_publication.clj](../src/lain_harness/session_publication.clj) |

## Environment and prompt

There is no single universal prompt file. The selected seed determines the task and capabilities. The conversation seed provides the four default context/language/observation/memory cells and source history; catalog seeds add discovery, restoration and phase-specific contracts. Documentation examples are text, not preinstalled solved task networks. Details: [conversation environment](environment-conversation.md).

The current cumulative pilot deliberately uses a smaller environment: `role-cell`, `current-task`, `language-introduction-cell`, `memory-cell`, `user-output`, arithmetic/string/reference/inspection capabilities. Its isolated `retained.lain` and `fresh.lain` seeds are under the local report directory, not replacements for repository environment seeds. The corrected request has an environment-derived system message and a user message from the current-task cell, following `stage-in`. The task is currently repeated in both. See [pilot record](2026-11-14-cumulative-history-pilot.md) for exact locations and scope.

## Memory and validity

Distinguish generated source, provider thinking, delivered conversation replies, current cell values, supported observations and catalog metadata. They are not interchangeable histories. Callable reuse is checked through actual applications and declaration/environment identity; a model claiming reuse is insufficient.

Durability currently stores selected source and dependency metadata, then reconstructs fresh runtime objects. The demonstrated subset does not cover arbitrary-depth restoration, persistent suspended TTMS state, full causal provenance, or automatic safe replay of external effects.

## Repair boundaries

Missing-output repair can add connections to existing parent ports in the same child. Semantic repair uses descendant candidate environments with fresh output ports; rejected candidates stay inspectable and only an accepted candidate reaches public outputs. Current inequalities are necessary conditions, not a universal correctness proof. These experiments repair before public delivery; correction of already-delivered wrong answers is separate work.

## Scope boundaries

Keep `soul-refined.lain` small, environment seeding separate, one existing model, and scheduling/repair policy in Lain. Use public session extension/admission paths rather than pre-seeded domain cells or hand-built low-level networks. Numeric external publication has its documented public API. Kernel changes, production restarts and broad integration work require separate scope decisions. Proposal-only Xiaobao integration is not an implemented second controller.

## Experiment completion scheduling

A small [Lain scheduler](../tools/scheduler/README.md) now observes job state and selects wait/launch/wake actions through a public session extension. Its bounded host adapter performs watchdog launch and `codex queue --resume` effects. The deployed instance monitors the existing pilot; it does not replace Lain model-stage orchestration or launch another benchmark. Notification claims/receipts are durable and interrupted jobs are not automatically restarted.

### Executed shared-clock refinement

The [live-queue runtime](../tools/scheduler/live_queue/runtime.lain) now expresses queue traversal, per-task observation, `cond` alert selection, notification and acknowledgment in Lain. One clock is shared; task completion does not stop it. A public session extension supplies managed IO capabilities. A controlled real-clock run passed; durable production adapters and lifecycle work remain separate from this executable draft.

## Real queue IO and lifecycle follow-up — November 15

[The full IO run](../tools/scheduler/live_queue/FULL-RUN.md) passed using real receipt files and one Codex CLI notification. Extension replay, duplicate queue entry and a fresh owner with saved acknowledgment caused no duplicate send; closed-owner callbacks were rejected. The queued self-test event subsequently arrived in this conversation and both receipts were reviewed. The shared-clock version remains undeployed. That map-list version retains completed watchers; the disposable-session map-task implementation below supersedes the earlier proposal, but does not remove parent queue nodes.

## Disposable task scopes — November 15

[map-task runtime](../tools/scheduler/map_task/runtime.lain) maps an open Lain queue to `task:run` effects. Each effect admits [worker source](../tools/scheduler/map_task/worker.lain) through session extension into a separate owned session; Lain chooses completion/deadline, notification, acknowledgment and retirement. The [adapter](../tools/scheduler/map_task/adapter.clj) owns session creation/closure and a shared tick broadcaster. Retirement closes the child on a separate executor, removes its subscription, releases the owner, and then delivers its receipt. This uses registered source modules, not transported captured lexical closures. Duplicate active entries return `already-active`; completed identities return cached receipts. Parent graph nodes are retained. See the [contract and evidence](../tools/scheduler/map_task/README.md).

## Corrected pilot transport boundary — November 15

The corrected-2 retained arm confirms Lain-built system/user messages reach the provider (three HTTP 200 responses); later requests include earlier generated source. It does not confirm callable reuse: generated arithmetic uses separate definitions. The fresh arm failed before HTTP/source admission, likely in its idle gate. That wrapper waits outside its exception recorder, leaving a diagnostic gap; the runner can exit 0 after recording an arm's exit 1. These are experiment-adapter limitations, not established heuristic/kernel faults. See the [dated pilot review](2026-11-14-cumulative-history-pilot.md).

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

The isolated staged pilot binds phase, stage-guide, analysis-record and generalization-record into each environment snapshot. Lain gates successive requests using the existing print-shop plan validator. Only qN implementation replies enter the unchanged runtime. The host records calls and checks outcomes; it does not choose phases or answers. Evidence and executable Lain: [/home/lain/reports/heuristic-cumulative-20261114/staged-20261115](/home/lain/reports/heuristic-cumulative-20261114/staged-20261115/README.md).


## Staged pilot terminal review — November 15

Observed Lain phase gating rejected non-plan responses before admission. The implementation phase was supplied the generalization record, but the real model emitted direct arithmetic instead of its proposed callable. Format validation does not enforce plan-to-code consistency. Failure-path observer currently omits partial answer/topology snapshots. See [reviewed evidence](/home/lain/reports/heuristic-cumulative-20261114/staged-20261115/RESULTS.md). Prior running checkpoints are superseded.


## Print-shop scale campaign — November 16

Scale experiment preserves the successful historical Lain stage policy, main runtime and model unchanged, on a clean 2773dff runtime export. Retained uses five-question episodes; fresh uses one independent session per matched question. The host only publishes questions and records observations; Lain chooses phases and bounded repairs. This measures five-question reuse repeatability, not one continuously growing 150-question context. [Frozen plan and evidence](/home/lain/reports/heuristic-printshop-scale-20261116/PLAN.md).


### Scale launch checkpoint — November 16

Launch review verified frozen driver/case hashes and clean control closure. Lain stage/repair orchestration remains the historical version. Real retained sessions span five questions; fresh sessions one matched question. Host scheduling changes no model answer or phase decision. [Evidence](/home/lain/reports/heuristic-printshop-scale-20261116/CHECKPOINT.md). Earlier prepared/control-running checkpoints are superseded.


## Print-shop scale terminal review — November 16

Real scale evidence verifies recurring reuse at five-question horizon using declaration/captured-environment identity, stable reference and new applications. It does not establish 150-question accumulated memory or an accuracy benefit. Printed internal runtime symbols can make diagnostic evidence unreadable by the offline EDN evaluator; this stopped the campaign without modifying kernel. [Reviewed report](/home/lain/reports/heuristic-printshop-scale-20261116/RESULTS.md).


## Pi model extension — November 16

Pi backend lives in pi_session.clj + SDK subprocess bridge; same model-refined.lain and main Lain runtime. Lain retains phase/admission/repair ownership, Pi has only a scoped bounded inspection tool loop. No shell, hidden persistent history, compaction or second source compiler. See [implementation and evidence](2026-11-16-pi-model-extension.md).


## Pi comparison terminal review — November 17

Same model-refined.lain/main orchestration identity verified in terminal artifacts. Pi inspection loop ran successfully (two scoped sessions, five provider calls, six tools). Existing Lain plan validator rejected the raw provider decision string and stopped before source admission; implementation-only repair policy did not cover this planning error. [Full evidence](/home/lain/reports/heuristic-pi-20261116/RESULTS.md). Previous running checkpoints are superseded.

## Queryable context draft — November 17

[Implementation checkpoint](2026-11-17-queryable-context.md): new session extensions
add immutable evidence, evidence-backed claims, paginated catalog queries and
bounded topology observation to the existing Datomic catalog. Storage effects
dispatch after commit. This is an unverified foundation, not an integrated context
cycle; model/runtime composition remains unchanged. Lain policy, tokenizer budget
enforcement, environment examples and matched Qwen trials remain outstanding.

The draft above is superseded by the implemented composition in
[context-policy.lain](../lib/context-policy.lain), composed with unchanged
`soul-refined.lain` / `model-refined.lain`. Request preparation archives the
packet, then measures it through the serving tokenizer adapter; execution is a
separate network selected in Lain only within the input budget. One-time receipt
and completed-stage snapshots prevent historical supports from delaying later
field projections. Task-boundary evidence selection, bounded retrieval, stage
correction and indexing remain Lain relationships. Native adapters own only
storage, inspection, measurement, independent validation and transport.
[Environment](../environments/context.lain) documents capabilities and individual
operations; [trial driver](../tools/probe_context.clj) provides inputs and measures
outcomes, not retrieval selection. Full-cycle acceptance is still pending.

The shared model has a single temporal ingress in
[experiments/context.lain](../experiments/context.lain). Per-attempt runtime
request ports publish into it; completed-response compilation still belongs to
the main runtime. This avoids conflicting producers when later attempts send
requests. The focused complete task/index check passes.

The current full-history control serializes task/source, selected evidence and
diagnostic transcripts (including indexing/corrections) into `working-evidence`
under `:history`; indexed tasks receive an empty history and the broad paginated
overview. Both use the same fresh capability environment and live catalog access.
This is a strict uncompressed-history control, not an optimized chat-history
representation. Source remains outside prompts unless selected or supplied by
that control. The real pilot exposed both retrieval-contract failures and
full-history overflow; see the dated report before interpreting performance.

## Queryable context final boundary — November 17

The candidate is implemented and its controlled contracts pass, but the real pilot
fails the acceptance gate ([report](2026-11-17-queryable-context.md)). The final
[driver](../tools/probe_context.clj) persists catalog operations as they occur and
archives terminal host failures; [offline reconciliation](../tools/recover_context.clj)
reads retained evidence without restoring live references. [Reuse analysis](../tools/report_context.clj)
compares application identities, declaration and captured environment inside each
condition's session and checks a graph connection to that task's delivered output.
It excludes earlier applications that only influence later orchestration gates.

Core Lain/model/runtime/capability sources match across all experimental conditions.
Historical costs, failed attempts and superseded observations remain available.
The architecture makes no promise of bounded live-graph memory, suspended-state
resumption, or successful automatic recovery from arbitrary dynamic application
errors. No kernel or deployed service change was made.

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

The [planner delegation pilot](2026-11-21-planner-delegation.md) uses two optional Lain assignment ports, separate model workers and lexical children, callable outputs and premise-supported invoice checks. The planner dispatches by publishing a job. Lain gates execution on source admission.

In the question-rooted prototype, a Datomic dependency record carries a typed relation, target event reference and stored English description. Both the recognition index and the composed evidence packet expose these records. [Implementation](../experiments/dependency-context/probe.clj)

The isolated crossover driver maintains a lexical FTS5 index beside the Datomic archive, reranks up to eight candidates, expands stored references and executes responses against declarations loaded from source files. [Source](../experiments/context-crossover/run.py)

The isolated project benchmark grants Flash structured search/read/edit actions in pinned snapshots, with offline Node execution and a journal of tool reads, writes and feedback. [Implementation](../experiments/autocomplete-project/run.py)

The chronological Soul editor can bind request-policy changes to the next packet ID via the [editor hot-reload integration](../experiments/editor-hot-reload/README.md).

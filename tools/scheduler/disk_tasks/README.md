# Lain scheduler with a single-writer disk queue

Superseded by [explicit Lain ingress and observer networks](../reactive_scheduler/README.md). This version is preserved for rollback and historical evidence.

`runtime.lain` installs one managed `map-tasks` effect. The queue is an opaque store
reference, not an append-only list in the parent network:

```lain
(define runtime
  (network [clock queue]
    (map-tasks clock :watch-task queue)))
```

The service admits this source and then its start relationships through the public
session-extension API:

```lain
(define clock (clock:shared 15))
(define scheduler (runtime clock (queue:open)))
```

`worker.lain` owns observation, completion/deadline selection, notification,
acknowledgment and renewal. It uses `cond`. `adapter.clj` supplies managed effects,
clock callbacks and disposable child sessions. There are no kernel changes and no
experiment-launch capability. Only registered worker source is supported; arbitrary
captured lexical closures are not serialized.

## Queue ownership and storage

One scheduled effect executor owns queue admission, completion and renewal writes.
Independent threads/processes submit immutable JSON requests to `inbox/`; they do
not modify queue state. The effect writes replies to `replies/`. `runner.lock`
prevents two runners from owning the same store. TTMS supports values inside the
workers; it is not used as a filesystem lock or transaction coordinator.

`queue.py ROOT TASK.json` submits a task and waits for its admission reply. Fields:
`id`, `unit` (watchdog run identity), `deadline` (absolute Unix seconds), `thread`
(Codex destination), `evidence` (report path), and optional numeric `priority` and
custom `message`. Replay uses `[id, unit]`; changing a payload under that identity
is rejected. A timed-out submission remains in the inbox: inspect its command ID,
do not invent a new task identity to retry it.

Queued tasks are admitted by priority descending, then admission timestamp, then
stable filename for ties. Up to four workers are active. High-priority arrivals
can delay lower-priority work; this is explicit priority scheduling, not a fairness
guarantee. Selection scans the disk backlog with bounded working memory.

- `queued/`: task and admission timestamp.
- `active/`: durable claim before admission; surviving claims require recovery.
- `archive/`: full completed task and acknowledged result.
- `completed/`: compact task fingerprint and archive reference for replay suppression.
- `blocked/`: ambiguous delivery or failed task requiring reconciliation.

Disk persistence is an effect: write/sync a temporary file, atomically rename it,
then sync its directory. Completion is persisted before child retirement. A failed
write retains the scope and active record. Diagnostics retain at most 16 samples;
there is no RAM completion index and no per-task result stream into the parent.
The task/claim/result records are intentionally retained on disk; no automatic
expiry/deletion policy is enabled.

## Real notification and recovery

`io.py` is a one-operation effect interpreter. It reads watchdog receipts and
calls `codex queue --thread ... --message ... --resume`. It writes `sending` before
invocation; only successful queue output without warnings becomes `submitted`.
Acknowledgment means durable CLI submission, **not** proof the agent read it.
A fresh runner checks active records: already archived completion is finalized;
no-send/submitted/acknowledged states can resume the watcher safely; ambiguous
`sending`/`needs-review` states move to `blocked` without resending.

After independently verifying the message was submitted, an operator can reconcile:

```sh
python3 tools/scheduler/disk_tasks/queue.py ROOT TASK.json \
  --operation reconcile --confirmed-message-id VERIFIED_MESSAGE_UUID
```

This records the operator's evidence and lets the Lain worker acknowledge the
existing submission. It does not send another message. The UUID is an explicit
operator assertion, not an automatic lookup of Codex history. If the outcome is
still unknown, leave the task blocked. There is no exactly-once external-delivery
claim and no blind retry command.

Workers renew after 900 seconds by default. Lain selects renewal only after checking
acknowledgment, completion and deadline. The effect closes the old owner, requeues
the watcher and constructs a fresh one. Durable notification claims survive renewal.
The shared clock/parent remain alive. No experiment process is restarted.

## Evidence

Clean Lain runtime pin `3c3f643`. Local evidence root:
`/home/lain/reports/heuristic-scheduler-20261115/`.

- `disk-tasks-2`: initial disk effect, ten completions; parent unchanged at 595 nodes
  and two effect receipts; all sampled child owners/networks collected.
- `deployment-tests-2`: single-writer lifecycle test, two renewals then one completion
  alert, late callback rejection, failed-write retention and completed replay.
- `process-tests-1`: ten independent producers, six unique tasks in priority order;
  fresh process suppresses completed replay; SIGKILL during synthetic send produces
  quarantine with no resend.
- `final-verification`: formatter/linter and expanded process/reconciliation checks.
- `real-delivery`: labelled real Codex CLI integration smoke (see its result file).

All synthetic checks use controlled notification output, not model calls. Process
crash tests kill only their explicitly owned child process groups. No experiments
are launched. Historical failed reader/lint attempts remain in their report folders.

Measured small-record storage: six completed tasks used 3,024 logical bytes total
(504 bytes/task) for archive plus replay records; filesystem allocation was 49,152
bytes total (8 KiB/task). Delivery logs are additional. Large custom messages and
receipts change these sizes. Retaining these small records is intentional.

Limits: actual power-loss behavior is not tested; manual verification is necessary
for ambiguous external sends; priorities do not guarantee fairness; finite tests
are not a lifetime reliability or whole-JVM heap bound. Renewal bounds the lifetime
of a watcher network, but high fan-in, slow IO and disk backlog still need operational
monitoring. Completion files and abandoned producer replies can grow on disk.

## Service operation

Service: `heuristic-experiment-scheduler.service`. Deployment status and rollback
instructions are recorded in `/home/lain/reports/heuristic-scheduler-20261115/deployment/`.
Production configuration: `/home/lain/.local/state/lain-scheduler/config.json`.
Queue: `/home/lain/.local/state/lain-scheduler/queue`.

```sh
systemctl --user status heuristic-experiment-scheduler.service
systemctl --user stop heuristic-experiment-scheduler.service
python3 tools/scheduler/disk_tasks/queue.py \
  /home/lain/.local/state/lain-scheduler/queue /absolute/path/to/task.json
```

The previous pilot already delivered its completion event. It is not re-enqueued
when replacing the old service. No new benchmark is scheduled automatically.

The real-delivery self-test subsequently arrived in the conversation and was reviewed. The deployed clock continued advancing with zero active tasks and no reported errors; no new test or experiment was launched for that acknowledgment.

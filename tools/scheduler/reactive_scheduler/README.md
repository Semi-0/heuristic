# Lain ingress and isolated observation networks

This revision keeps the tested task runner and adds two explicit Lain networks.
The deployed predecessor remains in `../disk_tasks/` for rollback.

```lain
(define runtime
  (network [clock queue]
    (map-tasks clock :watch-task queue)))

(define receive-tasks
  (network [inbox queue]
    (let [request (inbox:subscribe inbox)]
      (inbox:acknowledge inbox (queue:enqueue queue request)))))

(define observe-scheduler
  (network [queue scheduler log]
    (log:append log (queue:updates queue))
    (log:append log (runtime:updates scheduler))))
```

`start.lain` connects clock, queue, inbox, log and these three networks. It is
admitted with session extension. `map-tasks` still accepts the registered
`:watch-task` module, not an arbitrary captured closure. `worker.lain` is unchanged:
Lain selects notification, acknowledgment, deadline and renewal behavior.

## Submission really passes through Lain

`inbox:subscribe` emits one versioned command value at a time. It holds the next
command until acknowledgment, avoiding overwritten undelivered input. The Lain
connection activates `queue:enqueue`, which submits admission to the existing
single writer. A persisted admission receipt activates `inbox:acknowledge`; that
writes the client reply and releases the next command. The task runner no longer
reads the inbox or directly admits commands.

The `queue.py` client and JSON schema are unchanged. Producer writes are immutable
inbox commands. Only the managed effect writer changes queue state. Replayed command
IDs reuse `admissions/<command-id>.json`; replayed task IDs retain the existing
on-disk deduplication rules. Recovery/reconciliation still uses the same path.

## Independent observation

Queue admission and runtime start/create/retire events are published as immutable
pending records. `queue:updates` and `runtime:updates` expose them to Lain. The
separate observer network forwards them to `log:append`, a file-IO capability.

Log location: `/home/lain/.local/state/lain-scheduler/queue/log/*.json`.
Each file contains `id`, `kind`, `action`, `at`, and `data`. One atomic JSON file per
event gives idempotent append semantics without a partially written JSONL tail.
Pending events are removed only after the log record is persisted. Observers can
be connected after work has started and drain the recorded events.

A sink failure is recorded separately in `observer-errors`; it leaves its pending
event and pauses that observer stream until restart. Admission and worker execution
do not depend on logging receipts. The test injects a log failure and confirms task
completion without a parent host failure. This does not imply continued operation
when the shared disk itself is full and queue persistence also fails.

## Evidence and growth

Runtime pin: clean `3c3f643`. Local reports:
`/home/lain/reports/heuristic-scheduler-20261115/ingress-final/` and `ingress-real/`.
The first attempt (`ingress-1`) failed on a test-only filename collision; preserved.
`ingress-2` passed after correction.

Compiled tests prove an inbox request stays untouched without `receive-tasks`, then
is admitted when that network is connected. Observers attached afterward log both
queue and runtime events. Eleven task deliveries and replay checks passed; an
injected observer failure did not stop a task. There were 34 normal log records.

Across ten additional tasks in the final controlled run:

| Measure | Before | After |
| --- | ---: | ---: |
| Parent nodes | 1459 | 1459 |
| Managed effect receipts | 11 | 61 |
| Serialized network bytes | 10610056 | 11459128 |
| Used JVM heap after requested GC | 42417648 | 42914216 |

Observed heap delta: 496568 bytes, about 49.7 kB/task in this short run. This includes
JVM/compiler effects and is not a reliable long-run slope. Serialization size is
not heap size. Live ingress/observer histories are retained; this revision **does
not claim bounded parent history**. They are intentionally allowed to grow at the
low-volume experiment-monitoring workload, with the existing service memory limit.
Worker networks still retire/renew. Completed task and log records remain on disk.

The process test retains concurrent producers, fresh-process replay suppression,
crash-during-send quarantine and explicit reconciliation. Priority applies among
already admitted pending tasks; streaming admission does not batch all incoming
commands before starting workers. No global ordering is promised for simultaneous
inbox producers. Python and Clojure formatter/linter checks are included.

The service entrypoint is `run.sh` / `main.clj`; observation remains a separate Lain
network. Status JSON is supplementary host diagnostics. No kernel change, experiment
launch capability, commit or push. Deployment details/rollback live in
`/home/lain/reports/heuristic-scheduler-20261115/ingress-deployment/`.

The ingress self-test event subsequently arrived in this conversation. Its successful receipt and four observer events were reviewed; production status showed an advancing clock, zero active tasks, and no scheduler/observer errors. This confirms follow-up delivery in this thread, not universal recovery guarantees. No experiments were launched or restarted.

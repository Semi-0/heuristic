# Disposable task scopes

Latest tested architecture: [disk-backed map-tasks effect](../disk_tasks/README.md). It removes per-task parent growth in the controlled run and remains undeployed.

`runtime.lain` maps an open Lain queue to independently owned task sessions.
`worker.lain` owns the observation, completion/deadline choice, notification,
acknowledgment and retirement request. `adapter.clj` installs capabilities through
session extension, broadcasts one clock, and creates/closes child sessions through
the public host API. No kernel changes and no deployment.

```lain
(define clock (clock:shared 1))
(define tasks (let-cell [port] port))
(define notifications (runtime clock tasks))
;; A later session extension can supply an open queue prefix:
(define later (let-cell [port] port))
(-> (cons (task:record "example" "run-1" 100) later) tasks)
```

Clock values here are shared monotonic integer ticks. Deadlines use that same
coordinate system, not epoch seconds. One `clock:shared` call per manager is the
supported contract. The caller supplies an IO interpreter to `adapter/manager`;
this module does not launch experiments or send real notifications by default.

`map-tasks clock module tasks` accepts a registered source-module identity, currently
`:watch-task`. It is **not** an arbitrary captured lexical closure mapper: captured
closures cannot simply be moved into independent environments. Each worker imports
its own Lain definitions and gets explicit task and clock capabilities. More worker
modules need an explicit trusted registry before this becomes a general library.

Task identity is `[id unit]`, with a new unit required for a new run. Replaying a
completed identity returns its receipt without creating a child. A duplicate of
an active identity returns `already-active`; it does not subscribe to the eventual
result or own/cancel the original child. The primary queue entry owns that scope.

On `task:finish`, a separate retirement executor closes the child, removes its
clock subscription and releases the strong owner reference before publishing the
receipt. Cleanup failure retains the scope and records an error. Closing the parent
also closes active children. An already-sent external side effect cannot be undone.
The IO boundary remains responsible for durable notification claims and idempotency.

## Evidence and limits

The focused test uses real Lain compilation, session extension, a real timer and
real child-session closure, with a **controlled recording IO sink**. It sends no
Codex message and launches no benchmark. It tests completion, deadline, open-list
extension, admission replay, duplicate completed identity, continued clock ticks,
no polling after retirement, rejected late callback and parent cancellation.

Initial evidence: `/home/lain/reports/heuristic-scheduler-20261115/map-task-1/`.
The initial run passed with four created/retired children, four subscribed/unsubscribed
clock listeners, one shared clock and three expected alerts. Final verification after the race fix and added network probe passed under
`/home/lain/reports/heuristic-scheduler-20261115/map-task-2/`: zero live weak owner
references, zero live weak final-network references, zero active scopes and zero
subscribers. Formatting/lint reported zero errors/warnings. The combined quality
and test job exited 0: 45.853 s wall, 44.782 s CPU, 257.4 MiB peak memory. Runtime
pin: `3c3f643`; final managed unit: `lain-test-03d3672367ad4bf196ef408335b9569a`.
These are whole-job readings, not per-task admission costs. The runtime emitted an
existing Meander reflection warning; the new files had no lint findings.

Retiring the child permits collection of its network; it does **not** delete the
parent's queue spine, `task:run` nodes, effect receipts or completed-task index.
Weak-reference clearing in a short test is not a heap-growth bound or proof of
long-running stability. The diagnostic weak-reference arrays themselves also grow.
There is no cross-process queue recovery here, nor a new durable acknowledgment
store. These remain necessary before replacing the deployed monitor.

## Public name and entry reclamation boundary

The public Lain operator is now `map-tasks` (plural). Historical report paths and
the internal adapter namespace remain unchanged. This is a symbol rename only;
the earlier lifecycle runs predate the rename, and no new JVM run was performed.

Reading the pinned public session/host API found whole-owner close and whole-run
replace/rerun, but no supported per-subgraph deletion. `host/replace!` retires all
old effects, including the parent clock and active task scopes; it is not selective
entry collection. Thus the current implementation still retains parent entries.
A complete design must place per-task queue/application/result nodes inside a
retirable scope too, or introduce a runtime scope-removal facility. Simply removing
an adapter map entry cannot collect nodes still owned by the parent network.
Completed-task deduplication records need a separate retention/replay contract.
No kernel changes or new GC claim accompany this rename.

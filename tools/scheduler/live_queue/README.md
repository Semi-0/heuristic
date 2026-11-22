# Executed Lain live-queue orchestration

2026-11-15: controlled run passed on clean runtime `3c3f643`.

`runtime.lain` is the main program: map `watch-task` over a native connected Lain
list, observe each item against the same clock, select a completion/deadline alert
with `cond`, notify, then acknowledge its receipt. Completing a task never stops
the clock. `example.lain` supplies two tasks and an open queue tail. The run extends
that tail with a third task through the public session-extension API.

`run.clj` installs pure field/task-record primitives and managed IO capabilities
using `files/prepare-extension!` and `host/extend!`. `clock:every` is a real Java
scheduled clock publishing versioned callbacks. `queue:observe` reads controlled
external task statuses; `codex:notify` records delivery instead of contacting Codex;
`queue:acknowledge` records a matching receipt. Each effect runs through the existing
`async-op` / `EffectInterpreter` / session host. No host loop traverses the queue
or selects alerts. The host's bounded waits only drive/assert the test scenario.

## Observed result

- One clock subscription, nine ticks.
- Running tasks initially send no alerts.
- A completes: one `completed` notification.
- B remains running and reaches its deadline: one `deadline` notification.
- C is appended to the open list by session extension: one `completed` notification.
- All three receipts are acknowledged; extension replay and later ticks remain quiet.
- Exactly three notification attempts, not merely three deduplicated records.
- Zero clock cleanups during task completion; one cleanup when the owner closes.
- Managed owner reports no failures; runner exit 0.

Local evidence: `/home/lain/reports/heuristic-scheduler-20261115/live-queue-1.edn`
and `live-queue-1.log`. Recorded managed process: 20.127 seconds wall, 15.441 seconds
CPU, 247.1 MiB peak memory. These are whole-test readings, not a steady-state benchmark.
Source imports were subsequently cleaned up. The first lint check found a directory/namespace naming mismatch; the directory was corrected to `live_queue` and lint rerun.

## Boundaries

This is actual compiled Lain and managed effects, with synthetic task state and a
recording Codex sink. It did not send real messages, start experiments or replace
the deployed earlier monitor. The earlier real CLI wake-up test is separate.

The queue is an appendable Lain list, not an arbitrary mutable host vector.
Removal/cancellation, durable queue restoration, failure/retry contracts, stale
callbacks, unbounded queue growth and long-running observation retention are not
accepted by this short run. Task and event identities belong to the IO boundary;
the recording sink has an idempotency set, but this is not durable exactly-once
delivery. Deadline monitoring and experiment launch scheduling remain separate.

The superseded `../managed/` draft stopped each job's clock and had a repeated-start
failure (one expected start, three observed). It is not this successful run and
has not been deployed. The original monitor remains in place while the shared-clock
architecture is reviewed.

[Real IO follow-up](FULL-RUN.md): actual watchdog receipt, Codex submission, durable acknowledgment and owner-reopening checks now passed separately from the recording-sink run.

[Disposable-session map-task follow-up](../map_task/README.md) now passes controlled lifecycle and collection checks. It is separate from this real-IO adapter and remains undeployed.

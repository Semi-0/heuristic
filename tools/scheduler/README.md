# Small Lain experiment scheduler

Current replacement: [Lain ingress and isolated observers](reactive_scheduler/README.md). The earlier monitor described below is preserved for rollback; its pilot already completed. Deployment receipt: `/home/lain/reports/heuristic-scheduler-20261115/ingress-deployment/result.json`.

`policy.lain` (12 lines) owns wait/launch/wake/done decisions. `engine.clj` installs
it with public `session-extension` / `extend-session`, then advances one versioned
observation cell. No domain cells are pre-seeded by the host; no kernel or heuristic
model implementation is changed. This follows How to Think in Lain §§1, 9, 18, 24:
explicit relationships/state, Boolean selection, declared extensions and IO outside
primitive activation. The only native primitive is a checked record field accessor.

`scheduler.py` samples status, interprets the selected action and records effects.
It can either attach to a known watchdog unit (the deployed configuration) or launch
one configured argv through the existing watchdog after three idle samples. It
never chooses experimental questions, replies or model repair phases.

## Deployed instance

- Service: `heuristic-experiment-scheduler.service` (user service).
- Config: `/home/lain/reports/heuristic-scheduler-20261114/pilot-config.json`.
- State/decision/notification receipts: sibling `pilot/` directory.
- Attached job: the already authorized `corrected-2` cumulative pilot.
- Destination: this user's existing Codex thread, explicitly configured by ID.
- Poll: 15 seconds. Monitor deadline: 12 hours from configuration creation.
- Monitor limit: 896 MiB, half a CPU; JVM heap 512 MiB. Pure session rebuilt every
  32 observations to bound telemetry retention; effect claims remain on disk.

```sh
systemctl --user status heuristic-experiment-scheduler.service
systemctl --user stop heuristic-experiment-scheduler.service
# Also prevent startup after login/reboot:
systemctl --user disable heuristic-experiment-scheduler.service
```

Stopping the monitor does not stop the experiment. No restart policy relaunches
an interrupted test. The monitor deliberately runs independently of the test's
watchdog unit so it can observe a resource cancellation and notify the agent.
It remains a bounded local monitor, not an exception for running heavy tests.

## Wake-up boundary

A terminal receipt (success, failure or interruption), an inactive unit without a
receipt, or the monitor deadline selects `wake`. Deadline is not success and does
not kill the test. The adapter calls the installed CLI:

```sh
codex queue --thread THREAD_ID --message MESSAGE --resume
```

The installed command documents that resume failure can warn while exit remains
zero. Both streams and the exit code are retained; warnings require review.
Successful enqueue/resume submission is not proof the agent has completed review.
The message asks the agent to inspect evidence, update reports and notify the user,
without restarting interrupted work or committing/pushing.

A durable `sending` claim precedes notification. On a crash between claim and CLI
completion, delivery is ambiguous: restart will not resend automatically. `sent`
means CLI success without warning; `needs-review` records a reported problem.
These are bounded duplicate-avoidance semantics, not exactly-once delivery.
If launching a job and the monitor dies before persisting its identity, reconcile
its watchdog registry/receipt manually; it will not launch it again automatically.
The daemon, CLI and authenticated local Codex service must remain available.

## Verification

Pinned test runtime: clean `3c3f643` export and recorded classpath. Tests run through
the resource watchdog, not against production services. `test_scheduler.py`
covers all 64 policy combinations through compiled Lain, repeated observations,
bounded session reopening, warning receipts and notification restart suppression.
A guarded launch smoke uses synthetic idle telemetry and a real `/usr/bin/true`
managed job. The end-to-end wake smoke observes a real completed test receipt,
evaluates Lain and queues a labelled message to the actual configured thread.

Evidence: `/home/lain/reports/heuristic-scheduler-20261114/`, especially `tests.log`,
`launch-test.log`, `smoke/status.json`, `smoke/wake-receipt.json`. The initial test
had an unclosed stdout pipe warning; the adapter was corrected and the focused
suite rerun cleanly. Formatter/linter report zero errors/warnings for engine.clj.
The wake smoke was queued while the destination turn was active. After that turn
ended, the event arrived as a new user-channel turn and the agent reviewed the
receipts and updated documentation. This verifies deferred follow-up delivery in
this thread; already-idle/disconnected clients and daemon restarts remain untested.

For a new job, use a fresh state directory and config containing `name`, `thread`,
`evidence`, `classpath`, `runtime`, absolute epoch-seconds `deadline`, and either a
known `unit` or an explicitly authorized `command` argv plus `memory_mib`.
Configuration is trusted local input. Do not overwrite an existing run's claims
or edit running experiments to attach notification. No commits/pushes performed.

## Shared-clock refinement

[The live-queue Lain program](live_queue/README.md) was compiled and run successfully on November 15 with recording notifications. It keeps one shared clock and watches an appendable queue. It is not yet the deployed service described above.

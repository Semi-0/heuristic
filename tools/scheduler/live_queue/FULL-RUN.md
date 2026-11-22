# Real IO extension — 2026-11-15

The existing runtime.lain was extended through the public session API with
bridge.clj: real wall-clock callbacks, watchdog receipt observation, Codex CLI
notification and durable notification/acknowledgment records. io.py executes one
requested IO operation per invocation; it does not poll or choose alert branches.
The shared clock, map-list traversal and alert policy remain in Lain.

A fresh session first ran a clock with an empty open task queue. A subsequent
extension added a task referencing an actual successful watchdog test receipt.
The first owner recorded one notify and one acknowledgment. The CLI exited zero,
returned queued-message ID 01a1dc94-4efd-75c0-91cf-e92f69a7b1d0, and emitted no warning.
An extension replay and duplicate queue position caused no second notification.
A second fresh owner reused the saved acknowledgment: nine observation effects,
zero notification effects. Each owner had one clock subscription, cleaned up only
on owner close. Invoking each retained callback after closure returned :closed.
The complete test exited zero; 31.252 s wall, 24.964 s CPU, 294.9 MiB peak memory.

Evidence: /home/lain/reports/heuristic-scheduler-20261115/full-1/ contains
full-result.edn, run.log, quality.log and per-task observed/state/notification JSON.
The notification subsequently arrived as a user-channel event, and the agent read
and verified both receipts. This proves delivery and follow-up review in this thread;
it does not prove disconnected-client or daemon-restart recovery.

This is real IO integration with a labelled self-test notification, not deployment
of a general scheduler. It does not launch or alter experiments. The existing
production monitor remains unchanged. The two owners are in one JVM, with fresh
Python IO processes reading durable state; whole-process crash recovery was not
tested. Claim-before-send prevents automatic duplicate sends but can leave an
ambiguous sending record after a crash; no exactly-once claim. Failed/ambiguous
submission cannot be acknowledged as success. External effects already sent cannot
be undone by a rejected late callback.

Currently a task is acknowledged once, so a deadline alert suppresses subsequent
completion alerts for that task/run. Queue removal, event-by-event subscriptions,
retirement/GC of completed watchers, long-running observation growth and durable
queue reconstruction remain unfinished. A lifecycle-aware map-task is the next
proposed abstraction; plain map-list retains completed watchers. Cancellation and
reference release must be distinguished from actual graph/memory reclamation.
No kernel edits, production replacement, commits or pushes.

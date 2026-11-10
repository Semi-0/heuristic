# Durable executable reuse: preflight and open decision

After the successful cumulative catalog experiment, the user requested durable
execution, documentation, and an explicit pause when a design choice is uncertain.
This is a read-only runtime audit, not a completed durability experiment.

## Observed machinery

Runtime checkout: `lain-harness-runtime`, revision
`2773dffc1cf5a2b129559aa445d66d09f96d4dd7`.

- `propagators/compiler_2/session.clj`: the public `rerun` operation creates a
  fresh compiler session/run and reinstalls declarations. It is not a disk
  serialization/import API for a live session.
- `propagators/compiler_2/runtime/session/instance_replay.clj`:
  `export-instance` records versioned clients, source commits and inspection
  snapshots. `import-instance!` reconstructs through source commit replay. This
  is the versioned editor/runtime path, not a demonstrated durable import for
  this harness's public-session extensions and scoped admissions.
- `propagators/compiler_2/session/connected.clj`: `freeze` resolves connected
  finite data but treats callables and lexical references as opaque. It does
  not provide a portable executable closure checkpoint.
- `propagators/compiler_2/runtime/retained_network.clj`: retained callback
  snapshots support in-memory reuse. Their existence does not establish
  cross-process serialization of native propagators, resources or closures.
- The harness catalog already persists descriptions, examples, source chunks
  and admission evidence. Its executable reference registry is process-local.
  Reopening Datomic alone does not repopulate that registry.

No general portable live-graph restore API was found in these inspected paths.
No kernel, runtime, service, or experiment source was changed during this audit.

## Decision requiring clarification

Two different acceptance targets could reasonably be meant by “durable executable”:

1. **Restore from saved executable source and dependencies.** Terminate the old
   process, reopen the database in a fresh process, rebuild the saved pure
   callable definitions and captured dependencies through public compiler/session
   APIs, rebind their persistent catalog identities to new live references, then
   let a real model discover and invoke the restored closure. No model regeneration
   of its implementation. This tests durable executable reconstruction, not exact
   suspended-state resumption. This is the recommended bounded first experiment.
2. **Resume the serialized executable graph/state itself.** Preserve cell state,
   lexical captures, dependency information and executable structure across process
   termination without recompiling source. The inspected APIs do not establish
   that capability. Its implementation needs a separate persistence design before
   claiming acceptance, while respecting the user's prohibition on kernel changes.

For option 1, proposed evidence includes distinct process identities; saved source
and dependency identities; no old model requests or reply deliveries re-executed;
actual database discovery in the reopened process; correct new applications;
and independent reactive updates without additional model calls. Restoring old
external effects is outside this pure-computation experiment.

The user subsequently selected option 1: save Lain code and its dependencies.
The completed implementation and real-model outcome are recorded in
[the source-restoration experiment](2026-11-10-durable-source-restoration.md).
The audit above describes the pre-implementation decision, not the current status.

# Runtime and dependency recovery fixes

> **Status: kernel changes reverted at the user’s instruction.** The kernel changes described below are historical experimental variants, not active fixes. Both backend checkouts now match their respective HEAD revisions. Passing results recorded here, including the 84/84 controlled run, used those variants and do not verify the current harness against the unchanged kernel. Harness and Lain changes remain pending revalidation. No deployed service was restarted.

The subsequent [unchanged-kernel cross-check](2026-11-08-unchanged-kernel-cross-check.md) distinguishes native failures from harness usage and fixture setup errors, and records working `if` and completed-context alternatives.

The experiment keeps one Lain orchestrator. `soul-refined.lain` still connects the environment request, existing model and completed-response compiler. Phase transitions, readiness and bounded repairs remain in `experiments/phase-orchestration.lain`. The host supplies external inputs, forwards provider responses and records evidence.

## Two reproduced runtime faults

The generalization transition stalled because a primitive published an empty membership container while waiting, then a plain constant string when its input became available. Cell merge rejected those different domains. Native diagnostics recorded `Cannot merge membership evidence with this value`, with operands such as `"generalization"` and `{:membership/observations #{} :membership/states #{}}`.

`propagators/compiler_2/session/connected.clj` now normalizes an information-free pending membership container to bottom. Pending state knowledge with nonempty states is still retained. Constant scalar behavior remains unchanged. A compiled-Lain regression feeds a delayed value through `cell:first`, a record-producing closure and a lexical environment view, and checks all five readable context fields. This fails before the fix and passes afterward.

The tax contradiction was caused by a transient reconverging tuple: a new subtotal and an old delivery branch temporarily carried different versions of the same input. Scalar publication retained that invalid computed value; when the new branch dropped the delivery-fee dependency, its smaller support could not dominate the invalid older tuple.

`propagators/stdlib/supported_procedure.clj` now publishes only the known premise-state frontier for incompatible input versions. It does not publish a computed payload for that tuple. Compatible calculations, genuine equal-version conflicts, collection projection and conditional semantics remain unchanged. The regression covers direct arithmetic, tax and a closure, independent fee changes, input withdrawal and restoration. `tools/probe_reconverging_branch.clj` also records native debug-runner activation profiles and the four observed outcomes in `runtime/reconverging-branch-fix-20261108/`.

These native fixes and regressions were subsequently reverted in both backend checkouts. Their patch is retained only as historical evidence under `runtime/kernel-rollback-20261108/`; it must not be applied by the runner.

## Agent feedback

The environment inspection now exposes actual output supports, expected premise source identities and missing supports. Each question supplies reference requirements for its question premise and the shared-rule premise. Generic set operations let Lain include these dependencies in readiness; native inspection supplies facts without deciding phases or retries. A missing dependency produces `:missing-dependencies` feedback, alongside unchanged source, receipts and retained-prefix evidence. No expected numerical answer or handwritten pricing network is inserted into model requests.

Verification and full-run outcomes are recorded below when complete.

## Scoped boolean regression

A separate selector regression reproduced a scoped supported `false` choosing the true branch. The selector read the truthiness of the evidence container instead of its boolean payload. `propagators/stdlib/conditional.clj` now uses the existing payload projection for branch selection while preserving the original supported argument for publication. Both scoped boolean cases and the existing scalar/branch/publication/transport suites pass: 24 tests, 276 assertions. No global numeric merge or equality rule was changed.

## Session extensions without pre-seeded cells

All active harness extension declarations use `session/session-extension` with source/primitive declarations and the existing live host admission API. Source admission no longer injects a synthetic completion cell through `:cells`; receipts reference `session/source-result-cell` instead. `premises/source!` requires a Lain-declared endpoint and publishes information through `session/cell-input`. The temporary behavior evaluator produces its callable and arguments using propagators rather than seeding or replacing cell contents. Its 17 tests and 292 assertions pass. Runtime composition and premise ingress pass 2 tests and 15 assertions, including explicit checks that extension `:cells` remains empty and source-result references belong to canonical topology.

The static audit finds no `:cells`, `seed-cell`, `assoc-net-cell` or `cell/cell` use in `src/lain_harness`. Compiler/kernel internals and low-level operator test fixtures are distinct from host extension declarations.

## Snapshot lifetime and local resource limits

Completed phase descriptions and attempt observations now use one-time native observations. Lain compares a small live view of the five context fields before allowing a complete snapshot, and stops that live view when the phase reply arrives. Native adapters only read data and manage observation lifetime. Lain owns coherence, validation, readiness, dependency checks and correction budgets.

Environment lookup batches the runtime binding index once per view instead of scanning it once for every binding/frame. Unknown environments avoid that scan. Native debug profiling separately found lexical `:env/parent` and `:env/local-bindings` transport dominating compilation time; rendering text was not the main cost in that profile. The profile recorded finite work (54,238 activations), with 16.4 seconds in parent transport and 12.3 seconds in local-binding transport. General lexical transport performance remains a limitation.

The experimental recovery module uses the existing experiment allowance (300,000 steps, 180 seconds); global compiler defaults are unchanged. Its five tests and thirty assertions pass, including delayed context, bounded repairs and snapshot lifetime. The larger local control additionally records an explicit 600-second time allowance while retaining the 300,000-step cap. This allowance is not evidence that the general performance issue is solved.

## Actual provider trial and remaining acceptance

`runtime/printshop-support-feedback-live-20261108/` contains five actual requests in one session: analysis, generalization, implementation and two corrections. The initial implementation was truncated and rejected before admission. The first correction admitted ten forms, included both premises, and produced correct production and rush values, but other outputs became contradictory. The final correction contained prose and was rejected; Lain exhausted its declared budget. The missing-support map was empty for the admitted correction, but that does not establish correct values, independence or mutation behavior.

That trial preceded the scoped-boolean fix and the final snapshot-lifetime changes. It is retained as a failed real trial; it is not reported as five-question acceptance. Earlier replays stopped by short watchdogs or propagation limits are likewise not acceptance evidence. No provider source was manually corrected or replaced.

Broader checks have known baseline failures: 92 backend tests / 1,604 assertions retain the same four failures as the unmodified backend, and the legacy foundation module retains eight import-snapshot errors across 48 tests / 538 assertions, also reproduced with the unmodified bridge. These are reported separately from the passing focused suites.

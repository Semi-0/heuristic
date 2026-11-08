# Cross-check against the unchanged kernel

Both backend checkouts are unmodified. The runtime checkout is at `2773dffc1cf5a2b129559aa445d66d09f96d4dd7`; the canonical research checkout is at `a6edf223823bf4e4dbf779980caebeafce3141ee`. Their only committed difference is the language philosophy document. No kernel patch, runtime rebinding, service restart, or deployment is part of this audit.

Evidence is under `runtime/unchanged-kernel-audit-20261108/`. This audit supersedes the earlier assumption that patching the kernel was necessary to continue. It does not establish full experiment acceptance.

## Native tests and runnable comparisons

**Follow-up correction:** the four experimental assertion failures below are not four demonstrated failures of compiled Lain consumers. The [public session API follow-up](2026-11-08-session-api-audit.md) reproduces the cases through source and producer extensions. Lain `car`/`cdr` consumers preserve the required support and withdraw correctly; direct inspection of raw list storage bypasses that access boundary. A changing list shape also updates the Lain consumers successfully. The old requirement to reject that change needs contract review.

`tools/audit_unchanged_kernel.clj` runs eight existing native suites: scalar primitives, branching, publication, premise transport, premise closures, compound access, immutable sessions, and session extensions. There are 90 tests and 601 assertions: 597 pass and four fail. All four failures are in the existing premise-closure suite, concerning returned-list member supports, nested-list withdrawal readiness, and a changing return interface. They are not new assertions introduced by this audit.

Plain `false`, lexical closure parameters, premise-gated closure parameters, and slot-derived `false` all select the correct branch in compiled comparisons. Native slot comparisons use `p:slot`; harness comparisons explicitly import the combinator library through the existing snapshot declaration path. Earlier exploratory probes omitted that library or incorrectly supplied multiple forms to the single-expression native fixture; their failed logs are retained but excluded from conclusions.

`tools/audit_reconvergence_usage.clj` follows the publication and compilation pattern in `test/propagators/ttms_primitives_test.clj`, with one changed input and a fixed fee. The native lifecycle example, a direct branch, and arithmetic with unchanged dependencies all pass initialization, correction, withdrawal, and restoration.

This expression fails only at correction from subtotal 185 to 257:

```lain
(* (+ subtotal (select (>= subtotal 250) 0 fee)) 0.08M)
```

It initially produces 15.76, becomes contradictory instead of 20.56 after correction, withdraws to unknown, and restores to 15.76. Splitting the two tax terms still fails with `select`. Both of these equivalent formulations pass all four stages:

```lain
(* (+ subtotal (if (>= subtotal 250) 0 fee)) 0.08M)
(if (>= subtotal 250)
  (* subtotal 0.08M)
  (* (+ subtotal fee) 0.08M))
```

This is a reproducible limitation of this `select` composition, rather than evidence that ordinary arithmetic or every selector is broken. The controlled pricing fixture now uses the proven `if` form. That fixture is an explicit controlled input; it is not substituted for provider-generated source.

## Delayed context and completion boundaries

The original delayed-context regression used `env:slot`, while the phase helper uses `env:reference-slot`. `tools/audit_context_usage.clj` tests both. Both immediate variants expose all five expected fields; both delayed variants lose four fields to contradictions. A direct delayed record projection succeeds, so the failure is not universal delayed-record behavior. Replacing projection with a native scalar adapter alone did not solve the context case.

Freezing the complete context with `cell:first` before field projection makes the delayed reference-slot variant pass all five fields. The Lain phase helper now uses:

```lain
context (cell:first (cell:value context-reference))
```

The corresponding fixture freezes its context argument before reading fields. This expresses the intended one-completed-context-per-phase semantics in Lain. It does not modify kernel merge rules or install a host phase controller. It is a demonstrated correction to harness usage; it is not proof that all delayed compositions are supported.

## Harness baseline

Before the context correction, behavior evaluation passes 17 tests / 292 assertions, operator examples pass 3 / 108, and runtime composition passes 2 / 15. The recovery module has 5 tests / 13 assertions, with three failures and two timeout errors. The delayed context assertions fail and the two repair scenarios time out; the invalid-plan and one-time snapshot checks pass.

The broader harness audit also reruns the legacy modules. Eight legacy foundation errors are unsnapshotted `model-refined.lain` imports. Their fixture assembly differs from the current `model/declaration` import path; they must not be counted as arithmetic/kernel failures. The legacy model module and a source replay test time out, and the legacy streaming integration also encounters the import setup error. The first model-IO run lacks permission to bind its HTTP test server; that environmental error is separated from the authorized loopback rerun.

After adding the completion boundary, recovery has 11 passing assertions, no assertion failures, and two timeout errors across five tests. The formerly failing delayed-context assertions now pass. A targeted rerun records the actual rejected admission receipt and all three retained prefix forms, so the residual stall is after successful failure reporting by the compiler and before a repair request is emitted. This boundary is a partial improvement, not a complete recovery fix.

The separately authorized loopback rerun of model IO passes six tests and 45 assertions. Together with behavior, operator examples, and runtime composition, that is 28 focused tests and 460 passing assertions. The broader legacy harness run remains unsuccessful: the import setup errors, source replay timeout, and legacy model timeout are retained in `harness-suites.log`; its socket permission error is superseded by `model-io.log`.

Two controlled recovery experiment attempts are recorded separately: `runtime/unchanged-kernel-printshop-control-20261108/` and `runtime/unchanged-kernel-printshop-context-barrier-20261108/`. Both naturally finish with a round-one outcome timeout and report cleanup `:closed`. The baseline records one response (analysis); the context-boundary variant progresses through three responses (analysis, generalization, initial implementation), then stalls before issuing a correction. Neither reaches the pricing/mutation acceptance checks, so the `if` alternative is verified by the isolated native lifecycle comparisons, not by a completed print-shop run. Their isolated source snapshots and empty runtime patches make the tested variants explicit. No new real-provider trial is claimed while these controls remain unsuccessful.

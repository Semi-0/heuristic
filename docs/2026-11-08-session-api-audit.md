# Public session API and returned compound data

The harness source, tools, and tests were searched for extension constructors, low-level installation, cell seeding, and raw compiler entry points. Active extension declarations use `propagators.compiler-2.session/session-extension`, or the public `session.files/prepare-extension!` for source imports. Pure admission uses `session/extend-session`; live admission uses `host/extend!`, `host/extend-source!`, or `host/submit-extension!` with receipts. No extension in these active directories supplies a `:cells` map of initial values. Historical snapshots under `runtime/` are evidence, not active implementations.

Trusted native operator bundles remain bootstrap capabilities and primitive implementations. The private behavior evaluator still uses existing temporary application machinery; it is not a session-extension entry point. Lexical `:frames` and `:target` declarations retain existing supported child admission and do not seed domain values. The main `soul-refined.lain`, `model-refined.lain`, and `lib/compiler.lain` are unchanged.

## Extension-only reproduction

`lain-harness.session-publication` declares a pure producer using the public `op/prim-op`, then supplies Lain source connecting its result to an already-declared endpoint. Each revision has an immutable declaration identity; equal replay is a no-op and conflicting reuse is rejected. `op/frozen-primitive` preserves declaration equality without a host registry. No direct input call, low-level compiler entry, cell installation, or network mutation is used by this helper.

The list reproduction and both reconvergence tools now use that path. The comparison portion of the native audit also uses it; explicitly invoked existing backend test suites remain reference tests from their original repository. The earlier low-level diagnostic source and results are retained as historical evidence.

The documented checked-in public API does also expose `session/cell-input` and `session/advance` for external facts. Existing real input/callback ingress uses public session/host APIs. The stricter reproductions here avoid those calls entirely: they extend producer declarations instead. This is distinct from removing the public input API or replacing application IO with a second orchestrator.

## What the four old failures actually demonstrate

The experimental namespace `propagators.experimental.ttms-application-test` is not listed in the default production test registry. It was explicitly invoked during the harness audit to compare available capabilities. Its questions and assertions were not inserted into model context or used as model-generated solutions.

Three failed assertions inspect internal returned-list cells: two expect definition support directly on raw leaf storage, and one expects an internal nested-list reference to become unavailable. Those stored objects remain readable internally. In contrast, compiling these Lain consumers through a source extension carries the definition premise correctly:

```lain
(define f
  (ttms-premise-closure
    (network [x] (list (list x (+ x 1))))
    definition-one))
(define result (f 5))
(define accessed (car result))
(define left (car accessed))
(define right (car (cdr accessed)))
```

With an active premise, `left` and `right` are 5 and 6 with that premise's support. Withdrawal makes the Lain consumers unknown. Restoration returns 5 and 6 with revision 3 support. The source endpoint and premise producer are admitted before the function source, matching the original fixture's fact-before-consumer setup without pre-seeding cells. The initial alternative test had four failures because it assumed an unpublished premise would prevent early output when the function was admitted first. That stronger assumption is not established by the original test and is separate from withdrawal/restoration.

The fourth old assertion expects an exception when a chosen function changes from returning `(list x)` to `(list x x)`. The public session reproduction instead updates the returned list to `[5 5]`; compiled consumers receive the second value and current premise support. This does not establish that every return-interface change is legal; it shows that this old exception expectation is not sufficient evidence of a user-visible failure in this case. No backend assertion or implementation was changed.

## Verification

`bash tools/test_module.sh session-api` passes 3 tests and 36 assertions, covering flat/nested consumer supports, withdrawal/restoration, extension replay/conflict handling, stable result identity, empty seed-cell catalogs, and observable return-shape updates. The module is included in the harness `all` selection. Runtime composition passes again: 2 tests and 15 assertions. Logs are in `runtime/unchanged-kernel-audit-20261108/session-api-fact-first.log` and `runtime-composition-after-api-audit.log`.

`closure-extension-only.log` records eleven successful compiled closure/selector comparisons. `reconvergence-extension-only.log` retains the specific `select` correction failures, while equivalent `if` expressions pass all four lifecycle stages. Moving entry through the high-level API does not erase that arithmetic difference. Full experiment recovery remains unsuccessful; no new real-provider acceptance is claimed.

## CI status is separate

The checked backend revision `a6edf223823bf4e4dbf779980caebeafce3141ee` has a failing `gx10/ci` status. The GX10 deployment receipt and journal show that its November 7 attempt stopped at formatting checks for `propagators/compiler_2/session/supported.clj` and `test/propagators/compiler_2/stream_extension_test.clj`. The pipeline stopped before runtime tests, so it cannot establish that all runtime tests passed or that these four experimental assertions caused the CI failure. GitHub Actions workflows are intentionally replaced by GX10 CI in this repository. This audit did not restart deployment or alter either backend checkout.

### Formatting-only follow-up

After the user requested autoformatting and linting, the pinned `format-fix` command was run in the canonical research checkout against the same deployed-release baseline, `af7f3a77f8021332e3ca4ffaba4bb23e0d1c72f3`. It changed only whitespace in the two files named above; `git diff --ignore-all-space --exit-code` and `git diff --check` pass. This explicitly requested style correction supersedes the earlier unchanged-checkout statement for the canonical checkout only. The harness runtime checkout and deployed service remain untouched.

Local checks now pass: formatting checks all 18 CI-selected files with zero failures; lint checks those 18 files with zero errors and 47 non-blocking warnings; quality-tool tests pass 9 tests and 27 assertions. Logs are `ci-autoformat.log`, `ci-format-check-after.log`, `ci-lint-after.log`, and `ci-quality-tests-after.log` under the audit evidence directory. These are local results; no new remote CI or full deployment run is claimed.

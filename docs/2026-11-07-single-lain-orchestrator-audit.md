# Orchestration audit and dependency failures

The earlier real recovery experiment (`runtime/printshop-recovery-live-snapshot-20261107/`) had a second controller: Clojure selected analysis/generalization/implementation phases and correction attempts. Lain owned the worker, requests, generated computations and admission, but did not own the entire experiment policy. That run must not be described as proof of Lain-only orchestration.

## Current correction

`experiments/phase-orchestration.lain` now owns phase transitions, plan validation, output readiness, retained-prefix feedback and the two-correction limit. `experiments/printshop-three-stage-requests.lain` connects user questions to that policy. `soul-refined.lain` remains the main runtime and imports the existing model and completed-response compiler. Its source, `model-refined.lain` and `lib/compiler.lain` are unchanged.

The active probe only publishes external questions and premise inputs, forwards provider traffic, observes Lain outcomes, and independently checks results. Its timeout is a watchdog; it never chooses a phase or retries. `src/lain_harness/experiment_observation.clj` supplies pure data operations and native observations. The admission adapter additionally publishes accepted-prefix receipts; it makes no retry decisions. The previous host controller is preserved only as historical evidence under `runtime/orchestration-audit-20261107/before/`.

Focused compiled-Lain recovery tests pass (3 tests, 22 assertions), including a rejected response with a retained prefix, an admitted response with unresolved outputs, two corrections, exhausted corrections, invalid planning, and replay suppression. The unchanged main runtime composition test also passes (1 test, 9 assertions). Full five-question verification of the new policy remains incomplete. The initial replay stalled before its first provider request: a redundant construction-readiness guard became contradictory. Removing that guard preserves all 22 focused assertions. The latest replay (`runtime/printshop-lain-policy-natural-inputs-20261107/`) sends `analysis-1`, receives a completed saved response, and validates it (`a-ok=true`), but stalls before generalization because the next rendered context does not match its requested context. No fresh provider calls were made in these replays. The remaining snapshot/transition problem is not fixed or claimed as accepted. The earlier successful real-provider run remains evidence for model repair, not for the corrected architecture.

## Why fourteen checks failed in the earlier real run

All 35 initial numeric checks passed. The fourteen failures concern dependency maintenance:

| Checks | Count | Evidence |
| --- | ---: | --- |
| Question premise on each result | 5 | Generated source never attached `question-N` to the computation. |
| Shared-rule premise on each result | 5 | Generated source never attached `shared-rule`; it did not use `ttms-premise-closure`. |
| Quantity correction | 1 | Expected 277.56; observed contradictory. |
| Quantity withdrawal | 1 | Expected unknown; observed contradictory. |
| Question withdrawal | 1 | Expected unknown; observed contradictory. |
| Question restoration | 1 | Expected 277.56; observed contradictory. |

An exposed environment binding does not automatically become a computation's premise. The first ten failures are missing connections in the model-generated network.

For the mutation failures, changing quantity from 80 to 120 correctly changes production to 232, subtotal to 257 and delivery to zero. Tax becomes contradictory inside the generated child, before the final output-slot connection; total then becomes contradictory. The recorded old tax support still includes `delivery-fee`, although the current free-delivery branch no longer needs it. This localizes the failure to evidence maintenance around a changed branch and reconverging computations; the exact primitive-level cause is not established.

Question withdrawal additionally cannot invalidate a result whose question premise was never attached. These checks run sequentially, so later checks can inherit an earlier contradiction. They are not fourteen independent arithmetic errors. The handwritten control with premise wrapping also has two baseline mutation failures (quantity correction and question restoration, returning unknown), so fixing the model's missing premise links alone is not proven sufficient.

No generated quote implementation was manually repaired to pass these checks. No deployed service was restarted.

## Subsequent fixes

See [the November 8 fix report](2026-11-08-runtime-and-dependency-recovery-fixes.md) for the reproduced primitive-domain, mixed-version and scoped-boolean defects, the migration away from pre-seeded extension cells, current verification and remaining full-experiment limitations.

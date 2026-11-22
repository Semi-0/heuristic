# Heuristic documentation

## Current maintained pages

- [Status and evidence](STATUS.md)
- [Design and source map](DESIGN.md)
- [Plan and acceptance gates](PLAN.md)
- [Remaining issues](ISSUES.md)
- [Original research proposal](lain-agent-harness-proposal.md)

## Recent experiment reports

- [November 21 — planner delegation, verification and memory](2026-11-21-planner-delegation.md).
- [November 18 — context retrieval prompt trials](2026-11-18-context-prompt-repair.md).
- [November 17 — queryable context and the matched pilot](2026-11-17-queryable-context.md).
- [November 16 — direct Flash and Pi comparison](2026-11-16-pi-model-extension.md).

- [November 14: cumulative-history benchmark pilot](2026-11-14-cumulative-history-pilot.md) — corrected request envelope and terminal results.
- [November 13: semantic validation and bounded repair](2026-11-13-semantic-validation-repair.md).
- [November 13: dependency-aware catalog reuse](2026-11-13-dependency-aware-catalog-reuse.md).
- [November 11: missing-output repair](2026-11-11-missing-output-repair.md).
- [November 11: agent-directed durable accumulation](2026-11-11-agent-directed-durable-accumulation.md).
- [November 10: durable source restoration](2026-11-10-durable-source-restoration.md).
- [November 10: durable executable preflight](2026-11-10-durable-executable-preflight.md).
- [November 10: cumulative catalog closures](2026-11-10-cumulative-catalog-closures.md).
- [November 10: catalog discovery](2026-11-10-catalog-discovery-experiment.md).
- [November 10: autonomous repair continuation](2026-11-10-autonomous-repair-experiment.md).

## Historical reading index

The following earlier index is retained for navigation. Use STATUS above for current conclusions; dated reports retain their original scope and runtime pins.

# Language context

Start with [the current research status and evidence map](research-status-2026-11-08.md). It organizes the design, passed conditions, failed conditions, corrected diagnoses, reproducible checks, and remaining work. The [Tsuki handoff](handoffs/2026-11-08-heuristic-to-tsuki.md) is the bounded cross-project reading brief; the [publication receipt](publication-2026-11-08.md) records delivery and CI/CD status. Dated reports below preserve the history and should be read with their runtime/orchestration caveats.

The [returned Tsuki review](handoffs/2026-11-08-tsuki-review.md) is preserved for the next joint discussion; its proposed probes have not been executed.

The guide and examples are copied from the local Lain source tree at `projects/datalog-research/propagators/doc/`. Their internal implementation links refer to that upstream tree.

`soul-refined.lain` contributes a language-guide index and harness context through Lain environment composition. The index identifies documents; it does not pretend to load their contents. A future document capability must preserve source identity and revision when contributing contents. No generated bootstrap or fixed host description schema is required.

## Research intention

- [Lain Agent Harness](lain-agent-harness-proposal.md): the author's version 0.1 proposal for persistent computational networks as executable agent memory.
- [Experiment intention and next steps](experiment-intention.md): how wiring the model into `soul-refined.lain` tests executable reuse, the implementation rounds, and the separate persistence milestone.

## Experiment records

- [Three-stage interface generalization](2026-11-07-three-stage-generalization-experiment.md): explicit class/parameter decisions, optional abstraction, and primitive-composition protocol limits.

- [Requiring executable checks](2026-11-07-executable-checks-experiment.md): actual test construction, invalid-source failures, duplicate test formulas, and stale expectations.

- [Explicit analysis and inventory implementation](2026-11-07-planned-inventory-experiment.md): three two-stage trials, visible look-back decisions, and independent correctness checks.

- [Inspection examples and response format](2026-11-07-inspection-context-experiment.md): three fresh trials, actual inspection usage, partial completion, and remaining support/text failures.

- [Generalize reuse experiment](2026-11-06-generalize-reuse-experiment.md)
- [Lookback reuse experiment](2026-11-06-lookback-reuse-experiment.md)
- [Partial network experiment](2026-11-06-partial-network-experiment.md)
- [Printshop diagnostic feedback](2026-11-06-printshop-diagnostic-feedback.md)
- [Printshop strict procedure](2026-11-06-printshop-strict-procedure.md)
- [Printshop struct experiment](2026-11-06-printshop-struct-experiment.md)

- [Arithmetic reuse and premise-supported networks](2026-11-06-arithmetic-reuse-and-premises.md): actual model-generated source, context changes, failures, support checks and reproduction instructions.

- [Our first working Lain model IO experiment — November 4, 2026](2026-11-04-first-model-io-experiment.md): the journey from behaviors and model wiring to a live reply and semantic trace, including failed approaches, corrected diagnoses, and remaining bugs.
- [Model IO experiment](model-io.md): implementation, verification commands, and hosted editor details.

- [Clear runtime composition](2026-11-07-clear-runtime-composition.md): runtime definition, independent seeds and completed-response compilation.

- [Environment-visible recovery experiment](2026-11-07-environment-recovery-experiment.md): bounded corrections using actual failures and the same live child.
- [Single Lain orchestrator audit and fourteen dependency failures](2026-11-07-single-lain-orchestrator-audit.md)
- [Runtime faults and environment-visible dependency recovery](2026-11-08-runtime-and-dependency-recovery-fixes.md)
- [Unchanged-kernel cross-check: runnable examples, context boundaries, and remaining failures](2026-11-08-unchanged-kernel-cross-check.md)
- [Public session API audit and compiled Lain compound consumers](2026-11-08-session-api-audit.md)

- [Scheduler map-task lifecycle and collection evidence](../tools/scheduler/map_task/README.md) — controlled November 15 run; no deployment.

- [Disk-backed map-tasks effect](../tools/scheduler/disk_tasks/README.md) — stable parent graph, persistence-before-retirement and controlled recovery checks.

- [Lain ingress and isolated observer networks](../tools/scheduler/reactive_scheduler/README.md) — live command propagation, file event logs, measured retention and deployment.

- [Pi backend through the existing model session extension](2026-11-16-pi-model-extension.md) — implementation, control checks and terminal comparison.

- [Queryable context: implementation and negative pilot — November 17](2026-11-17-queryable-context.md) (controlled gates pass; indexed acceptance failed; all runs terminal).

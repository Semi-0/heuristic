# Heuristic — a Lain agent harness

Heuristic explores executable agent memory: a model builds Lain networks that later tasks can inspect, reuse, compose and reconstruct from saved source.

**Latest experiment, November 21.** A Flash planner commissioned two implementation workers through Lain. The live network reused their pricing functions to check new invoices. The planner covered one of the two advertised discount rules. See the [experiment and memory design](docs/2026-11-21-planner-delegation.md) for the controlled and real results.

## Start here

| Document | Purpose |
| --- | --- |
| [Current status and evidence](docs/STATUS.md) | What is implemented, what was demonstrated, and current experiments |
| [Current design](docs/DESIGN.md) | Orchestration boundaries, environment/context construction and source entry points |
| [Plan and acceptance gates](docs/PLAN.md) | Original phases, next experiments and criteria for completion |
| [Remaining issues](docs/ISSUES.md) | Known limitations, failure modes and unresolved research questions |
| [Documentation index](docs/README.md) | Dated experiments, original proposal and historical reports |

## Runtime boundary

[soul-refined.lain](soul-refined.lain) imports the existing [model](model-refined.lain), creates a lexical child and connects completed responses to [source compilation](lib/compiler.lain). Independent [environment seeds](environments/) supply context; [experiment networks](experiments/) choose stages and bounded repairs. Host adapters provide external IO, source admission and observations. They do not supply a second agent phase controller.

Generated code remains inspectable. Saved Lain source can reconstruct selected definitions in a new process; this creates fresh runtime identities and is not resumption of the old graph or TTMS state. The kernel and deployed service are outside normal harness experiment changes.

## Verification and maintenance

Use the relevant dated report for its runner, runtime pin, model settings, controls and acceptance criteria. Focused module commands include `tools/test_module.sh session-api` and `tools/test_module.sh runtime-composition`; these are reproduction entry points, not claims that checks ran today. Real-model experiments must observe local resource admission and provider availability.

After each experiment or architecture change, update the status, plan and issues pages together with its dated report. Keep large databases, raw prompts/responses and logs local; publish compact reports and provenance only. The [documentation maintenance instructions](AGENTS.md) define the evidence fields and update triggers. A separate [small Lain scheduler](tools/scheduler/README.md) now monitors the authorized pilot and submits completion events to Codex; its receipts distinguish notification from completed review.

The [previous root README](docs/historical-root-readme-2026-11-14.md) preserves the early migration narrative and measurements without presenting them as today's status.

Current scheduler: [Lain ingress and isolated observers](tools/scheduler/reactive_scheduler/README.md), with explicit admission connections, file event logs, a single queue writer and disposable/renewable workers. See the deployment receipt before inferring current service health.

Historical revision-stamped Lain snapshots are in [process/](process/README.md).

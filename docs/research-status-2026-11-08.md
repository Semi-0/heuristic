# Heuristic: design, evidence, and current research status

This is the reading entry point for the November 8 review and the Tsuki handoff. The [November 9–10 continuation](2026-11-10-autonomous-repair-experiment.md) records subsequent real repair attempts and the new unchanged-runtime control. Historical records remain at their original paths. A passing result applies to its recorded runtime, orchestration, and input condition; it is not automatically acceptance of the latest design.

## Intended architecture

The [original proposal](lain-agent-harness-proposal.md) treats an agent invocation as a persistent computational environment. Earlier computations should remain inspectable and executable, rather than surviving only as textual memory. The [experiment intention](experiment-intention.md) separates in-session reuse from durable reopening; source-based reopening is now demonstrated below, while exact suspended-state resumption remains unproved.

The current runtime is still [soul-refined.lain](../soul-refined.lain), importing the existing [model](../model-refined.lain) and [completed-response compiler](../lib/compiler.lain). Parent environment bindings provide role, task, language, capabilities, examples, inputs, and observations. Generated source extends a lexical child. Transport forwards requests; the Lain network selects phases and bounded corrections. Host code admits external inputs, delivers effects, reads diagnostics, and runs an independent evaluator. A hidden host prompt, handwritten substitute solution, and host retry controller are outside this intended experiment.

Separate identities matter: session, worker, lexical child, question, provider response, admitted form, source revision, and delivered reply. Written source, provider thinking, and actual conversation replies are different records. Unknown inputs are valid partial computations. A context snapshot used for one phase is an observation boundary, not a replacement for the live domain network.

## Evidence map

| Condition | What was observed | Limit and primary record |
|---|---|---|
| Real model IO | A generated reply traversed the existing model worker and Lain output connection. | Transport success, not the complete conversation-construction goal. [November 4](2026-11-04-first-model-io-experiment.md). |
| Arithmetic and three-turn reuse | Actual generated code computed 126; later experiments computed −2980 → −149 → 1, referring to earlier cells. | The parent supplied conversation infrastructure. These historical conditions have their own runtime pins. [Arithmetic/reuse](2026-11-06-arithmetic-reuse-and-premises.md). |
| Inventory with premises | Some real trials passed numeric, support, correction, withdrawal, restoration, and unrelated-source isolation checks. Other trials failed source format or output ownership. | A successful condition is not reliability across fresh attempts, nor a clean-current-kernel claim. [Inventory record](2026-11-06-arithmetic-reuse-and-premises.md). |
| Explicit generalization on a small task | The model considered the problem class but declined to create a wrapper; it used primitives and partial evaluation. | Asking for generalization did not establish learned-closure reuse. A planning contract also rejected a primitive-reuse description. [Three-stage inventory](2026-11-07-three-stage-generalization-experiment.md). |
| More complex print-shop task | Fifteen real calls produced 35 correct initial numeric fields and four subsequent invocations of the retained generated quote function. | Ten missing premise checks and four mutation checks failed. This earlier orchestration condition is not proof of the current single-orchestrator design. [Print-shop](2026-11-07-three-stage-printshop-experiment.md). |
| Real repair with a host controller | A previous real-model repair condition completed numerical answers. | A second host controller selected phases/retries; retained only as historical evidence. [Orchestration audit](2026-11-07-single-lain-orchestrator-audit.md). |
| Experimental kernel variant | One controlled run reported 84/84 passing checks. | Its kernel changes were reverted at the user's instruction. It is not current acceptance and its patch must not be installed by a runner. [Historical variant](2026-11-08-runtime-and-dependency-recovery-fixes.md). |
| Current public-session contracts | Three extension-only tests pass 36 assertions; runtime composition passes two tests / 15 assertions. Eleven closure/selector probes pass. | Controlled compiler/consumer evidence, not a real-model construction result. [API audit](2026-11-08-session-api-audit.md). |
| Earlier single-Lain recovery control | With the completed-context boundary, it reaches analysis, generalization, and initial implementation. Compilation rejects the deliberately invalid suffix and retains the valid prefix. | No repair request was emitted in that condition; round one timed out. [Historical cross-check](2026-11-08-unchanged-kernel-cross-check.md). |
| Timestamped repair guidance and partial gates | The controlled worker receives analysis, generalization, implementation and two repairs. Accepted definitions survive; the final repair computes 11 and completes without replaying requests. | Controlled transport evidence, not a fresh real-model success or the full five-question print-shop experiment. [Repair guidance](2026-11-08-timestamped-repair-guidance.md). |
| Full single-Lain recovery reference | Five questions, four retained-callable reuses and all 84 evaluator checks pass on the unchanged runtime, including correction, withdrawal/restoration and isolation. | Controlled transport, zero provider calls. Distinct from the older patched-kernel condition. [November 9–10 continuation](2026-11-10-autonomous-repair-experiment.md). |
| Real five-question continuation | One real session makes 15 calls, delivers all five correct quotes, reuses the retained pricing callable four times, and passes all 84 numerical/dependency/mutation checks plus nine history/stability and eight integrity checks. | Uses the unchanged runtime, compact parent diagnostics, string/keyword status labels, documented branch limits and 8,192/4,096 generation limits. Earlier real repair failures remain recorded; general planning repair and admitted-contradiction repair are unfinished. [Final campaign record](2026-11-10-autonomous-repair-experiment.md). |

The [November 10 catalog experiment](2026-11-10-catalog-discovery-experiment.md)
adds real recursive Datalog discovery and retained callable composition through a
local Datomic wrapper. The matched flat-catalog baseline is numerically correct
but reuses one fewer application; this small comparison is not a general
performance claim. That initial trial tested metadata persistence; source-based executable reopening
is covered by the later restoration experiment below. The [cumulative closure continuation](2026-11-10-cumulative-catalog-closures.md)
then passes four real model calls: database examples are executed, a composed
closure is registered and rediscovered, and two independent orders update without
further model calls.

The [durable source-restoration experiment](2026-11-10-durable-source-restoration.md)
now demonstrates rebuilding saved definitions and captured callable dependencies
in a separate process, followed by real-model discovery/use and reactive updates.
All 14 checks pass. This advances source-based executable persistence; exact
suspended graph/cell-state resumption remains unproved.

## Corrections to earlier interpretations

The four old experimental closure assertions are not four established failures of Lain consumers. Three inspect raw returned-list storage. Through compiled Lain `car`/`cdr` accessors, premise withdrawal makes flat and nested results unknown, and restoration gives values with the current premise support. The fourth expects rejection of a list shape change; the high-level path produces the changed list and updates its consumers successfully. That expectation needs contract review. The old tests were explicitly invoked as references and were not model questions or generated code.

A separate arithmetic limitation does reproduce through the public compiler and extension-only producers. After changing subtotal 185 → 257, `(* (+ subtotal (select (>= subtotal 250) 0 fee)) 0.08M)` becomes contradictory. Equivalent `if` formulations pass initialization, correction, withdrawal, and restoration. A universal selector failure or a need to patch the kernel does not follow.

Immediate context projection works; delayed context projection can become contradictory. Freezing the completed phase context before reading its fields fixes the small reproduction and improves the full control's progress. That earlier condition still stalled after compilation. The subsequent [repair-guidance control](2026-11-08-timestamped-repair-guidance.md) reuses partial Boolean operators and completed observation checks; it sends both repairs and completes the controlled round. A fresh real-model print-shop result remains unverified.

## Current implementation and reproduction

- [Lain phase policy](../experiments/phase-orchestration.lain), [question connections](../experiments/printshop-three-stage-requests.lain), and [independent seed](../environments/printshop-three-stage.lain).
- [Source admission](../src/lain_harness/soul_session.clj) retains response/form identities and accepted-prefix receipts. Failed/truncated responses are rejected before admission; admitted prefixes survive a later invalid form.
- [Public producer extension](../src/lain_harness/session_publication.clj) and [compiled consumer tests](../test/lain_harness/session_api_test.clj). Extensions add source and propagators, without pre-seeded domain cells. External facts/callbacks also have documented public input APIs; the strict diagnostic reproductions use producer extensions only.
- [Generic observations](../src/lain_harness/experiment_observation.clj) expose edge values, required outputs, supports, missing supports, and immediate input/output connections. They do not select retries or add solved networks.

From the harness root, using the companion runtime checkout:

```sh
LAIN_OFFLINE_UUID=1 tools/test_module.sh session-api
LAIN_OFFLINE_UUID=1 tools/test_module.sh runtime-composition
LAIN_OFFLINE_UUID=1 tools/test_module.sh behavior
LAIN_OFFLINE_UUID=1 tools/test_module.sh operator-examples
LAIN_OFFLINE_UUID=1 tools/test_module.sh model-io
```

Model-IO's HTTP test needs loopback server access. The latest recorded focused total before adding the session-API module was 28 tests / 460 passing assertions. Recovery and legacy suites have separately recorded failures/timeouts; do not report the whole harness as green. The latest backend style commit `90f74fc` changes only whitespace relative to the checked `a6edf22` revision. The companion test runtime remains at `2773dff`; its executable source matches the latter revision except documentation and the new whitespace-only follow-up.

The machine's deployment pipeline gates the backend/editor pair. It is separate from the heuristic research harness and does not establish the harness's real-model acceptance. After the formatting correction and three test/documentation follow-ups, the full pipeline passed and activated backend `3c3f643` / editor `602eb67`. Production runtime checks report 9,867 passing assertions with zero failures/errors. The [publication receipt](publication-2026-11-08.md) records activation and GitHub's status-limit caveat for the unchanged editor SHA.

## Evidence preservation

Readable contexts, original generated source/thinking, receipts, outputs, evaluators, and failed trials remain under `runtime/`. Large new artifacts have individual deterministic gzip copies listed in [the archive manifest](../runtime/evidence-archive-20261108.json); both compressed and original SHA-256 hashes are recorded. Local originals are retained and ignored, not deleted.

```sh
python3 tools/archive_experiment_evidence.py verify
python3 tools/archive_experiment_evidence.py restore
```

The user chose local-only bulk evidence after automatic approval review blocked the broad archive publication. Source, documentation, the hash manifest, and selected small evidence records are published; the complete archive remains local and is supplied in the Docker reference bundle. Copy those archives from the bundle before running `restore` in a fresh checkout. Archived kernel patches and old host controllers are historical evidence only. Use the status notices in their folders.

## Questions worth carrying into Tsuki

1. Compare usable accessor results, support/state frontiers, and retained raw evidence separately. Storage surviving withdrawal is not by itself a live-result error.
2. Treat successful admission, computational readiness, domain correctness, and premise completeness as separate observations. A network can be syntactically valid but unfinished or wrongly justified.
3. Investigate the receipt-to-feedback-to-repair connection through existing edge values and connections. A controlled failure must be recoverable before attributing a later failure to model generation.
4. Compare `select`/`if` when dependencies shrink across a branch, using the same high-level source and revision stream. Do not import the reverted kernel fix as a conclusion.
5. Evaluate abstraction by actual retained callable identity and later invocations, not by an analysis record saying “reuse.” Complexity can make abstraction useful; prompting alone did not force it on a trivial problem.
6. Keep Tsuki contribution tags, alternative justifications, versioned premises, writer supersession, ledger authority, and projected support as explicitly different contracts until a shared probe establishes equivalence.

The [Tsuki reference brief](handoffs/2026-11-08-heuristic-to-tsuki.md) proposes a bounded reading/review task for the existing Docker agent. It preserves that agent's current runtime, kernel, ledger, and test-freeze boundaries. No review date, autonomous reminder, or new deployment authority is implied.

The [returned Tsuki review](handoffs/2026-11-08-tsuki-review.md) adds an important distinction for the next joint review: our extension-only producer uses `collection/content` support-only content, while the public `source-publication/active`/`retracted` path can carry a state-aware frontier. Entering through the public compiler API does not make those payload contracts identical. Its proposed cross-engine probe separates these configurations, raw evidence, usable projections, and consumer outputs; it was written but not executed. Interpret the arithmetic comparison above as evidence for its recorded producer contract, not a universal statement about all TTMS publication paths.

# Cumulative-history benchmark pilot

Documentation checkpoint: 2026-11-14. This is a compact repository report of local experiment evidence, not an accepted benchmark result. Source harness snapshot: `00c1ead`; runtime export: `3c3f643`. [Current status](STATUS.md).

## Question and setup

Can a model use a previously constructed computation on a related new question, rather than rebuilding it? Three unchanged synthetic benchmark questions: q002 (90901 total mass, 87290 packaging), q003 (90 total, 1 packaging), q005 (packaging 2, total absent). Independent expected answers: 3611, 89, UNKNOWN. Expectations are evaluator facts, not supplied answers.

Each question includes the same 48 example cards and two explicit rules. Both arms use one existing model worker with Lain-controlled sequential stages. Retained inherits earlier compiled children and includes previous generated source in memory; fresh uses independent children with no earlier source. Fresh is lexical isolation, not one process per question. Generic methodology invites reuse without prescribing a particular operator. The benchmark does not test autonomous construction of its own history mechanism.

Settings: temperature 0, thinking enabled, medium effort, requested thinking budget 4096, maximum output 8192, HTTP timeout 600 seconds. These differ from D-007 baseline settings/context, so this is a memory ablation, not a matched benchmark comparison. Numerical correctness and actual retained callable application are separate acceptance criteria.

## Observed sequence

| Attempt | Observed outcome | Interpretation |
| --- | --- | --- |
| Original retained/fresh | Requests recorded, no completed responses; failed-response admission rejection | No generated source to assess |
| Exact-envelope HTTP probe | HTTP 400 in about 0.0203 s: `No user query found in messages.` | System-only envelope rejected by installed template before generation; attribution to original calls inferred because their raw HTTP was not saved |
| Queued unchanged rerun | Explicitly cancelled before either arm ran | Artifacts preserved; not an additional model trial |
| Corrected controlled preflight | Three fixture responses admitted; exact task user-message assertions passed | Wiring evidence only, no provider generation |
| `corrected-1` | Retained entered per-call idle wait, no recorded provider HTTP call, generic rejection; fresh never started; watchdog later stopped job at 91.2 C | Likely idle-wait expiration before retained rejection; exact exception was not retained. Thermal stop is measured in watchdog event/receipt. Neither is a demonstrated generated-network failure |
| `corrected-2` | Terminal: retained 3/3 correct, three HTTP 200 completed responses admitted; fresh no HTTP/no response and pre-source rejection | History reached later requests, but zero reusable callables; fresh comparison incomplete. Likely idle timeout, exact exception not captured |

Correction is confined to the copied experiment seeds: system content comes from the environment snapshot; a user message comes from the current-task cell, following `experiments/catalog-common.lain/stage-in`. Task text is currently repeated in system and user messages. No heuristic implementation/kernel changes. An external loopback recorder preserves request/response bodies and upstream HTTP statuses. Both arms retain the same settings. Idle sampling is not a reservation against unrelated provider clients.

## Evidence locations (local, not repository dependencies)

- Working report: `/home/lain/reports/heuristic-cumulative-20261114/RESULTS.md`.
- Exact HTTP failure: `diagnosis/probe-request.json`, `diagnosis/probe-response.body`, `diagnosis/probe-http.json` beneath that directory.
- Corrected seed diff and hashes: `corrected-2/seed-only.diff`, `corrected-2/manifest.json`.
- Current environment seeds: `corrected-2/harness/retained.lain`, `corrected-2/harness/fresh.lain`.
- Captured controlled first prompt: `corrected-1/control/retained/q1-context.edn`.
- New raw calls, if made: `corrected-2/http/<arm>/<call>/request.body`, `response.body`, `status.json`.
- Final acceptance, if produced: `corrected-2/acceptance.edn`, `integrity.json`, `http-summary.json`.
- Watchdog renewed-run unit: `lain-test-335ac5a51414478aba5227278cf42606.service`.

The runner appends to the local RESULTS.md on completion, not to this repository automatically. On reviewing its completion/interruption, update this report and STATUS/PLAN/ISSUES. Do not silently retry a resource-stopped run or replace missing results with controlled fixtures. Large prompts, databases and logs remain local; no commit or push is performed by this documentation work.

Latest observed queue checkpoint: `2026-11-14T22:18:57.130121+00:00`; arm `retained`, 2 running provider requests and 0 waiting. This is a timestamped observation, not a promise that the queue remains active.

## Terminal review — 2026-11-15

The scheduler terminal event was received and its job receipt reviewed: finished,
exit 0, not interrupted. This is the runner's status, not both arms passing: the
runner records child exit codes and still completes its analysis on arm failure.
Retained exited 0; fresh exited 1. No restart was performed.

**Measured/read artifacts:** retained made three HTTP calls, all 200 with `stop`,
all containing system and user roles. All three responses were admitted and delivered
`ANSWER: 3611`, `ANSWER: 89`, `ANSWER: UNKNOWN`. Prior q1 source appears in HTTP
request 2, and q1/q2 source in request 3, confirming history reached the provider.
However, q1 and q2 each define a separate subtraction network using new cells;
there are zero declared reusable callables and no retained-callable application
evidence. Q3 emits the literal UNKNOWN. Correct outputs do not establish reusable
abstraction or cumulative benefit.

**Fresh arm:** no HTTP capture, no completed response, provider-ms 0, and admission
rejected the failed/incomplete response before generated source existed. The final
idle sample shows one running provider request and no waiting requests. The arm
ended about 16 minutes after retained. **Inference:** the wrapper's 900-second
per-call idle deadline likely expired. Its `wait-idle!` runs outside the diagnostic
try block, so the exact exception was not saved; do not label this a proven transport
or generated-code failure. Metrics sampling does not reserve the shared provider.

The planned comparison remains incomplete. Recorded-call settings are identical;
there is no fresh HTTP request with which to establish an observed cross-arm
comparison. HTTP token usage is available in http-summary.json (despite the earlier
auto-generated report's statement about the unchanged transport lacking it).
No benchmark expansion, kernel change, commit or push was performed.

## Fresh-only pilot follow-up — 2026-11-15T06:24:49.404387+00:00

The user authorized one fresh-arm rerun using the same corrected seed and settings.
It was registered as `lain-test-b0893239ea334e73b58ff95b0552ca6c.service`; its initial
idle-wait checkpoint is superseded by the completed review below.
Per-call idle timeout is six hours instead of fifteen minutes; a separate copied
probe driver allows nineteen hours for the arm, preserving the ten-minute HTTP
timeout and all generation settings. The wrapper now records full exceptions from
both idle wait and transport. The harness/seeds remain byte-identical to corrected-2.

Retained will not be rerun: its historical 3/3 correct result is the comparison arm.
It produced no reusable callable; q1/q2 constructed separate networks, so cumulative
reuse is not yet shown. Evidence and automatic side-by-side report:
`/home/lain/reports/heuristic-cumulative-20261114/fresh-rerun-20261115/`.
The deployed Lain scheduler watches the terminal receipt. No automatic retry,
watchdog reset, kernel/heuristic implementation change, commit or push.

## Fresh-only pilot terminal review — November 15

The single authorized fresh rerun completed: three HTTP 200 responses with `stop`,
three admitted results, answers 3611 / 89 / UNKNOWN, 3/3 correct, zero reusable
callables, clean owner closure. Historical corrected-2 retained also had 3/3 correct
and zero reusable callables; it was not rerun. Retained q1/q2 built separate networks.
Thus correctness is demonstrated for both arms, but cumulative callable reuse and
a retained-history benefit are **not** demonstrated. The earlier fresh failure
remains a separate no-call attempt.

Seed/harness hashes and recorded generation settings were independently verified
unchanged. Six-hour idle limits and full exception capture were installed; no
exception occurred. Job receipt: exit 0, not interrupted, 12 min 19.995 s wall,
56.279 s client CPU, 432.5 MiB peak client memory. These exclude server resources;
noncontemporaneous arm timings are not a performance comparison.

Side-by-side report:
`/home/lain/reports/heuristic-cumulative-20261114/fresh-rerun-20261115/RESULTS.md`.
The scheduler delivered the terminal event and receipts were reviewed. No additional
attempt, restart, kernel change, commit or push. Next scientific question is whether
a task/context elicits a reusable abstraction; no new experiment is authorized here.

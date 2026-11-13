# Lain-owned repair of admitted but missing outputs

This experiment addresses the parent-output shadowing failures from
[agent-directed durable accumulation](2026-11-11-agent-directed-durable-accumulation.md).
A response can compile successfully and compute a local result while leaving the
required parent ports unknown. The prior experiment needed revised instructions
and a new trial; here Lain observes that condition and requests bounded repairs in
the same lexical child.

## Scope and architecture

No kernel, existing runtime, model implementation, source-admission adapter or
native observation implementation is changed. The new experiment uses the
existing `runtime` in `soul-refined.lain`, a single model worker, public session
extensions, `incremental-compile`, and `inspect:outputs-once`.

`lib/output-repair.lain` supplies reusable two-output attempt and repair networks.
After a completed admission receipt, it snapshots the required **parent cell
references**, independently of any names shadowed in the generated child. A stage
is ready only if admission succeeded and both observed outputs are neither
unknown nor contradictory. Discovery checks its rows port twice; selection checks
identity and notes; execution checks answer and reply.

This is a missing-output readiness contract. It does not establish output types,
domain correctness, premise completeness, or validity of natural-language notes.
Independent host checks still verify the selected callable, actual compiled
applications and reactive numerical results.

The same Lain network constructs a `:repair-required` diagnostic containing the
failed source, timestamped admission receipt, accepted forms, child binding
headlines, immediate output values/input/output connections, and remaining budget.
An environment binding named `system-message` exposes it to the model. No separate
host instruction is appended to the provider request. The observation comes after
admission; no host wall-clock timeout is used to decide that a repair is needed.

The repair response is admitted directly into the original phase's child. There
are at most two correction requests per phase. A successful attempt stops repair;
two unsuccessful corrections produce a terminal `:failed` outcome. Later phases
wait for the preceding phase's successful output contract, not admission alone.

`environments/catalog-output-repair.lain` provides stable parent-port aliases:
`parent-discovery-results`, `parent-selected-id`, `parent-selection-notes`,
`parent-answer`, and `parent-user-output`. These reference existing parent cells;
they do not create an alternate result store. For an admitted local `answer`, the
missing connection can be added as `(-> answer parent-answer)`. The model is told
to retain definitions and add only missing connections.

`experiments/catalog-output-repair.lain` composes this policy with the previous
catalog discovery, source restoration and execution. The host probe supplies
external inputs, records observations, and evaluates outcomes. It does not select
phases or retries. Controlled transport dispatch supplies responses to requests
that Lain has already issued; it does not schedule requests.

## Cases

Four small source fixtures are verbatim real-model responses retained from the
previous experiment. Their origins are listed in
`test/fixtures/output-repair/README.md`; byte equality with the original local
responses was verified. The failing execution resolves and invokes the saved
closure but defines local `answer` and `user-output` instead of connecting parent
ports. No result or correction is inserted into that original response.

| Mode | Base responses | Repair responses | Purpose |
|---|---|---|---|
| `control` | Recorded source, shadowed execution | Two missing connections | Verify detection, same-child admission, and progress |
| `clean` | Recorded source, correctly connected execution | None expected | Verify no false repair |
| `exhausted` | Recorded source, shadowed execution | Definitions that supply no outputs | Verify two-repair bound and terminal failure |
| `replay` | Recorded source, shadowed execution | Real provider | Test model repair of an observed real failure |
| `fresh` | Real provider | Real provider if needed | Check integration without replayed base responses |

The source database is copied from the closed successful accumulation producer.
Each process starts with metadata and no live saved callables. Lain restores the
selected three-module source dependency bundle using the existing admission path.
The expected answer is 859; changing delivery gives 879, then changing quantity
gives 1005. No model request should be needed for these input changes.

## Results

The three controls passed: one repair for the shadowed case, zero for the clean
case, and exactly two followed by terminal failure for exhaustion. All control
owners closed; none called the provider. `control-1` and `clean-1` passed all 21
checks in their recorded probe; `exhausted-1` passed six failure-path checks.

The real repair trial `real-repair-1` passed all 23 acceptance checks. Its three
base responses were unchanged recorded source; only `execute-repair-1` called the
real provider. No context or response was edited during this session. The real
provider returned:

```lain
(-> answer parent-answer)
(-> user-output parent-user-output)
```

Before repair, the observation showed `{:first :unknown :second :unknown}` for
the required parent ports. After repair it showed
`{:first 859.0 :second "Total: 859.0"}`. The two observations have the same child
identity, and all phase observations have one worker identity. The admitted
closure invocation remains installed: inspection finds one delivered-event
application, one event application, two retained linear applications, and one
retained surcharge application. The repair adds connections without regenerating
or reinvoking the computation.

Delivery and quantity changes produce 879 and 1005. Replaying `start` does not
cause another request or source admission. Provider calls remain one; total Lain
requests are four (three replayed base responses and one real repair). The owner
closed successfully. The saved bundle hash still matches the producer.

This establishes real-model repair of this observed missing-output failure with
documented parent aliases and concrete connection examples. It is not a claim of
unaided diagnosis, repair of arbitrary contradictions, or a fully fresh base run.

### Fully fresh integration

`fresh-1` also passed all 23 acceptance flags. All three responses—discovery,
selection and execution—came from the real provider. It selected
`:delivered-event-cost/v1`, restored the unchanged saved bundle, and connected
outputs correctly on the first attempt. There were three provider calls and zero
repairs. Input changes again produced 859 → 879 → 1005 without further inference
or readmission. The owner closed. This fresh run establishes clean integration;
it does not itself exercise repair. Some repair-specific flags are conditional
and therefore not evidence of a repair occurring in that run.

| Trial | Provider calls | Repair requests | Terminal result |
|---|---:|---:|---|
| `control-1` | 0 | 1 controlled | Done; all 21 recorded checks pass |
| `clean-1` | 0 | 0 | Done; all 21 recorded checks pass |
| `exhausted-1` | 0 | 2 controlled | Failed as intended; all 6 checks pass |
| `real-repair-1` | 1 | 1 real | Done; all 23 checks pass |
| `fresh-1` | 3 | 0 | Done; all 23 checks pass |

The controls used the initial probe snapshot. Before the real trials, two
additional evaluator checks were added for unchanged request/admission counts
after input and start replay. Lain policy and environment instructions were
unchanged between the controlled and real trials. No post-failure prompt changes
or host-triggered repair were needed in this experiment.

Formatting/lint reports zero errors and warnings for the new Clojure probe.
Shell syntax and diff whitespace checks pass. The `.lain` files are verified
through the actual high-level compiler and session-extension path in all five
runs, not a low-level hand-built network. They remain excluded from the Clojure
formatter. Existing runtime/kernel tracked files have no changes; the companion
runtime remains at `2773dffc1cf5a2b129559aa445d66d09f96d4dd7`.

## Remaining limits

The repair examples and explicit parent aliases are supplied guidance. The live
repair demonstrates adding missing connections; it does not prove repair of an
incorrect known answer, polluted parent outputs, arbitrary contradictions,
malformed-response transport failures, missing premises, or resource effects.
Two-output readiness is intentionally smaller than a general contract/type
system. Broader discovery/selection repair paths are wired but their failure
cases were not separately injected in this campaign.

Next in the research plan is dependency-aware reuse: validate retained
assumptions under a changed policy and test selective recomputation through the
existing premise machinery. This experiment does not alter that machinery or
claim that milestone complete.

## Reproduction

```bash
LAIN_REPAIR_MODE=replay \
LAIN_ACCUMULATED_TRIAL=/absolute/path/closed-successful-accumulation \
LAIN_EVIDENCE_DIR=/absolute/path/fresh-repair-trial \
bash tools/run_catalog_output_repair.sh
```

Change `LAIN_REPAIR_MODE` to one of the modes above. Every evidence directory must
be fresh. Raw databases, contexts, responses, admission receipts, outcomes and
application observations remain under ignored `runtime/output-repair-20261111/`.
The shell owns process lifecycle; the `.lain` files own inference and repair
scheduling. The deployed service is not restarted.

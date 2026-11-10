# Local catalog discovery and cumulative Lain reuse

This experiment extends the original harness proposal's discovery phase. A local
Datomic catalog stores operator descriptions, interfaces, assumptions, tags and
capability relations. Lain remains the orchestrator: the existing model emits
complete Lain source, and the unchanged runtime admits it into lexical children.

## Structure

- `src/lain_harness/catalog.clj`: session extension exposing `catalog:register`,
  `catalog:query`, and `catalog:resolve`. Registration is idempotent; conflicting
  identities are rejected. Queries accept EDN fact clauses and named recursive
  rules, with relation-shaped results. No arbitrary host functions are allowed.
- `environments/catalog-discovery.lain`: environment documentation, ports and
  capabilities. `experiments/catalog-common.lain` owns generic stage connections. Instructions, concrete examples, recursive-rule methodology and
  exact database schema are environment bindings. One model worker serves
  separate library, discovery and solution children.
- `experiments/catalog-discovery.lain`: build, search, solve through Lain gates.
- `experiments/catalog-baseline.lain`: the same library followed by a solution
  request receiving all registered metadata directly, without a required query.
- `tools/probe_catalog_discovery.clj`: public session extension/input APIs,
  provider IO, independent assertions and evidence recording. It does not choose
  model phases, construct the solution, or select operators.

The library task requests quantity/rate multiplication, fractional surcharge,
legacy multiplication capturing rate 2, and numeric maximum. The model chooses
bindings and writes all implementations and registrations. Public catalog ids
are specified to make the experiment auditable. Status and assumptions are
model claims; registration is not correctness verification.

The new task combines 120 pieces at 3 units each and 8 hours at 15 units/hour,
then applies an 8% surcharge. The discovery stage receives no earlier generated
source or operator binding names. A conceptual dependency graph contains
`event-cost -> batch-cost -> linear` and `event-cost -> percentage`. The agent
must write the recursive query, inspect candidate metadata including the
obsolete candidate, and use live references to apply selected computations.
The evaluator expects 518.4, then 583.2 after quantity changes to 140, with no
additional model request. It compares retained callable declarations/captured
environments against actual compiled applications: two linear applications,
one surcharge application, and no legacy/maximum application.

## Scope and persistence

Datomic persists metadata, relations, and archived source/admission evidence.
Live cell references are session-local. Reopening the catalog is tested;
reopening executable closures is not implemented or claimed. The database is
not a second orchestrator. Queries observe a database basis when invoked; there
is no automatic subscription to subsequent database transactions.

The current metadata schema is deliberately small. The taxonomy and library
objectives are supplied by the experiment; autonomous taxonomy invention and
large-library retrieval are not tested. A single small comparison cannot
establish improved cumulative efficiency.

The source/admission archive records evidence; it does not prove the claimed
interface or restore a network. Source identities and immediate application
observations also do not establish full causal provenance.

## Early findings

The first real discovery attempt exhausted its response budget and was rejected
before source admission. Its recorded reasoning repeatedly questioned whether
capability edges stored keywords or entity references. The initial documentation
had recursion examples but omitted exact schema types. The revised context
explicitly states Datomic entity-reference semantics and the supported query
subset. A separate prompt correction escapes strings in the registration example
and removes future task text from the shared binding directory.

The initial unused maximum operator called an unavailable `max` binding. This
reinforces why catalog membership and a model-written `:current` status must not
be treated as verification. The revised language context documents core `if`
and `>=` for that operation.

## Reproduction and evidence

Run from the harness repository, choosing a fresh absolute evidence directory:

```bash
LAIN_DISCOVERY_THINKING=false LAIN_EVIDENCE_DIR=/absolute/path/live bash tools/run_catalog_discovery.sh
```

For the flat-catalog comparison, replay the exact model-written library response
through the same source admission path, then make a fresh real solve request:

```bash
LAIN_DISCOVERY_MODE=baseline LAIN_DISCOVERY_THINKING=false \
LAIN_LIBRARY_RESPONSE=/absolute/path/live/build-response.edn \
LAIN_EVIDENCE_DIR=/absolute/path/baseline bash tools/run_catalog_discovery.sh
```

Replayed library construction is recorded separately from real provider calls;
it is not counted as another real construction success. Bulk evidence and local
database files stay in ignored `runtime/discovery-20261110/`. The runner snapshots
source into an isolated directory. It does not restart deployed services or edit
the kernel, `soul-refined.lain`, `model-refined.lain`, or `lib/compiler.lain`.

## Observed results

The completed comparison used the identical library written by the real model
in `live-7-schema`. Both arms replayed that exact complete build response through
normal source admission into fresh sessions. All discovery and solution responses
were fresh real model completions. This controls library differences; it is not
an executable-network restart/resume claim.

| Observation | Recursive discovery (`live-8-join`) | Flat catalog (`baseline-1`) |
|---|---:|---:|
| Fresh provider calls after loading the library | 2 | 1 |
| Datalog queries | 1 | 0 |
| Candidate metadata supplied to solve | 3 operators | 4 operators |
| Retained linear applications | 2 | 1 |
| Retained percentage applications | 1 | 1 |
| Legacy/maximum applications | 0 / 0 | 0 / 0 |
| Initial total | 518.4000000000001 | 518.4000000000001 |
| Total after quantity 120 → 140 | 583.2 | 583.2 |
| Additional model calls for changed input | 0 | 0 |
| Stable session | yes | yes |
| Strict full-reuse acceptance | pass | fails linear-application count |

The discovery model wrote both recursive rules and the operator-capability join.
Its query included the obsolete fixed-rate candidate and excluded unrelated
maximum. The solver selected the two current compatible interfaces and explained
why the obsolete fixed-rate interface did not fit. Runtime application records
match the original callable declarations and captured environments. Its solution
contains `(linear-op quantity unit-price)`, `(linear-op hours hourly-rate)`,
and `(percentage-op combined-cost surcharge-rate)`.

The baseline correctly selected the same two operators but computed labor with
`(* hours hourly-rate)`. That is correct arithmetic and partial reuse; the strict
full-reuse assertion intentionally reports it separately. More retained calls
are not inherently more efficient than a primitive multiplication. This single
comparison supports feasibility and a small difference in reuse coverage, not a
claim that mandatory discovery improves overall performance. It cost one extra
model call. Solve context sizes were 12,807 versus 13,567 characters; the discovery
arm additionally sent a 12,497-character search context, so total prompt volume
was larger, not smaller, for this four-operator catalog.

The numerical oracle uses absolute tolerance 0.00001. The raw text answer exposes
floating-point precision (`518.4000000000001`); exact decimal money formatting was
not part of this experiment.

### Failure sequence and interpretation

- `live-1`/`live-2`: dependency/classpath setup failures, no model completions.
- `live-3`: database seed lookup reference failure, no model completion.
- `live-4`: sandbox transport failure, no provider response admitted.
- `live-5`: two real completions. Library registered; search response hit the
  token limit while repeatedly questioning storage shapes. Source rejected.
- `live-6`: one real completion. Library response hit the token limit while
  repeatedly reconsidering syntax. Source rejected.
- `live-7-schema`: three real completions with thinking disabled and exact schema
  supplied. Library construction succeeded. Search omitted the capability join
  and returned an empty set. The solver then constructed new operators instead
  of reusing the earlier ones. This does not satisfy discovery/reuse acceptance.
  Separately, evidence archival failed because an individual Datomic value
  exceeded 4096 bytes. The model sources and receipts remain in local files.
- `live-8-join`: two fresh real completions plus exact earlier library replay.
  Added a generic example joining recursive concept reachability to operators.
  Full discovery/reuse acceptance passes.
- `baseline-1`: one fresh real completion plus the same library replay. Numerical
  and reactive behavior pass; full-reuse metric fails as described above.

Nine real provider completions were recorded across these trials. The provider
was the existing localhost endpoint, using the existing model transport. Earlier
trials enabled thinking with an 8192-token maximum and 4096 thinking budget; the
successful comparison disabled thinking, keeping the same model and maximum.
Schema, example, and inference-setting changes were not isolated factorially:
we cannot attribute the improvement to one of them alone.

The archive fix stores numbered, bounded Unicode-safe chunks, retaining exact
source and admission evidence without exceeding the per-value limit. A final
zero-provider replay checks the current wrapper and archive implementation
against the saved successful model sources. This supplements, and does not
replace, the real-model result.

## Assessment

This is evidence for a useful next architectural component: a queryable catalog
can return live references that the model actually composes into a new network.
The exact schema and an example composing *different relations* mattered more
than merely telling the model to perform transitive reasoning.

Next, test a larger library with unseen task variants and less prescribed
registration objectives, repeat both arms, and measure correctness, unsuitable
reuse, new code, model cost and retrieval cost. Durable restoration of executable
closures remains a separate uncompleted milestone from the original plan.

## Final local verification

- Three focused tests, 17 assertions, zero failures/errors: public compiler
  registration/query/application, replay/conflict behavior, recursive cycles,
  persisted metadata and identity checks, query restrictions, and exact large
  Unicode source/receipt archival.
- Three changed Clojure files pass formatting and lint with zero warnings/errors.
- Successful saved-source replay passes numerical, reply-update, identity,
  candidate and retained-application checks with zero provider calls.
- Main runtime, existing model and completed-response compiler hashes unchanged;
  companion runtime tracked files unchanged. No deployment or CI/CD rerun.

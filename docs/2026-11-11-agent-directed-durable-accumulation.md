# Agent-directed durable accumulation

This experiment follows the November 10 source-restoration milestone. It changes
who chooses the restoration root: the real model first queries persisted metadata
with no saved callable live, then selects an identity. Lain admits the selected
source and its captured dependencies before asking the model to execute.

## Question and acceptance

The first process asks for a reusable delivered-event quote. Printed pieces and
labor form an event cost with a fractional surcharge; a fixed delivery charge is
added **after** the surcharge. The model must discover a suitable existing event
callable, explain its choice, and compose a new six-argument closure around it.
Its name and public identity are not prescribed. The catalog includes an explicitly
superseded fixed-price candidate with an incompatible interface.

The second process starts with the same database and an empty live-reference
registry. It receives a new delivered-event question, queries metadata again,
selects the newly accumulated closure, restores its saved source, and invokes it.
No previous generated source is included in the model context. The source loader
reads the database; it never asks the model to regenerate retained definitions.

Numerical checks distinguish the placement of the surcharge from a formula that
also surcharges delivery:

| Process | Inputs | Expected result |
|---|---|---|
| Accumulate | 75 pieces at 6; 4 hours at 30; 5%; delivery 25 | 623.5 |
| Accumulate update | delivery becomes 40 | 638.5 |
| Accumulate update | quantity becomes 100 | 796.0 |
| Reuse | 100 pieces at 6; 6 hours at 30; 5%; delivery 40 | 859.0 |
| Reuse update | delivery becomes 60 | 879.0 |
| Reuse update | quantity becomes 120 | 1005.0 |

Updates occur without new model requests. Inspection compares callable declaration
and captured-environment identities, checking that the delivered closure invokes
the retained event callable, which invokes the retained linear callable twice and
the retained surcharge callable once. Source bytes and bundle hashes are compared
across restoration. Process identity and prior-process exit are checked separately.

## Architecture

- `environments/catalog-directed.lain` supplies role, operator documentation,
  examples, tasks and parent output ports. No extra host system instructions are
  appended to provider requests.
- `experiments/catalog-directed.lain` owns discovery, selection, sequential source
  admission and execution. It uses the existing runtime and one model instance.
- `src/lain_harness/catalog_selection.clj` reads source for the selected identity
  using the previously tested dependency collector. It does not compile source,
  create live references, call a model or choose an agent stage.
- `tools/probe_catalog_directed.clj` supplies input facts, records evidence and
  admissions, persists the successful new source artifact, and checks results.
- `tools/run_catalog_directed.sh` copies the closed input database and runs two
  sequential isolated JVMs. Original evidence and deployed services are preserved.

Restoration is deliberately bounded to three ordered archive modules: the original
library, the original composition, and the newly accumulated composition. An
unselected module has empty source and an ordinary zero-form admission receipt.
This is not yet an arbitrary-depth module loader. The previous collector's source
subset restrictions remain: captured named definitions and literal catalog
references, with matching successful form admissions. Metadata and examples are
claims rather than proof. The obsolete candidate is a metadata fixture, not an
additional persisted executable.

The main `soul-refined.lain`, model implementation, compiler adapter and kernel
remain unchanged. Source restoration reconstructs definitions with fresh runtime
identities; it does not resume suspended cell or TTMS state. Archiving source is
host evidence recording; model-stage and admission ordering remain in Lain.

## Reproduction

```bash
LAIN_SAVED_DATABASE=/absolute/path/cumulative-6-direct/database \
LAIN_EVIDENCE_DIR=/absolute/path/fresh-directed-trial \
bash tools/run_catalog_directed.sh
```

To rerun only reopening against a successful accumulation whose process has
exited, use a fresh evidence destination:

```bash
LAIN_ACCUMULATED_TRIAL=/absolute/path/successful-accumulation-trial \
LAIN_EVIDENCE_DIR=/absolute/path/fresh-reopening-trial \
bash tools/run_catalog_directed.sh
```

This copies the database and producer result, records the producer trial path,
and starts only the consumer JVM; it never regenerates the producer's closure.

The saved database must be closed. Bulk databases, prompts, responses, source,
receipts and application observations remain local under ignored
`runtime/directed-20261111/`. This document is the compact shareable report.

## Results

**Passed, with context revisions between attempts.** The successful producer is
`runtime/directed-20261111/live-5/accumulate-result.edn`; the successful consumer is
`runtime/directed-20261111/reuse-6/reuse-result.edn`. The consumer copied the
producer's already-saved database. Its execution instructions had a stronger
output-port example; the producer's retained code was unchanged. This is not a
claim that an unmodified prompt passed both halves on its first attempt.

| Evidence | Producer | Fresh consumer |
|---|---|---|
| PID | 2244020 | 2244864 |
| Model-selected identity | `:event-cost/v1` | `:delivered-event-cost/v1` |
| Saved callable registry before discovery/selection | Empty | Empty |
| Model calls in successful process | 3 | 3 |
| Initial answer | 623.5 | 859.0 |
| Delivery-charge update | 638.5 | 879.0 |
| Quantity update | 796.0 | 1005.0 |
| Further model calls for updates | 0 | 0 |
| Session owner closed | Yes | Yes |

Both result files have all 16 acceptance flags true; process/hash flags that only
apply to reopening are explicitly conditional in the producer. The consumer
also asserts that the producer PID no longer exists. These are observations of
this bounded experiment, not a reliability rate across independent trials.

The model wrote and registered this new computation (registration metadata and
answer wiring omitted here):

```lain
(define event-cost-fn (cell:value (catalog:resolve :event-cost/v1)))
(define delivered-event-cost
  (network [quantity unit-price hours hourly-rate surcharge-rate delivery-charge]
    (+ (event-cost-fn quantity unit-price hours hourly-rate surcharge-rate)
       delivery-charge)))
```

The consumer's compiled application identities show one delivered-event call,
one retained event call, two retained linear calls and one retained surcharge
call. Exactly four public operators were restored; obsolete and unrelated
operators stayed absent from the live registry. Thirteen selected forms were
admitted: four library definitions/registrations, six original composition
forms, and three new closure forms. Old result applications and reply connections
were not selected for replay.

The saved and rebuilt bundle hashes match:
`07e06dc938504c36f44aa87a83bd08e6234957fa848e0659d5f3926456068640`.
The new module source hash is
`7afe684f9491957daef833fb24ac30a8608194041328aca33bc09b6fd8afb514`.
The two earlier module hashes match the November 10 report. The consumer uses a
fresh session/run identity and resolves fresh cell references; only source,
metadata and admission evidence cross the process boundary.

Selection prose is not independently verified reasoning. In particular the
consumer claimed that adding delivery externally to the old event callable would
incorrectly surcharge it; that claim is false. Its selected six-argument callable
and the actual compiled numerical behavior are correct. Acceptance checks validate
those computations, not every natural-language justification.

Focused source-selection tests pass: 4 tests, 20 assertions, zero failures/errors.
Formatting and lint pass for all three new Clojure files. Shell syntax and diff
whitespace checks pass; `.lain` files are excluded from the Clojure formatter.

### Failed trials and revisions

- `live-1`: the restricted-network attempt received no provider response. The
  failed response was rejected at admission; no source was admitted. Its transport
  exception was not captured, so the exact connection failure is not established.
- `live-2`: the provider returned a query inside Markdown fences. The source
  reader rejected it. An explicit valid/invalid raw-source example was added.
- `live-3`: discovery succeeded; selection chose the correct event callable and
  rejected obsolete candidates, but generated local definitions named
  `selected-id` and `selection-notes`. Self-connections did not populate the parent
  ports. The isolated pending trial was stopped. Selection-specific correct and
  incorrect connection examples were added.
- `live-4`: accumulation passed all checks and saved
  `:delivered-event-cost/v1`. The new process then shadowed `discovery-results` and
  connected its query rows to `answer`. The isolated reopening trial was stopped.
  The generic role example that connected an arithmetic result to `answer` was
  removed, and discovery received its own explicit output-port example.

- `live-5`: accumulation passed again. Reopening discovered the newly registered
  closure, selected it, restored all three modules and invoked it, but defined
  local `answer` and `user-output` cells instead of wiring parent outputs. The
  pending reopening trial was stopped. The execution task received a concrete
  pair of `->` examples and corresponding incorrect `define` examples.

These are manual experiment-context revisions, not evidence of autonomous model
repair. No response was stripped of fences, edited, or replaced with handwritten
solution code. The shadowing failures were valid local definitions with missing
parent outputs, not compiler rejection or evidence of a kernel defect. Whether
the generic role example caused the mistaken destination is a hypothesis, not a
controlled causal result.

## Remaining milestone

The durable accumulation mechanism is demonstrated for this three-module source
subset. The clearest reliability gap is a stage whose source admits successfully
but never supplies its required parent output. A next experiment should expose
that condition through Lain-owned output contracts and the environment's repair
diagnostics, then test whether the model repairs it in the same session. Current
success depended on revised examples and fresh trials; autonomous repair of these
mistakes is not established here. General arbitrary-depth restoration and durable
suspended-state resumption remain separate work.

The subsequent [missing-output repair experiment](2026-11-11-missing-output-repair.md)
now demonstrates the bounded repair of this recorded shadowing failure in the
same child, plus a separate clean fresh run. Its scope and controls are recorded
separately; it does not retroactively change the failed trials above.

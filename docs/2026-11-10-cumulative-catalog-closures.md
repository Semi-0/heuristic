# Database examples and cumulative closure experiment

This continuation separates two questions: can the model discover a documented
network through the database and actually execute its examples; and can it then
add a composed closure that another model request discovers and applies?

The fixed library is the earlier real-model library, with four host-authored
`:catalog/example` strings. These describe multiplication, fractional surcharge,
obsolete fixed-price multiplication, and maximum. They are database data, not
running example networks. The fixture is explicit experimental input; its
admission is not counted as a fresh model construction success.

## Lain stages

`experiments/catalog-cumulative.lain` uses the existing model and runtime:

1. Admit the documented seed library through completed-source admission.
2. A real model request writes a Datalog query that retrieves descriptions,
   interfaces, assumptions, statuses and examples.
3. A real model request executes the discovered examples, constructs a composed
   closure, registers it with its own example, and applies it to the first order.
4. A real model request queries the grown database for the whole-event closure.
5. A separate lexical child resolves and invokes the newly discovered closure
   on a second order, without access to earlier generated source or local names.

The parent environment and task-specific guidance are in
`environments/catalog-cumulative.lain`. It explicitly imports the base seed
module. Main runtime, existing model, compiler, kernel and deployed services are
unchanged. The host supplies external input values and checks outcomes; Lain owns
stage transitions. Extensions add propagators/source, not pre-seeded cells.

## Acceptance

- The first query returns stored examples, and real model-generated Lain invokes
  retained operators on those examples, producing 12 and 108.
- Exactly one new registered closure appears, with a documented example.
- The later query returns its new public identity; a later source response
  resolves and invokes that identity.
- Compiled applications match the retained declaration and captured environment:
  the new closure is invoked twice. Its composed dependencies include the
  earlier linear and surcharge callables. Distractors are not invoked.
- The first quote is approximately 518.4; the second is 1100.
- Changing first-order quantity 120 → 140 changes only its total to 583.2.
- Changing second-order hours 10 → 12 changes only its total to 1144.
- Both updates occur without another model request and retain the same session.

Values are checked with absolute tolerance 0.00001. This does not promise exact
decimal money formatting or full causal provenance. Persisted database metadata
and archived source are distinct from session-local executable references;
executable close/reopen remains unfinished.

## Findings during setup

The initial new environment lacked an explicit import of the base seed module.
All nine fixture forms were admitted and four registrations completed, but the
new discovery environment was unresolved. Adding the module import fixed that
handoff without changing the runtime. The first stalled trial was stopped;
bounded diagnostic trials closed on timeout. Diagnostic boot scripts also had a
brief syntax error, corrected before further model calls.

The first real discovery response used a local `define discovery-results`,
shadowing the parent output. Its query was syntactically valid, but the next
stage could not observe the result. The context now includes correct/incorrect
examples for output ownership; the generated response was not edited.

A subsequent real trial successfully retrieved database examples, generated
calls producing their results, and registered an event-cost closure. It then
failed its second query: it passed a public keyword into a rule expecting an
internal entity id and traversed prerequisites instead of matching the whole
capability directly. The context now distinguishes direct capability lookup
from transitive prerequisite discovery. That trial's composition also rebuilt
linear multiplication with primitives, so explicit dependency-composition
requirements were added for the next trial.

## Reproduction

From the harness root, with a fresh absolute output directory:

```bash
LAIN_DISCOVERY_MODE=cumulative LAIN_DISCOVERY_THINKING=false \
LAIN_EVIDENCE_DIR=/absolute/path/cumulative \
bash tools/run_catalog_discovery.sh
```

This makes four real provider calls after one controlled seed admission, using
the existing localhost model. The numeric answer, query and composed closure are
not supplied by the host. Raw evidence remains under ignored
`runtime/discovery-20261110/`; source, tests and this report are reviewable files.

## Final real-model result

`cumulative-6-direct` completed all four real model requests and all 14 acceptance
checks. One fixture admission preceded those requests. The model chose the new
identity `:event-cost/v1`; the host did not supply that identity or its source.

The generated closure, reformatted here for readability, was:

```clojure
(define event-cost-fn
  (network [quantity unit-price hours hourly-rate surcharge-rate]
    (let [goods-cost (linear-fn quantity unit-price)
          labor-cost (linear-fn hours hourly-rate)
          subtotal (+ goods-cost labor-cost)]
      (percentage-fn subtotal surcharge-rate))))
```

`linear-fn` and `percentage-fn` were obtained by resolving the database results
and observing their live cell values. The model registered the new closure and
an example: `[120 3 8 15 0.08]` produces approximately `518.4`. That example
matches its first actual application. A later model-generated direct-capability
query returned the new record. In a separate lexical child, the final response
resolved `:event-cost/v1` and invoked it with the second order's input cells.

| Evidence | Result |
|---|---|
| Database example executions | 12 and 108 |
| New registered closures | 1 |
| Applications of retained new closure | 2 |
| Applications of original linear closure | 5: example plus two per order |
| Applications of original surcharge closure | 3: example plus one per order |
| Applications of obsolete/maximum distractors | 0 / 0 |
| Initial totals | 518.4000000000001 and 1100.0 |
| After changing first quantity | 583.2 and 1100.0 |
| After changing second hours | 583.2 and 1144.0 |
| Further provider calls for changes | 0 |

This establishes discovery followed by cumulative *in-session* closure reuse
under explicit instructions to compose and register an interface. It does not
establish spontaneous abstraction, large-library retrieval reliability, or
executable restoration after process termination. Database metadata, examples,
and archived source persist; live executable references remain session-local.

### Validation and retained failures

- Four focused compiler/database tests pass 23 assertions, including execution
  of every seeded example through public session extensions.
- The successful real trial passed 14 checks, including actual application
  identities, captured environments, database rediscovery and order isolation.
- No main runtime/model/compiler or kernel changes were made.
- The earlier output-shadowing response and invalid keyword/entity query are
  preserved unchanged. No host-authored replacement solution was admitted.
- Eight real completions were recorded across the three model-bearing trials:
  one in `cumulative-4`, three in `cumulative-5-ports`, and four in the successful
  `cumulative-6-direct`. Other attempts failed or stalled in setup before making
  a real provider call. These are development trials, not a reliability estimate.

For replay-only verification of the successful generated sources, also set
`LAIN_REPLAY_DIR` to the original `cumulative-6-direct` directory. This makes zero
provider calls and is supplementary verification, not another real-model result.

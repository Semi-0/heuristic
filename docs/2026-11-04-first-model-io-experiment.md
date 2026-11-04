# Our first working Lain model IO experiment

November 4, 2026 · Europe/Amsterdam

Today we got a model defined in Lain to receive an input, trigger asynchronous
IO, receive a real Blazux reply, expose its fields through connected slots, and
show its semantic graph in the hosted editor. We could inspect it, change it,
and test the connections independently of a live model.

This was our first successful experiment with this simple model design in the
hosted editor. It was not the completion of the larger behavior or model
orchestration project. The milestone was a working, inspectable path from a
Lain definition to external IO and back into supported cells.

The implementation is recorded in commit
[`ff72a82`](https://github.com/Semi-0/heuristic/commit/ff72a82).
The runnable example is [model-refined.lain](../model-refined.lain), and
[Model IO experiment](model-io.md) describes its implementation and limits.

## We began with behaviors and a larger model

The initial question was how to express behavior as propagator relationships.
The acceptance example was deliberately small:

```clojure
(define input-cell)
(define sums (scan input-cell +))
```

The intended meaning was more demanding than a mutable running total. Inputs
would be supported temporal occurrences; withdrawing an occurrence would
change the supported history, and recovery would restore it. Arrival order,
batching, and duplicate evidence should not change the settled meaning.

The behavior experiment used the existing reducer-cell machinery and the
Compiler-2 callable path for built-in and user-defined reducers. Reduction ran
during cell merge, while strongest reads projected the supported result. A
locally constructed runner supplied the evaluation policy. This answered the
question about using the runner constructor without changing the default merge
policy for every session.

That work produced real results: the committed acceptance log records 14 tests
and 274 assertions passing. The 100-occurrence benchmark cases validated their
outputs, kept active topology stable, and performed no reducer invocations for
identical replay. But correctness at that scale did not establish practicality.
All five 1,000-occurrence cases exceeded the two-minute limit before finishing
warm-up. A scan over 100 occurrences retained 5,050 prefix-selection references.
The 64-occurrence window bounded evidence payloads and cache entries, while
premise states and replay tombstones still accumulated. Those results kept
behavior-based model integration deferred.

The [behavior verification record](../runtime/behavior-verification.edn)
preserves both the passing results and the limitations. Its regression section
also records baseline failures; the behavior suite passing was not a claim
that every repository test passed.

We explored an FSM direction too: collapse start and step into reducers, express
the connections as propagators, and test each reducer and connection on its
own. That remains a useful design direction. It was not the implementation
shipped in the final model IO commit; the earlier behavior/FSM edits were left
separate in the workspace.

## The turning point was making the model smaller

The user proposed that the model could simply hold inputs and outputs. Lain did
not need to represent the queue itself. One effectful operator could enqueue
input for a background worker; another could publish its results into an output
cell. Other values could then be derived from the reply.

That made the next experiment concrete. We could establish the IO boundary
before solving the entire conversation state machine. The user chose core.async
for the worker and Meander for effect interpretation. The resulting model is:

```clojure
(define model
  (network []
    (let-cell [input output worker]
      (-> (io-model:worker) worker)
      (io-model:input worker input)
      (io-model:output worker output)
      (struct :input input
              :output output
              :thinking (prim-slot :thinking output)
              :actions (prim-slot :text output)))))

(define m (model))
```

`model` declares the network; `(model)` materializes an instance. Its worker is
represented inside Lain by an immutable handle. The native effect interpreter
owns the actual core.async channels and dispatches `:open`, `:enqueue`, and
`:subscribe` with Meander.

The request and result queues have bounded capacity. The bridge adds no explicit
locks, although the existing session host still synchronizes admission. We did
not make the whole runtime lock-free.

An input effect triggers IO after commit. The worker's reply returns through the
host callback boundary as a versioned TTMS publication. “Session advancement”
means admitting that publication and propagating it into a new immutable
session state. The network describes the effect; the host performs it and
admits its result. Compiling the pure session or reading strongest values does
not send an HTTP request.

The current `:actions` field contains reply text. Its name does not mean that
generated code is being executed. Similarly, `:thinking` exposes a provider
response field; it is not a worker-busy indicator.

## What failed and what we learned

Several failures appeared as “nothing is showing,” but had different causes.
Separating them was much more useful than treating every empty display as a
model failure.

| Failure or confusing symptom | Cause or finding | Outcome |
| --- | --- | --- |
| The definition did not give us an instance with usable slots | A callable definition still needs to be materialized with `(model)` | We created `m` and addressed its connected slots |
| An ordinary wrapper did not connect the worker's output back to the caller | The output target needed an explicit operator output declaration; a Lain wrapper did not provide the required connection | `io-model:output` declares its second argument as an output, including while it is unknown |
| The native bridge could not be imported in the hosted editor | The project path was outside the application's allowed import root, and the native namespace needed to be available on the backend classpath | With authorization, we placed the bridge inside the existing backend root and imported it explicitly; no service restart was required |
| Blazux returned HTTP 400 about a missing request body | Java's default h2c upgrade interacted badly with the local proxy | Explicit HTTP/1.1 made the same transport path work |
| An editor change appeared not to arrive | Saved file contents, browser text, last-run source, and session extensions were distinct states | We inspected session status and used the editor API to load and run the intended source |
| A rerun lost the intended imported bindings | The local assembly needed to retain explicit import edges when combining snapshots from the runtime and project roots | We restored the original source and merged the import catalogs; rerun is covered by the model IO tests |
| Empty slots were not visible in the value display | Unknown connected fields were not presented as the user expected | Recorded as an open display issue; we did not seed fake values to make them visible |
| Inspecting all of `m` raised “Compound projection found ambiguous slot parents” | The projector rejects multiple locally present parent references for a field without comparing their values | Direct reply reads and semantic tracing worked; the whole-record projection issue remains open |
| `focus` returned nothing | The hosted editor already binds `focus` to a visualization operator; the second definition made the callable cell contradictory | Renaming the user network to `model-view` worked |
| Adding a second `(display ...)` through an extension caused a contradiction | It added another connection to the existing display sink rather than replacing it | Reloading source with one display expression worked |

The `focus` diagnosis deserves particular attention. The assistant initially
concluded that returning a struct containing slot reads from a network was
broken. The supposedly isolated reproduction reused the name `focus`, so it
reproduced the name collision too. Tracing the callable in the editor's actual
environment exposed the contradiction. Repeating the test under `model-view`
produced the expected record.

That was a diagnosis error, not a defect in nested struct composition. A useful
reproduction must isolate names and installed capabilities as well as data.
The unresolved ambiguous-parent error is separate; renaming `focus` does not
repair it. We also have not established whether all parent values agree in the
failing model inspection.

## Plain reply fields and connected slots needed different operations

The model's struct contains connected slots. The provider reply is a plain
record carried as a supported value. We made this distinction explicit:

```clojure
;; Access a connected model slot.
(define model:output (slot :output))
(model:output m)

;; Read a field from the supported reply record.
(prim-slot :text (model:output m))
```

The user asked for the small generic primitive `prim-slot`, rather than adding
special thinking and text output arguments to the worker API. It preserves
ordinary primitive support propagation. Tests cover an unknown record, absent
keys, explicit empty strings, withdrawal, and recovery. `prim-slot:text` is not
an automatically defined shorthand; it would need an explicit definition.

The worker boundary also needed lifecycle tests. Independent model instances
must not share responses. Identical replay must not enqueue another request.
Closing or rerunning a session must retire its worker and prevent late results
from entering the replacement run. A provider error must become an inspectable
output while allowing the next request to succeed.

## The moment we had something inspectable

The local live probe received “Hello!” from Blazux, with a completed response
and no truncation. The hosted editor then received “Hi! How can I help you
today?” through the compiled model and session extender. These were actual
provider calls, following the user's explicit authorization.

We then imported the semantic tracer and observed a struct assembled from the
model's slots:

```clojure
(load-lain "propagators/compiler_2/runtime/lain/tracers.lain")

(define model-struct-view
  (struct :input (model:input m)
          :thinking (model:thinking m)
          :action (model:action m)))

(define model-struct-trace
  ((tracer:semantic modifier:with-values) model-struct-view))

(display model-struct-trace)
```

First we added a named trace through the extender. The user then pointed out
that we could simply reload the file. We saved one display expression, loaded
the updated file through the editor API, ran it, and verified the graph. The
final inspected graph had 26 nodes and 28 edges. The preceding extended session
had 37 nodes and 44 edges; those were different declared topologies, not a
before-and-after performance comparison.

We had moved from a reply appearing somewhere in the system to a Lain model
whose connections and captured values we could inspect. That is the part of
the experiment worth carrying forward.

## What was verified and committed

| Evidence | Recorded result |
| --- | --- |
| [Final model IO tests](../runtime/model-io-test.log) | 6 tests, 42 assertions, zero failures or errors |
| [Clean runtime verification](../runtime/model-io-test-clean-runtime.log) | The same suite passed against runtime `5851bee`, without the unrelated runtime worktree edits |
| [Quality checks](../runtime/model-io-quality.log) | Pinned clj-kondo 2026.08.04 and cljfmt 0.16.6 passed; zero lint errors or warnings |
| [Local live probe](../runtime/model-io-live.log) | Successful reply and clean session shutdown; about 1.062 seconds from prompt extension to observed reply, excluding setup |
| [Hosted reply record](../runtime/model-io-hosted.edn) | Successful reply through the editor session; whole-record inspection failure recorded alongside it |

The single live latency observation is not a throughput benchmark. The test
runtime also emitted existing Meander reflection and external-classpath notices;
passing the quality gate does not mean every process was silent.

Commit `ff72a82` contains the native bridge, Blazux transport, `prim-slot`, the
traced Lain example, local session assembly, tests, probe tools, evidence, and
documentation. It was pushed to `wip/lain-open-sessions-20261104`. Earlier
behavior/FSM changes and exported editor files were not swept into that commit.
`soul.lain` was preserved.

The saved example leaves the prompt commented out so importing it does not
automatically enqueue a request. At the end of the experiment, the running
editor session still contained the user's submitted prompt. Those two states
were intentional.

## What remains for the next experiment

This version sends independent prompts. It has no conversation memory or token
streaming. Output publications have TTMS support, but causal provenance linking
each reply to its originating input is deferred. Bounded queues do not establish
bounded retention for the host's evidence or receipts. The native files added
inside the running release must be packaged into a future release to survive
replacement of that directory.

The [TODO list](../TODO.md) records the unknown-slot display, ambiguous-parent
projection, conflicting-definition diagnostics, and display replacement issues.
The larger behavior experiment still needs work on provenance cost and the
1,000-occurrence gate before model integration can be claimed.

The most useful next step is to keep the working model small while making these
boundaries clearer. We can improve diagnostics and test input-to-output
provenance without first rebuilding the entire orchestrator.

Today's lesson was practical: define the connections in Lain, make the effect
boundary explicit, test the parts with controlled inputs, and inspect the
actual running session. When something looks empty, first establish whether
it is unknown, contradictory, disconnected, or merely failing to render.

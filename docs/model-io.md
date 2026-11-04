# Model IO experiment

`model-refined.lain` now owns the model definition and the shared worker wiring.
The native implementation is `src/lain_harness/model_io.clj`; its effect
interpreter uses Meander to match `:open`, `:enqueue`, and `:subscribe`.

Each worker has bounded core.async request and result channels (capacity 8 by
default). Blocking IO and delivery use `core.async/thread`, with `alts!!` for
waiting and shutdown. The bridge introduces no explicit locks. The existing
session host still synchronizes immutable-session admission. A private native
resource catalog owns the channels; only an immutable session/run/call handle
enters Lain. Compilation and strongest reads do not allocate workers or send HTTP.

`io-model:input` accepts a string or a supported record containing `:text` and
optional `:id`. The input effect enqueues a request. Its result is `:completed`
when the worker has queued the response, or `:backpressured` if the request
channel is full. There is no automatic resend. Exact replay of unchanged evidence
does not enqueue again. Distinct supported publications can request the same text.

`io-model:output` declares its second argument as an output cell. It installs one
subscription per worker, including when that cell is unknown. Replies enter the
existing host callback ingress as versioned TTMS source publications. A full
host ingress is retried without losing the response. Session close/rerun retires
workers and subscriptions; late callbacks cannot publish into the retired run.

The first transport uses completed HTTP responses, not token streaming. Blazux
reply records contain `:kind`, `:text`, `:thinking`, `:finish-reason`, and
`:truncated?`. The default endpoint is `http://127.0.0.1:18320/v1` and the model is
`qwen3.8-flash-next`; native factory options can override these, the timeout,
maximum tokens, and optional API key. No credentials are embedded in Lain.
HTTP/1.1 is explicit: Java's default h2c upgrade caused this proxy to report a
missing request body (HTTP 400).

The worker returns errors as output records, without copying provider bodies or
credentials. The next request can still succeed. Thinking is read directly from
the reply's `:thinking` field. The current sketch's `:actions` slot exposes the
reply's `:text` field; it is text, not an executable action. The separate
`generated:action?` predicate recognizes explicitly tagged `:code` records.
Ordinary Blazux prose is not classified as code or executed.
This version sends each prompt independently, without conversation memory.
`prim-slot` reads a field from a supported plain record:

```clojure
:thinking (prim-slot :thinking output)
```

It is a generic pure primitive, separate from the worker. It uses ordinary TTMS
primitive support propagation, including withdrawal and recovery. The result
is unknown before the record arrives or when the key is absent; an explicit
empty string remains an empty string. `model:thinking` now exposes that field
value directly, rather than the entire reply selected by a Boolean condition.
The two-argument `io-model:output` API is unchanged. The file imports
`tracers.lain`, constructs `model-struct-view`, and displays
`((tracer:semantic modifier:with-values) model-struct-view)` through the named
`model-struct-trace` cell. The example prompt is commented out in the saved
source so importing the definition does not enqueue a model request; submit
the prompt through the session extender when ready.

Do not name this projection `focus`: the hosted editor already binds that name
to a visualization operator. Defining another callable in that cell produces a
contradiction rather than replacing the existing binding. Renaming the user
network to `model-view` resolved the apparent nested-struct failure. The separate
whole-record projection guard described in TODO still needs correction.

Input-to-output causal provenance remains deferred. Queue bounds do not imply a
retention bound on the host's TTMS evidence or effect receipts.

## Verification

```sh
LAIN_OFFLINE_UUID=1 tools/test_module.sh model-io
tools/check_model_io_quality.sh
tools/probe_model_io.sh --live
```

On 2026-11-04, the initial deterministic module passed 6 tests / 40 assertions: actual
compiled model source, two independent instances, duplicate replay, replacement
replies, thinking selection, errors and recovery, session rerun, cancellation,
bounded queues, Meander dispatch validation, a loopback HTTP contract, and
supported primitive field reads including withdrawal, recovery, and missing keys.
Pinned clj-kondo 2026.08.04 and cljfmt 0.16.6 reported zero errors and warnings.
The source imports are snapshotted from their respective project/runtime roots
by `model_io_session.clj`, retaining the explicit dependency edges for rerun.
The local compiler installs the editor's display and tracer capabilities so the
same saved source, including its semantic graph, compiles in the offline tests.

Final commit verification passed 6 tests / 42 assertions, including the current
text-valued actions slot and the semantic capture. The same module also passed
against a clean detached checkout of runtime `5851bee`, confirming that this
experiment does not require the unrelated runtime worktree edits. The clean
run is recorded in `runtime/model-io-test-clean-runtime.log`. Clojure emitted
external-classpath deprecation notices and Meander's existing reflection warning;
the pinned quality gate still reported zero lint errors and warnings. No live
provider request was made during this final verification.

The authorized live probe used the same compiled file and source-extension path
to send “Hi. Reply with one short greeting.” It received “Hello!”, a nonempty
thinking field, `finish-reason = stop`, and `truncated? = false`. Prompt extension
through observed reply took 1061.867086 ms; compilation/setup is excluded. The
session closed successfully. This is one connectivity observation, not a benchmark.

Environment: Linux 7.0.0-1019-nvidia aarch64; Temurin OpenJDK 21.0.12.1;
Clojure 1.12.4; core.async 1.8.741. Harness base `e91422c`, isolated runtime base
`5851bee`, both with existing worktree changes. Logs are in
`runtime/model-io-{test,quality,live}.log`.

## Hosted editor boundary

On the user's instruction, the two native files were subsequently copied into
the running backend's existing classpath root as `lain_harness/model_io.clj` and
`lain_harness/blazux.clj`. A `lib/model-io.lain` import file was added there with
its native path adjusted to `lain_harness/model_io.clj`. No existing backend
files were replaced and no service restart was needed. These additions belong
to the current release directory and must be included in a future release if
they are to survive replacement of that directory.

The hosted editor successfully imported the primitives, ran the saved
`model-refined.lain`, and accepted `(-> "hi" (model:input m))` through `extend`.
Blazux replied “Hi! How can I help you today?” with `finish-reason = stop` and
`truncated? = false`. At that check, the extension also
defined `model-reply` as `(model:output m)`, which the inspection API read as
ready. Inspecting the entire `m` record currently raises “Compound projection
found ambiguous slot parents”; this separate display issue is recorded in TODO.
Hosted verification details are in `runtime/model-io-hosted.edn`.

The generic `lain_harness/primitive_slot.clj` module was later added to the same
classpath root and imported through the active session's extender. Reading
`(prim-slot :thinking (model:output m))` returned the thinking string from the
existing live reply. This check made no new model request and did not rerun the
user's current editor source. The saved model definition uses this primitive;
the running definition changes when that updated source is run.

The final editor reload displayed the semantic trace directly (26 nodes and
28 edges at the recorded inspection). The preceding session-extension trace
was also ready (37 nodes, 44 edges); these observations have different declared
topologies and are not performance comparisons. The current editor run retains
the user's submitted prompt; the saved example leaves prompt submission explicit.

The original `soul.lain` remains unchanged. The unknown-slot display improvement
is recorded in `TODO.md`.

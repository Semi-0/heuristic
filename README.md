# lain-harness

[soul-refined.lain](soul-refined.lain) is the source of the experiment. It imports individually identified native extensions and keeps environment construction, lexical inheritance, capability composition, and inspectable policy networks in Lain:

```clojure
(-> (describe-environment e) (inputs-of model))
(incremental-compile (actions-of model) e)
```

The implementation uses independent model inbound/outbound FIFO queues, a streaming model transport, and atomic admission of each complete Lain form into `e`. It has been exercised with deterministic transports, not a live model. Loading the source does not start inference.

## Components

- `src/lain_harness/runtime.clj` supplies the native facilities, independent model/user extensions, and their open-session dependencies. Pure operators use `prim-op`; IO and subscriptions use `async-op` with the owner's effect interpreter.
- `src/lain_harness/environment.clj` describes initial lexical bindings and subsequent value/support changes. Native frame references retain lexical parentage and binding-cell identity. Compound previews are bounded at serialization, while live accessors remain connected.
- `lib/queue.lain` and `lib/rounds.lain` define FIFO and inference eligibility policies. `queue.clj` and `stream.clj` expose immutable transitions; `resource_queue.clj`, `resource_stream.clj`, and `resource_environment.clj` quarantine the existing resource adapters. `transport.clj` supplies configurable OpenAI-compatible SSE transport.
- The companion Compiler-2 change adds targeted, premise-supported extensions and immutable admission inputs and authoritative host publication. It is developed in `/home/lain/projects/lain-harness-runtime`; the reproducible [patch](runtime/streaming-extension.patch) is based on `5b5e4809c67902fd890d1d5ebc4f02ae19a55a2a` of `Semi-0/datalog-research`.

The existing deployment and loaded modules are unchanged. Apply the patch only to a separate checkout of that base revision. `LAIN_RUNTIME_ROOT` selects the development backend; verification scripts default to the sibling `lain-harness-runtime` checkout.

## Premises and streaming behavior

Each model session and inference round has its own premise source. Each emitted form has a derived extension cell carrying those premises and the support of context sent to the model. A context-validity premise prevents an input's changed value from silently validating code generated from its previous value.

Finishing a round leaves its premise active. Explicit withdrawal changes availability, retaining compiled topology. Recovery reuses topology. Retraction does not undo external effects already delivered. Use `lain-harness.premises/publish!` with the real source cell, increasing epoch, and `:active` or `:retracted` status.

Complete forms are admitted in FIFO order. An admission failure stops that response, retains earlier forms, and contributes a diagnostic to the environment for the next inference. Network/transport failure pauses the model; `resume-model!` is explicit. A model runs at most one inference at a time, queuing intervening environment changes for its next inference. The default budget is eight inferences; pending events remain inspectable when exhausted.

The native queues retain event history separately from consumer cursors. Duplicate identity/content is a no-op; conflicting identity reuse and pending-capacity overflow fail explicitly. There is no silent event dropping or generated-source rewriting.

Use declared output/parameter definitions for writes from supported networks, as in the existing premise-closure API. Scalar results, linked-list members, direct source links, and explicit parameter definitions have focused support tests. This is not a claim of unrestricted bidirectionality for arbitrary compound interfaces.

## Preparing an isolated session

Add this repository's `src` directory to the patched backend's classpath. From the backend working directory:

```clojure
(require '[lain-harness.runtime :as harness]
         '[propagators.compiler-2.session :as session]
         '[propagators.compiler-2.session.host :as host])

(def owner (host/open! (session/create-session)))
(def services (harness/services owner))
(harness/install-facilities! services)
(host/extend! owner
  (harness/specification-extension services "/path/to/lain-harness/soul-refined.lain"))
```

`specification-extension` prepares the actual source and existing compositional library, using the native import already supplied through `install-facilities!`. Later source extensions reuse that prepared import instead of reloading a live module.

Supply `model-extension` with `{:id ... :transport ...}` and `user-extension` with a separate identity through `host/extend!`. Either installation order is supported, including after the source has declared pending instance cells. `transport/streaming-model` accepts endpoint, model name, headers, token limit and timeout; tests inject a deterministic function instead. One model and one user-input interface are exposed per harness session.

`start-model!` starts the model dependency. Inference starts only once the Lain runtime connects an environment-description stream and compiler target. `publish-user!` publishes versioned user input independently. Optional weather and message-delivery capabilities remain unresolved until supplied; no hardcoded umbrella answer or weather dispatcher is installed.

Inspect `specification`, model `:state`, queue histories, source-admission receipts and the Compiler-2 source ledger. Close the isolated owner with `host/close!` when finished.

## Verification

```sh
tools/check_specification.sh
tools/test_streaming.sh
tools/check_quality.sh
```

In this sandbox, `clj-uuid` fails during network-interface enumeration before Compiler-2 loads. Set `LAIN_OFFLINE_UUID=1` to use the documented process-local initialization adjustment in `tools/offline_uuid.clj`. It changes no dependency files or compiler behavior. The normal check remains available without that flag.

Tests cover the actual source, both extension orders, fragmented SSE/code, lexical targeting, atomic failure, queue contracts, premise withdrawal/recovery, stale epochs, consumed-input changes, alternative support, network reuse, diagnostic feedback, transport pause, and changes arriving during inference. Existing immutable-session tests run alongside the new tests. No test calls Blazux or a weather provider.

## Preserved evidence

- [Original idea](soul.lain), retained unchanged.
- [Language guide](docs/how-to-think-in-lain.md) and [worked examples](docs/how-to-think-in-lain-examples.md).
- [Archived parallel experiment](/home/lain/experiments/lain-harness-live-20261103/parallel-scaffold-archive/experiments/live/REPORT.md) and [checksum manifest](/home/lain/experiments/lain-harness-live-20261103/parallel-scaffold-archive/manifest.json).

The archived scaffold is not a dependency of this implementation. Git history and unrelated workspace files remain intact.

## Lain-first migration status

This revision is an intermediate implementation, **not completion of the Lain-first
orchestration plan**. The existing transport path still uses host observation
subscriptions, mutable model/stream registries, resource queues, native context
selection, and a model worker that owns round coordination. The Lain queue and
round networks are executable and tested, but do not yet drive that transport
path. `describe-environment` and `incremental-compile` on the original runtime
relationship remain compatibility endpoints. These remaining boundaries must be
replaced before claiming fully Lain-owned orchestration.

The compiler worker and its promise registry have been removed. The transport
adapter now submits a single declaration to `host/admit-source!`, which publishes
`session/advance`'s immutable result under the existing host gates. Pure callers
can use `session/admission-input` directly with no host. Receipts are versioned
cell publications. A rejected form stops its response identity, retains previous
admissions, and marks subsequent forms skipped. Exact replays reuse the admission
ledger and topology. Conflicting identity reuse rejects. Ordinary cell-input
batches retain their previous atomic propagation behavior.

`incremental-compile-form`, defined in the actual entry point, reads one complete
form and returns a declaration plus its remaining suffix. It does not silently
consume the suffix or perform IO. The caller must wait for the connected receipt
before advancing the suffix. Complete orchestration of that feedback remains work
for the migration.

### Native audit and extension contracts

Existing `list`, `cons`, `car`, `cdr`, conditional networks, arithmetic, `struct`,
`slot`, `map-list`, and `filter-list` suffice for queue policy, round eligibility,
filtering, and descriptions. No queue or round scheduling primitive was added.
Existing relationship observers and TTMS publication supply observation lifetimes
and source epochs; the metadata adapter composes those facilities.

| Extension | Contract and reason native access remains |
| --- | --- |
| `lain-harness.source-reader/source-reader` | `source:read-next(text, finish?)` returns one parse result; Lain lacks access to its reader. No buffering service, admission, dependency, or IO. |
| `lain-harness.admission/declaration` | `admission:declaration(id, source, environment, support-cell)` returns an immutable session declaration; lexical frame/cell identities are compiler metadata. No host dependency. |
| `lain-harness.lexical-observation/bindings` | `lexical:bindings(environment)` exposes local lexical cell metadata through existing relationship observers; Lain cannot enumerate the compiler's lexical index. |
| `lain-harness.lexical-observation/parent` | `lexical:parent(environment)` exposes the declared lexical parent without a host lookup or registry. |
| Individual `lain-harness.runtime/<binding>` extensions | Existing environment construction, model port access, and compatibility endpoints each have their own identity and explicit source import. Description and compilation dependencies are separate. |

`facility-extensions` returns those independent declarations; `install-facilities!`
only assembles them. The two source primitives can be installed independently and
in either order, without resources. The reader's Java cursor is invocation-local
mutation, isolated in `source_parser.clj`; parser state and FIFO transitions are
immutable. The remaining mutable resource adapters are explicitly named above.

The native lexical value preview still has a bounded traversal policy. Moving all
traversal, change selection, provenance presentation, and model-facing relevance
to Lain remains part of the unfinished migration. Architecture checks cover the
pure queue, parser-state, admission, environment-metadata, and observation modules;
they do not assert that the legacy resource path is pure.

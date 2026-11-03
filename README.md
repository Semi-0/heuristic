# lain-harness

[soul-refined.lain](soul-refined.lain) is the source of the experiment. It now imports native open-session facilities while retaining environment construction, lexical inheritance and capability composition in Lain:

```clojure
(-> (describe-environment e) (inputs-of model))
(incremental-compile (actions-of model) e)
```

The implementation uses independent model inbound/outbound FIFO queues, a streaming model transport, and atomic admission of each complete Lain form into `e`. It has been exercised with deterministic transports, not a live model. Loading the source does not start inference.

## Components

- `src/lain_harness/runtime.clj` supplies the native facilities, independent model/user extensions, and their open-session dependencies. Pure operators use `prim-op`; IO and subscriptions use `async-op` with the owner's effect interpreter.
- `src/lain_harness/environment.clj` describes initial lexical bindings and subsequent value/support changes. Native frame references retain lexical parentage and binding-cell identity. Compound previews are bounded at serialization, while live accessors remain connected.
- `src/lain_harness/queue.clj`, `stream.clj`, and `transport.clj` provide FIFO event identity/acknowledgement, incremental Lain framing, and configurable OpenAI-compatible SSE transport. Mutable queues and transport resources stay outside propagator values; Lain carries immutable stream handles.
- The companion Compiler-2 change adds targeted, premise-supported extensions and queued admission at an idle host boundary. It is developed in `/home/lain/projects/lain-harness-runtime`; the reproducible [patch](runtime/streaming-extension.patch) is based on `5b5e4809c67902fd890d1d5ebc4f02ae19a55a2a` of `Semi-0/datalog-research`.

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
(host/extend! owner (harness/facilities-extension services))
(host/extend! owner
  (harness/specification-extension services "/path/to/lain-harness/soul-refined.lain"))
```

`specification-extension` prepares the actual source and existing compositional library, using the native import already supplied through `facilities-extension`. Later source extensions reuse that prepared import instead of reloading a live module.

Supply `model-extension` with `{:id ... :transport ...}` and `user-extension` with a separate identity through `host/extend!`. Either installation order is supported, including after the source has declared pending instance cells. `transport/streaming-model` accepts endpoint, model name, headers, token limit and timeout; tests inject a deterministic function instead. One model and one user-input interface are exposed per harness session.

`start-model!` starts the model dependency. Inference starts only once the Lain runtime connects an environment-description stream and compiler target. `publish-user!` publishes versioned user input independently. Optional weather and message-delivery capabilities remain unresolved until supplied; no hardcoded umbrella answer or weather dispatcher is installed.

Inspect `specification`, model `:state`, queue histories, source-admission receipts and the Compiler-2 source ledger. Close the isolated owner with `host/close!` when finished.

## Verification

```sh
tools/check_specification.sh
tools/test_streaming.sh
```

In this sandbox, `clj-uuid` fails during network-interface enumeration before Compiler-2 loads. Set `LAIN_OFFLINE_UUID=1` to use the documented process-local initialization adjustment in `tools/offline_uuid.clj`. It changes no dependency files or compiler behavior. The normal check remains available without that flag.

Tests cover the actual source, both extension orders, fragmented SSE/code, lexical targeting, atomic failure, queue contracts, premise withdrawal/recovery, stale epochs, consumed-input changes, alternative support, network reuse, diagnostic feedback, transport pause, and changes arriving during inference. Existing immutable-session tests run alongside the new tests. No test calls Blazux or a weather provider.

## Preserved evidence

- [Original idea](soul.lain), retained unchanged.
- [Language guide](docs/how-to-think-in-lain.md) and [worked examples](docs/how-to-think-in-lain-examples.md).
- [Archived parallel experiment](/home/lain/experiments/lain-harness-live-20261103/parallel-scaffold-archive/experiments/live/REPORT.md) and [checksum manifest](/home/lain/experiments/lain-harness-live-20261103/parallel-scaffold-archive/manifest.json).

The archived scaffold is not a dependency of this implementation. Git history and unrelated workspace files remain intact.

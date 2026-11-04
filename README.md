# lain-harness

This is an unfinished Lain-first migration. The immutable foundations pass their focused tests, but the canonical coded-conversation test does not yet deliver its first reply. Do not treat this checkout as acceptance of the orchestration plan. Current results and remaining work are recorded in [runtime/verification.json](runtime/verification.json) and [runtime/verification.log](runtime/verification.log).

The companion runtime snapshot is [datalog-research at `1d584e5`](https://github.com/Semi-0/datalog-research/commit/1d584e5509280bc07a3ebf5276dc5f5f10f615b1). Both repositories use the branch `wip/lain-open-sessions-20261104` for this incomplete snapshot.

[soul-refined.lain](soul-refined.lain) imports the executable policies. The original [soul.lain](soul.lain) is unchanged. The orchestration observes the parent and targets its lexical child:

```clojure
(let [e (lexical-child environment)]
  (-> (describe-environment environment) (inputs-of model))
  (incremental-compile (actions-of model) e))
```

`lib/observations.lain` implements lexical traversal, shadowing, selection and rendering. Native metadata describes one lexical frame or one compound's immediate fields. It has no native depth limit or domain-specific rendering schema. An additional contribution is implemented in `lib/observation-example.lain`.

Model and user sources are declared in `.lain`: `source:new` in `lib/ports.lain` declares a cell and wires `source:initialize` directly to it; `model:new` in `lib/model.lain` constructs each model's ports and state. Harness extensions contain primitive/effectful propagators, source files, interface declarations and external dependencies. They contain no preseeded cells or binding aliases to host-allocated model cells. Interfaces resolve references exported by the compiled Lain network.

Custom records and lists travel as TTMS base values; scalar constants use primitive values. There is no `Frozen` datatype. Data lists use `:lain.data/empty`, distinct from the compiler's open-list topology marker. Observation metadata uses `:premises` so inspecting a withdrawn premise does not itself retract the observation.

`lib/model.lain` uses `lib/queue.lain` and `lib/rounds.lain` to select requests, build prompts, maintain history and advance versioned model state. `lib/compiler.lain` buffers source and selects one admission at a time, waiting for its connected receipt. Resume is an ingress command interpreted by `model:command`; it preserves the current turn and inference budget. These are connected to the current execution path; the former host model coordinator and shared model/stream registries are no longer used.

`and` and `or` are primitive Boolean propagators; `not` is the existing builtin. A deciding operand can produce a result while other inputs are pending. Value support follows the deciding operand; premise-state updates from every watched input invalidate withdrawn evidence. Multiway policies use Compiler-2’s `(cond [test result ... else fallback])`; guarded operations still use conditional selection.

The latest bounded probe accepts and queues a user message but leaves the model context uninitialized: zero transport requests and zero delivered replies. The parent-description connection remains unresolved. The earlier snapshot reached transport output and exhausted its propagation budget; that earlier evidence is retained separately. The refactor passes 46 focused tests with 563 assertions, but successful admission and acknowledged reply delivery through the full composition have not been established.

## Open-session interfaces

The isolated runtime adds immutable connected declarations and declared interfaces to the existing session API. The authoritative host publishes user messages with stable identities and a current run token. Identical replay is harmless; conflicting identity reuse is rejected.

The editor commands `user-message` and `conversation-status` use the same owner and ingress operation as headless tooling. Existing owner discovery, generation checks, external-owner mutation restrictions and attachment mechanisms remain in force. Detaching a view does not close its owner.

Trusted experimental preparation is available through:

```clojure
(require '[lain-harness.runtime :as harness]
         '[propagators.compiler-2.session.host :as host])
(def services
  (harness/prepare!
    {:source-path "/path/to/lain-workspace/soul-refined.lain"
     :transport fake-transport
     ;; Optional: the editor service's existing owner catalog.
     :owners owners}))
(harness/publish-user! services "user" "message-1" "Hello")
(host/interface-values (:owner services))
(host/close! (:owner services))
```

Preparation submits no user message. Model attachment has no compiler-target argument; `conversation-experiment` explicitly connects compilation. Generated Lain source must invoke `message-to-user`; transport output is never directly displayed as a conversation reply.

## Verification and boundaries

```sh
LAIN_OFFLINE_UUID=1 tools/check_specification.sh
LAIN_OFFLINE_UUID=1 tools/test_streaming.sh
tools/check_quality.sh
```

The full test command includes the canonical conversation test and currently fails. The UUID workaround is process-local and changes no deployed dependency files. Quality tools are pinned to clj-kondo 2026.08.04 and cljfmt 0.16.6.

The [runtime patch](runtime/streaming-extension.patch) targets `5b5e4809c67902fd890d1d5ebc4f02ae19a55a2a` and is developed only in `/home/lain/projects/lain-harness-runtime`. No deployment, merge or service restart was performed. Native and mutable boundaries are listed in [docs/native-boundaries.md](docs/native-boundaries.md).

The archived Blazux configuration was checked on 2026-11-04. Access outside the restricted tool sandbox returned `qwen3.8-flash-next` from `http://127.0.0.1:18320/v1/models`. A live coded conversation has not been attempted because the deterministic harness path is not passing.

Remaining work includes the integrated propagation failure, model/round/consumed-context admission premises, Lain-owned diagnostic feedback, ingress/output backpressure, failure/resume and cancellation correctness, complete per-turn budget coverage, selective feedback acceptance, and full conversation preservation across editor attachments. The obsolete `resource_*` adapters remain unreferenced pending passing replacements; they are not an alternate canonical runner.

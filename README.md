# lain-harness

The current local conversation experiment is documented in
[Environment-constructed conversation](docs/environment-conversation.md).
`soul-refined.lain` imports the existing model and supplies its bootstrap context
and operator documentation through lexical environment bindings. Run
`tools/test_module.sh soul` and `tools/test_module.sh operator-examples` for focused checks and `tools/probe_soul.sh` for the
real-model construction and three conversation rounds. Live acceptance is
reported in that experiment's evidence; deterministic tests alone do not establish it.

The migration notes below describe the earlier integration path and its recorded
limitations. Its historical tests do not establish acceptance of the current
entry point.

This is an unfinished Lain-first migration. The immutable foundations pass their focused tests, but the canonical coded-conversation test does not yet deliver its first reply. Do not treat this checkout as acceptance of the orchestration plan. Current results and remaining work are recorded in [runtime/verification.json](runtime/verification.json) and [runtime/verification.log](runtime/verification.log).

The companion runtime snapshot is [datalog-research at `6f14751`](https://github.com/Semi-0/datalog-research/commit/6f147518dde882d027682fc0323f6ef0477d9441). Both repositories use the branch `wip/lain-open-sessions-20261104` for this incomplete snapshot.

[soul-refined.lain](soul-refined.lain) imports the executable policies. The original [soul.lain](soul.lain) is unchanged. The orchestration observes the parent and targets its lexical child:

```clojure
(let [e (lexical-child environment)]
  (-> (describe-environment environment) (inputs-of model))
  (incremental-compile (actions-of model) e))
```

`lib/observations.lain` implements lexical traversal, shadowing, selection and rendering. Native metadata describes one lexical frame or one compound's immediate fields. It has no native depth limit or domain-specific rendering schema. An additional contribution is implemented in `lib/observation-example.lain`.

Model and user sources are declared in `.lain`: `source:new` in `lib/ports.lain` declares a cell and wires `source:initialize` directly to it; `model:new` in `lib/model.lain` constructs each model's ports and state and exposes its live endpoints as a `struct`. Native harness extensions supply primitive/effectful propagators and external dependencies; the session file loader supplies Lain libraries. They contain no preseeded cells or binding aliases to host-allocated model cells. The host model/user extension factories are removed. Lain exports cell references with `session:export`; the host resolves those declarations without constructing instances.

Custom records and lists travel as TTMS base values; scalar constants use primitive values. There is no `Frozen` datatype. Data lists use `:lain.data/empty`, distinct from the compiler's open-list topology marker. Observation metadata uses `:premises` so inspecting a withdrawn premise does not itself retract the observation.

`integration.lain` imports dependencies, declares optional endpoints, and owns the queue capacity (256) and per-turn inference budget (8). `soul-refined.lain` imports that file. `integration:model` constructs a model from a user source and a capability `struct`; `integration:launch` mounts one conversation into the editor endpoints. Native `transport:stream` and `message:deliver` supply only external effects.

`lib/model.lain` uses `lib/queue.lain` and `lib/rounds.lain` to select requests, build prompts, maintain history and advance versioned model state. `lib/compiler.lain` buffers source and selects one admission at a time, waiting for its connected receipt. Resume is an ingress command interpreted by `model:command`; it preserves the current turn and inference budget. These are connected to the current execution path; the former host model coordinator and shared model/stream registries are no longer used.

`and` and `or` are primitive Boolean propagators; `not` is the existing builtin. A deciding operand can produce a result while other inputs are pending. Value support follows the deciding operand; premise-state updates from every watched input invalidate withdrawn evidence. Multiway policies use Compiler-2’s `(cond [test result ... else fallback])`; guarded operations still use conditional selection.

The latest bounded probe accepts and queues a user message but leaves the model context uninitialized: zero transport requests and zero delivered replies. The parent-description connection remains unresolved. The earlier snapshot reached transport output and exhausted its propagation budget; that earlier evidence is retained separately. The refactor passes 48 focused tests with 601 assertions, but successful admission and acknowledged reply delivery through the full composition have not been established.

## Open-session interfaces

The isolated runtime adds immutable connected declarations and declared interfaces to the existing session API. The authoritative host publishes user messages with stable identities and a current run token. Identical replay is harmless; conflicting identity reuse is rejected.

The editor commands `user-message` and `conversation-status` use the same owner and ingress operation as headless tooling. Existing owner discovery, generation checks, external-owner mutation restrictions and attachment mechanisms remain in force. Browser verification attaches the existing owner and displays its current model state; the full topology probe exceeded a 30-second request bound and remains unverified. Detaching a view does not close its owner.

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

## Opt-in temporal behavior experiment

`tools/test_module.sh behavior` compiles actual Lain fixtures and advances immutable sessions with supported temporal occurrences. Import `lib/behavior.lain` in a session created by `lain-harness.behavior/create-session`; the extension provides `scan`, `map`, `lift`, `hold`, `sample`, `event-filter`, `history`, `last`, and `history-map`. The library comments document signatures, support, interval boundaries, and window semantics.

The experiment uses the existing reducer-cell and Compiler-2 callable path. Reduction runs during merge. Its local runner is assembled with `network-iterator-constructor`; both session ingress and propagator messages use the selected evaluator. Default sessions retain their existing evaluation policy. No model orchestration or deployed runtime is changed.

Run the separate local benchmark with `tools/bench_behavior.sh`. It uses fixed evidence identities, 100 and 1,000 occurrences, one warm-up and three measured repetitions, with a two-minute limit per workload/size. Outputs are validated before timings are reported; unfinished cases are explicit. Results, environment details, commands, and limitations are recorded in `runtime/behavior-verification.edn` and the adjacent behavior logs. The experiment is not yet suitable for model integration: retained prefix provenance grows quadratically, and large cases can exceed the benchmark limit. A 64-occurrence window bounds payloads and caches, but replay-rejection metadata still grows. Window scans cover only the retained domain.

Final local verification: the behavior module passes 14 tests / 274 assertions. `tools/check_behavior_quality.sh` passes pinned cljfmt and clj-kondo checks with zero errors and two existing session warnings. Runtime regressions reproduce the baseline's four failing assertions; foundation tests reproduce eight errors from the existing, unchanged `lib/model.lain` parse failure.

The final 100-occurrence run produced these publication-to-settlement latencies (milliseconds, pooled over three repetitions):

| Workload | Median | p95 | Mean processing per repetition |
| --- | ---: | ---: | ---: |
| Incremental scan | 99.9 | 340.1 | 13.01 s |
| Scan → map → sample | 167.3 | 583.7 | 22.31 s |
| Duplicate replay | 31.0 | 35.2 | 3.14 s |
| Middle withdrawal + recovery | 425.0 | 430.0 | 0.85 s for the pair |
| 64-occurrence window | 59.5 | 106.9 | 5.97 s |

All five 1,000-occurrence cases in the full matrix were unfinished at 120 seconds, without completing warm-up. They establish no large-case correctness or timing result. The window case at 100 occurrences retained 64 evidence payloads and 64 cache entries; its 100 premise states and 36 replay tombstones are separate metadata. Scan retained 5,050 prefix-selection references for 100 occurrences. These measured limitations keep model integration deferred.

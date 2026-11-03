# Supervised direct-Blazux experiment — 2026-11-03

## Observed outcome

The existing Soul compiler session was extended successfully. Its session ID `22364858-9b15-4a53-ad39-8907f115ebe9` and run ID `13fa393f-da77-44e7-bbbf-2f3ef89bd24e` remained unchanged. The backend was not restarted. No Pi adapter or agent was used.

The existing `gx10-blazux-lain-test` container was started without changing its configuration. Its vLLM-compatible endpoint at `127.0.0.1:18320` advertised `qwen3.8-flash-next`, with context length 131072. The final check showed about 25 GiB MemAvailable, above the inherited 8 GiB reserve.

The final v4 run made seven direct inference requests, installed two generated source publications, made two real weather requests, and recorded no effect errors. The last event was 20:07:32 UTC; subsequent inspection found the compiler settled at `"No new source"`. No inference remained in progress. Seven requests include automatic reactions to compiler/weather observations and closely spaced input updates; this is not yet inference-efficient batching.

Blazux generated a parameterized `umbrella-for-v4` network, including an ordinary local `prob` binding. Its first application queried Amsterdam. Its second source publication only applied the existing function to Rotterdam: the definition was reused. See [the actual generated source](generated-blazux.lain).

| Check | Observed result |
| --- | --- |
| Amsterdam, 2026-11-04 | Real Open-Meteo probability 0.0; `h4:answer` = `"No umbrella needed."` |
| Rotterdam, 2026-11-04 | Real Open-Meteo probability 0.0; `h4:answer-2` = `"No umbrella needed."` |
| Unknown unrelated slot | `h4:unrelated` stayed unresolved without blocking inference |
| Compiler feedback | Published into the live observation field; reached the model automatically |
| Weather feedback | Actual place/date/probability observations reached the model automatically |
| End of computation | Model returned empty source; no duplicate network installation |
| Session preservation | Same original session and run identities; original Soul definitions preserved |

These are recorded observations, not a current weather recommendation. The adapter uses the daily precipitation-probability field documented by [Open-Meteo](https://open-meteo.com/en/docs).

## Environment and tracing

The description carries a document index, the complete 56,602-character language guide, the 9,642-character harness design, current request, capability description and live observations. The live interface consists of connected struct fields. Native serialization happens at the model boundary; a source change does not require rebuilding the scaffold.

The editor inspection API returned ready results and semantic graphs for both applications. A private browser view of the **actual deployed editor** rendered the live graph with four top-level nodes and no page errors. It forwarded authenticated read-only inspection/layout requests; its restore request was intercepted locally so it could not reset the user's panes. Screenshot: [editor-trace.png](evidence/editor-trace.png). The real user's browser has not been remotely attached: use **Reopen running session** and the session ID above, then inspect `h4:answer` in graph mode. The full computation can be expanded from the named application.

## Supervision and corrections

1. The first Java HTTP transport attempt returned HTTP 400 (missing request body). A Python endpoint probe succeeded. Explicit HTTP/1.1 fixed the Java/server interoperability issue. There was no stalled model inference.
2. The early reuse run supplied three arguments to a live two-argument network, following a schematic design example. Atomic compilation rejected it. The supervisor published the actual signature and diagnostic; Blazux repaired the application. Live signatures now explicitly take precedence over design sketches.
3. A supervisor observation at revision 100 exposed an adapter bug: lower-numbered compiler receipts could be superseded. The final adapter advances its observation counter past explicit publications; a regression verifies error feedback above revision 100.
4. The initial source allowlist rejected harmless local variable names. Automatic diagnostic feedback made Blazux revise the source, but this was an adapter defect, not invalid Lain. The final validator respects lexical `network`/`let` scope while rejecting undeclared external operations. The v4 model's original `prob` binding compiled successfully without repair.

Each native change used a new namespace/revision, preserving the previous immutable bindings and receipts. New opt-in module files were staged under the backend's existing classpath; no tracked production files, policy, global Vars, or service definitions were changed. Earlier experiment graphs remain in the original session as historical evidence. This is not a production CI/CD rollout.

## Verification

- Final focused suite: **3 tests, 18 assertions, zero failures/errors**. Uses the actual immutable compiler/session runtime with a deterministic model fixture for reproducible dependency and error-path checks.
- Live proof separately uses the real on-device Blazux model and real weather IO.
- clj-kondo: **zero errors and zero warnings**; cljfmt applied to native code and tests.
- Browser proof: actual live editor graph, four nodes, no JavaScript page errors.
- Evidence: [final-evidence.json](evidence/final-evidence.json), [test log](evidence/test-v4.log), [quality](evidence/quality-v4.log), [browser receipt](evidence/editor-proof.json).
- Full private chronological events, original state, previous module revisions and traces are retained in `/home/lain/experiments/lain-harness-live-20261103`.

## Limits and follow-up work

This is an MVP experiment, not the full research proposal:

- `describe-environment` observes selected connected fields and an explicit capability index. Automatic discovery of every new lexical binding is not implemented. Guide/design declarations are pinned for the scaffold version.
- The exposed operations and generated definition name are deliberately small for this weather experiment. The model adapter speaks directly to the OpenAI-compatible vLLM endpoint; a separate native Ollama protocol has not been tested.
- The model may make overlapping inferences for closely spaced updates. Stale replies are subject to the session boundary's admission rules, but request coalescing/cancellation efficiency needs further work.
- Inference is bounded at eight requests per adapter instance (seven used in v4), with 90-second HTTP deadlines. Weather has 25-second deadlines. This is not an unlimited background agent or perpetual supervisor.
- Generated code is installed by the session host extender and retained in execution receipts/source files. It is not yet reconciled into the editor's applied-extension history. Do not use Run or extension removal as a persistence test: these can rebuild from an incomplete editor source history. Backend-restart reconstruction has not been implemented or claimed.
- Traces expose compiler-native applications and dependencies. A unified causal edge from each model source publication into every installed definition, persistent retrieval, lexical-environment ownership and generalized hot replacement still need explicit design.
- Model/provider errors are recorded and stop that attempt; automatic retry of uncertain external work is not implemented. Real-weather failure remains an observable error rather than fabricated weather.

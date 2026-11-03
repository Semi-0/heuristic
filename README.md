# lain-harness

An experimental agent scaffold in which a model formulates Lain networks and the existing CPS compiler installs them into a persistent open session.

The maintained relationship is:

```clojure
(-> (h4:describe-environment e) (h4:inputs-of model))
(h4:install (h4:source (h4:actions-of model)))
```

The description observes connected environment fields. Request, capability and observation updates propagate to the existing model input. The model talks directly to Blazux's vLLM-compatible HTTP endpoint; Pi is not involved.

- [Original idea](soul.lain) and [annotated design](soul-refined.lain).
- [Executable scaffold](experiments/live/scaffold-v4.lain).
- [Native adapter](src/lain_harness/live_v4.clj): direct inference, explicit effect delivery, generated-source admission, same-session extension, and weather observations.
- [Focused compiler/session tests](test/lain_harness/live_v4_test.clj).
- [Observed run and limitations](experiments/live/REPORT.md).
- [Language guide](docs/how-to-think-in-lain.md) and [worked examples](docs/how-to-think-in-lain-examples.md).

## Running on the existing GX10 editor

This is an opt-in experiment, not a standalone packaged runtime. It requires the deployed Lain Compiler-2/editor dependencies. The guide and design are indexed and included in the environment description; the model can distinguish the live capability signature from schematic design examples.

1. Copy `experiments/live/config.example.edn` to `experiments/live/config.edn` and review its endpoint, inference budget and event-log path. The current adapter's `project` constant identifies this GX10 workspace.
2. Make the reviewed native module available as `lain_harness/live_v4.clj` under the backend's supported classpath/import root. The supervised run staged a new file without changing tracked release files or restarting the backend. Never overwrite a previously loaded module to change a live session: use a new namespace/revision.
3. Run `python3 tools/build_live.py` to build the document-bearing source extension.
4. Extend a selected existing session using:

   ```sh
   python3 tools/editor_session.py --session SESSION_ID --extend experiments/live/bootstrap-v4.lain
   python3 tools/editor_session.py --session SESSION_ID --extend experiments/live/start-v4.lain
   ```

   The start file contains an explicit example date. Adjust it for a new experiment. Do not re-run this bootstrap in the already-extended session; its definitions are already installed.
5. In the editor, choose **Reopen running session** and select that owner. Inspect `h4:answer` as a graph; expand `umbrella-for-v4` to see its computation. `h4:model-input`, `h4:model-view`, and `h4:execution` expose the input/model/compiler side.

The immutable session keeps its identity and run ID across these extensions. HTTP inference has a 90-second deadline and a per-adapter inference budget. Weather requests have a 25-second deadline. Compiler errors and weather observations publish through the same live environment rather than an external tool loop.

The weather example supports Amsterdam and Rotterdam and reads the daily precipitation probability from [Open-Meteo](https://open-meteo.com/en/docs). Fixture observations are labeled separately in the execution report.

## Scope

This validates a live, selected environment interface and compiled network reuse. It does not yet provide automatic enumeration of every lexical binding, a complete persistent-memory store, or safe arbitrary generated programs. Guide/design declarations are pinned for a given scaffold version. Generated source is recorded in the compiler and execution receipts; editor source-history reconciliation and restart reconstruction remain follow-up work. See the report before treating this as a production harness.

## Focused verification

Run `tools/test_live.sh` on GX10, or set `LAIN_RUNTIME_ROOT` to a compatible Lain backend checkout. This runs compiler/session tests with a deterministic model fixture; it does not call Blazux or the weather service. Live receipts are recorded separately in the experiment report.

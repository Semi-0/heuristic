# lain-harness

[soul-refined.lain](soul-refined.lain) is the experiment's entry point and source for subsequent implementation and verification. It is a top-down, inspectable specification awaiting review. Missing primitives remain explicit unresolved cells; loading the specification does not start a model run.

The central relationships remain:

```clojure
(-> (describe-environment e) (inputs-of model))
(incremental-compile (actions-of model) e)
```

Inspect `specification` for interfaces, implementation obligations, acceptance scenarios, guide index and example request. Environment construction and capability composition remain in Lain. The weather answer must be generated, rather than supplied by the harness.

Missing implementations will use the existing Compiler-2 open-session extension API with primitive (`prim-op`) and async (`async-op`) operators. Model-session and user-input contributions remain independent. The native adapter import is unresolved. Review this source before implementing those adapters or running inference.

- [Original idea](soul.lain), retained unchanged.
- [Language guide](docs/how-to-think-in-lain.md) and [worked examples](docs/how-to-think-in-lain-examples.md).
- [Archived parallel scaffold and evidence](/home/lain/experiments/lain-harness-live-20261103/parallel-scaffold-archive/experiments/live/REPORT.md).
- [Archive file/checksum manifest](/home/lain/experiments/lain-harness-live-20261103/parallel-scaffold-archive/manifest.json).

The archive preserves repository-relative paths, including the parallel source, adapter, tests, experiment tools and `experiments/live/`. Copies were checksum-verified before repository copies were removed. Git history is retained. The existing runtime and its loaded module files are untouched; source archival does not dispose of a session.

For isolated verification, run `tools/check_specification.sh` with `LAIN_RUNTIME_ROOT` optionally pointing to a compatible Compiler-2 backend. This compiles the actual source and checks known specification fields and unresolved obligations without opening a host session or dispatching effects.

Verification on 2026-11-03: the normal JVM check was blocked during `clj-uuid` initialization by sandbox network-interface restrictions. The isolated check passed with a process-local UUID initialization adjustment (no compiler or runtime-file changes). [Check log](/home/lain/experiments/lain-harness-live-20261103/specification-offline-check.log) and [adjustment script](/home/lain/experiments/lain-harness-live-20261103/check-soul-offline.clj) record that limitation. Behavioral acceptance scenarios remain future work, not verified capabilities.

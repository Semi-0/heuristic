Inspection context preview, 2026-11-07.

context-1.txt is the exact initial environment-derived request captured by a
controlled transport. No real provider was called. context-2.txt includes the
controlled example source and observations; it is not a live-model result.
verification.edn records successful admission, partial completion to 12,
late callable completion to 5, feedback value 5, updated directory states,
and empty error inspection on a healthy cell.

Source: examples/lain/inspection-inventory.lain, lib/inspection-context.lain,
src/lain_harness/inspection_experiment.clj, tools/preview_inspection_context.clj.
The old experiment sources and evidence remain unchanged.

The existing environment:overview operator lists visible unknown bindings.
A dedicated undefined-closure propagator has not been located; no such capability
is claimed. inspect:errors is a new small adapter over native error-layer records,
not a new error algebra or automatic causal diagnosis. The worked examples are
literal prompt documentation, tested separately by the controlled transport.

Reproduce from the companion runtime using an isolated harness root containing
the committed model-refined.lain, src/lib/tools, the four source files above,
and inspection-inventory.lain copied to soul-refined.lain:
LAIN_HARNESS_ROOT=<isolated-root> LAIN_EVIDENCE_DIR=<fresh-output-dir> \
LAIN_OFFLINE_UUID=1 clojure -J-Xmx2g \
-Sdeps '{:aliases {:experiment {:extra-paths ["<isolated-root>/src" "editor/src"]}}}' \
-M:experiment <isolated-root>/tools/preview_inspection_context.clj

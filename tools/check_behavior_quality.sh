#!/usr/bin/env bash
set -euo pipefail
harness_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
lain_runtime_root=${LAIN_RUNTIME_ROOT:-"$(dirname -- "$harness_root")/lain-harness-runtime"}
cd -- "$lain_runtime_root"
exec clojure -Sdeps '{:deps {clj-kondo/clj-kondo {:mvn/version "2026.08.04"} dev.weavejester/cljfmt {:mvn/version "0.16.6"}}}' -M \
  "$harness_root/tools/quality.clj" "${1:-check}" \
  propagators/cell_evaluator.clj propagators/runner.clj propagators/propagator.clj \
  propagators/compiler_2/session.clj propagators/compiler_2/session/files.clj \
  propagators/stdlib/observation.clj propagators/datastructures/reducer_cell.clj \
  propagators/experimental/temporal_behavior.clj propagators/experimental/temporal_runner.clj \
  test/propagators/experimental/temporal_behavior_test.clj \
  "$harness_root/src/lain_harness/behavior.clj" \
  "$harness_root/src/lain_harness/runtime.clj" \
  "$harness_root/test/lain_harness/model_behavior_test.clj" \
  "$harness_root/test/lain_harness/model_test.clj" \
  "$harness_root/test/lain_harness/module_support.clj" \
  "$harness_root/test/lain_harness/behavior_test.clj" \
  "$harness_root/test/lain_harness/behavior_benchmark.clj" \
  "$harness_root/tools/bench_behavior.clj" "$harness_root/test/modules.edn"

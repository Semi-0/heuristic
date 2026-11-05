#!/usr/bin/env bash
set -euo pipefail
harness_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
lain_runtime_root=${LAIN_RUNTIME_ROOT:-"$(dirname -- "$harness_root")/lain-harness-runtime"}
cd -- "$lain_runtime_root"
exec clojure -Sdeps '{:deps {clj-kondo/clj-kondo {:mvn/version "2026.08.04"} dev.weavejester/cljfmt {:mvn/version "0.16.6"}}}' -M \
  "$harness_root/tools/quality.clj" "${1:-check}" \
  "$harness_root/src/lain_harness/blazux.clj" \
  "$harness_root/src/lain_harness/model_io.clj" \
  "$harness_root/src/lain_harness/primitive_slot.clj" \
  "$harness_root/src/lain_harness/model_io_session.clj" \
  "$harness_root/test/lain_harness/model_io_test.clj" \
  "$harness_root/tools/probe_model_io.clj" "$harness_root/test/modules.edn" \
  "$harness_root/src/lain_harness/soul_session.clj" \
  "$harness_root/src/lain_harness/lexical_observation.clj" \
  "$harness_root/src/lain_harness/environment.clj" \
  "$harness_root/test/lain_harness/soul_test.clj" \
  "$harness_root/test/lain_harness/operator_examples_test.clj" \
  "$harness_root/tools/probe_soul.clj"

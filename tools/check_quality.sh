#!/usr/bin/env bash
set -euo pipefail
harness_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
lain_runtime_root=${LAIN_RUNTIME_ROOT:-"$(dirname -- "$harness_root")/lain-harness-runtime"}
cd -- "$lain_runtime_root"
exec clojure -Sdeps '{:deps {clj-kondo/clj-kondo {:mvn/version "2026.08.04"} dev.weavejester/cljfmt {:mvn/version "0.16.6"}}}' -M \
  "$harness_root/tools/quality.clj" "${1:-check}" \
  "$harness_root"/src/lain_harness/{admission,connected_data,environment,lexical_observation,queue,runtime,source_parser,source_reader,stream,value_metadata}.clj \
  "$harness_root"/test/lain_harness/*.clj \
  "$harness_root/tools/check_specification.clj" \
  "$harness_root/tools/run_checks.clj" \
  "$harness_root/tools/quality.clj" \
  propagators/compiler_2/session.clj propagators/compiler_2/session/host.clj \
  propagators/compiler_2/session/connected.clj \
  editor/src/graph/lain_editor/runtime/session.clj editor/src/graph/lain_editor/server.clj

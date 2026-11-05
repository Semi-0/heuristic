#!/usr/bin/env bash
set -euo pipefail
harness_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
lain_runtime_root=${LAIN_RUNTIME_ROOT:-"$(dirname -- "$harness_root")/lain-harness-runtime"}
export LAIN_HARNESS_ROOT="$harness_root"
export LAIN_OFFLINE_UUID=${LAIN_OFFLINE_UUID:-1}
cd -- "$lain_runtime_root"
exec clojure -J-Xmx2g -Sdeps "{:aliases {:model-io {:extra-paths [\"$harness_root/src\" \"editor/src\"]}}}" \
  -M:model-io "$harness_root/tools/probe_soul.clj" "$@"

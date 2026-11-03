#!/usr/bin/env bash
set -euo pipefail
harness_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
lain_runtime_root=${LAIN_RUNTIME_ROOT:-"$(dirname -- "$harness_root")/lain-harness-runtime"}
export LAIN_HARNESS_ROOT="$harness_root"
cd -- "$lain_runtime_root"
exec clojure -J-Xmx3g -Sdeps "{:aliases {:harness {:extra-paths [\"$harness_root/src\"]}}}" -M:harness "$harness_root/tools/check_specification.clj"

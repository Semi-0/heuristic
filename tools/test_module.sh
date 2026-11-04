#!/usr/bin/env bash
set -euo pipefail
harness_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
lain_runtime_root=${LAIN_RUNTIME_ROOT:-"$(dirname -- "$harness_root")/lain-harness-runtime"}
export LAIN_HARNESS_ROOT="$harness_root"
if [ "${1:-list}" = behavior ]; then
  export LAIN_OFFLINE_UUID=${LAIN_OFFLINE_UUID:-1}
fi
cd -- "$lain_runtime_root"
exec clojure -J-Xmx2g -Sdeps "{:aliases {:harness {:extra-paths [\"$harness_root/src\" \"$harness_root/test\" \"test\" \"editor/src\"]}}}" -M:harness "$harness_root/tools/run_module.clj" "${1:-list}"

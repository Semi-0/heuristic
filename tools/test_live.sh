#!/usr/bin/env bash
set -euo pipefail
harness_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
lain_runtime_root=${LAIN_RUNTIME_ROOT:-/home/lain/.local/share/lain-editor-deploy/current/backend}
cd -- "$lain_runtime_root"
exec clojure -J-Xmx2g -Sdeps "{:aliases {:harness-test {:extra-paths [\"$harness_root/src\" \"$harness_root/test\"] :main-opts [\"-m\" \"lain-harness.live-v4-test\"]}}}" -M:harness-test

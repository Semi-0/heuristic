#!/usr/bin/env bash
# Opt-in. Each size/workload gets 120 seconds for setup, warm-up and measurements.
set -euo pipefail
harness_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
lain_runtime_root=${LAIN_RUNTIME_ROOT:-"$(dirname -- "$harness_root")/lain-harness-runtime"}
export LAIN_HARNESS_ROOT="$harness_root"
export LAIN_OFFLINE_UUID=${LAIN_OFFLINE_UUID:-1}
cd -- "$lain_runtime_root"
for size in ${BEHAVIOR_SIZES:-100 1000}; do
  for workload in ${BEHAVIOR_WORKLOADS:-scan composed duplicate recovery window}; do
    set +e
    timeout --signal=TERM --kill-after=5s 120s \
      clojure -J-Xmx2g -Sdeps "{:aliases {:harness {:extra-paths [\"$harness_root/src\" \"$harness_root/test\" \"test\"]}}}" \
      -M:harness "$harness_root/tools/bench_behavior.clj" "$workload" "$size"
    result=$?
    set -e
    if [ "$result" -ne 0 ]; then
      printf '{:workload :%s :size %s :status :unfinished :exit %s}\n' "$workload" "$size" "$result"
    fi
  done
done

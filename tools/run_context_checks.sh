#!/usr/bin/env bash
set -euo pipefail
harness_root=$(cd "$(dirname "$0")/.." && pwd)
runtime_root=${LAIN_RUNTIME_ROOT:-$harness_root/../lain-harness-runtime}
check_root=$(mktemp -d /tmp/lain-context-check.XXXXXX)
cp -R "$harness_root/src" "$harness_root/lib" "$harness_root/test" "$harness_root/environments" "$harness_root/experiments" "$check_root/"
cp "$harness_root/soul-refined.lain" "$harness_root/model-refined.lain" "$check_root/"
mkdir "$check_root/tools"
cp "$harness_root/tools/offline_uuid.clj" "$harness_root/tools/test_context.clj" "$check_root/tools/"
echo "Frozen check source: $check_root"
harness_root=$check_root
cd "$runtime_root"
exec python3 /home/lain/projects/datalog-research/scripts/resource_watchdog.py run \
  --memory-mib 2048 --cpus 2 -- env LAIN_OFFLINE_UUID=1 LAIN_HARNESS_ROOT="$harness_root" \
  clojure -J-Xmx1200m -J-XX:ActiveProcessorCount=2 \
  -Sdeps "{:deps {com.datomic/local {:mvn/version \"1.0.301\"}} :aliases {:context {:extra-paths [\"$harness_root/src\" \"$harness_root/test\"]}}}" \
  -M:context "$harness_root/tools/test_context.clj" "$@"

#!/usr/bin/env bash
set -euo pipefail
harness_root=$(cd "$(dirname "$0")/.." && pwd)
experiment_root=$(mktemp -d /tmp/lain-printshop-diagnostics.XXXXXX)
runtime_root=${LAIN_RUNTIME_ROOT:-"$(dirname "$harness_root")/lain-harness-runtime"}
evidence_root=${LAIN_EVIDENCE_DIR:?Set a fresh evidence directory}
if [ -e "$evidence_root" ]; then echo 'Evidence directory already exists' >&2; exit 1; fi
mkdir -p "$evidence_root"
git -C "$harness_root" archive HEAD | tar -x -C "$experiment_root"
cp "$harness_root/examples/lain/printshop-diagnostics.lain" "$experiment_root/soul-refined.lain"
cp "$harness_root/tools/probe_printshop_diagnostics.clj" "$experiment_root/tools/probe_printshop_diagnostics.clj"
cp "$harness_root/tools/printshop_diagnostics.clj" "$experiment_root/tools/printshop_diagnostics.clj"
mkdir -p "$experiment_root/test/fixtures/printshop"
cp "$harness_root/test/fixtures/printshop/quote.lain" "$experiment_root/test/fixtures/printshop/quote.lain"
printf '%s\n' "$experiment_root" > "$evidence_root/isolated-root.txt"
git -C "$harness_root" rev-parse HEAD > "$evidence_root/harness-head.txt"
git -C "$runtime_root" rev-parse HEAD > "$evidence_root/runtime-head.txt"
case "${1:-control}" in control) export LAIN_CONTROL=control;; live) export LAIN_CONTROL=;; *) exit 2;; esac
export LAIN_HARNESS_ROOT="$experiment_root" LAIN_OFFLINE_UUID=1 LAIN_EVIDENCE_DIR="$evidence_root"
cd "$runtime_root"
clojure -J-Xmx2g -Sdeps "{:aliases {:experiment {:extra-paths [\"$experiment_root/src\" \"editor/src\"]}}}" -M:experiment "$experiment_root/tools/probe_printshop_diagnostics.clj" > "$evidence_root/run.log" 2>&1

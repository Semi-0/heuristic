#!/usr/bin/env bash
set -euo pipefail
harness_root=$(cd "$(dirname "$0")/.." && pwd)
experiment_root=$(mktemp -d /tmp/lain-printshop-three-stage.XXXXXX)
runtime_root=${LAIN_RUNTIME_ROOT:-"$(dirname "$harness_root")/lain-harness-runtime"}
evidence_root=${LAIN_EVIDENCE_DIR:?Set a fresh evidence directory}
if [ -e "$evidence_root" ]; then echo 'Evidence directory already exists' >&2; exit 1; fi
mkdir -p "$evidence_root"
git -C "$harness_root" archive HEAD src lib tools model-refined.lain | tar -x -C "$experiment_root"
cp "$harness_root/examples/lain/printshop-three-stage.lain" "$experiment_root/soul-refined.lain"
cp "$harness_root/tools/probe_printshop_three_stage.clj" "$experiment_root/tools/probe_printshop_three_stage.clj"
mkdir -p "$experiment_root/test/fixtures/printshop"
cp "$harness_root/test/fixtures/printshop/quote.lain" "$experiment_root/test/fixtures/printshop/quote.lain"
printf '%s\n' "$experiment_root" > "$evidence_root/isolated-root.txt"
git -C "$harness_root" rev-parse HEAD > "$evidence_root/harness-head.txt"
git -C "$runtime_root" rev-parse HEAD > "$evidence_root/runtime-head.txt"
case "${1:-control}" in control) export LAIN_CONTROL=control;; live) export LAIN_CONTROL=;; *) exit 2;; esac
export LAIN_HARNESS_ROOT="$experiment_root" LAIN_OFFLINE_UUID=1 LAIN_EVIDENCE_DIR="$evidence_root"
cd "$runtime_root"
clojure -J-Xmx2g -Sdeps "{:aliases {:experiment {:extra-paths [\"$experiment_root/src\" \"editor/src\"]}}}" -M:experiment "$experiment_root/tools/probe_printshop_three_stage.clj" > "$evidence_root/run.log" 2>&1

cp "$harness_root/tools/evaluate_printshop_three_stage.clj" "$evidence_root/evaluator.clj"
cp "$harness_root/tools/probe_printshop_three_stage.clj" "$evidence_root/probe.clj"
cp "$harness_root/tools/run_printshop_three_stage.sh" "$evidence_root/runner.sh"
cp "$harness_root/test/fixtures/printshop/quote.lain" "$evidence_root/controlled-source.lain"
git -C "$runtime_root" diff --stat > "$evidence_root/runtime-local-changes.txt"
clojure -M "$harness_root/tools/evaluate_printshop_three_stage.clj" "$evidence_root" > "$evidence_root/evaluation.log" 2>&1

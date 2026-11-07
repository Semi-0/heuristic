#!/usr/bin/env bash
set -euo pipefail
harness_root=$(cd "$(dirname "$0")/.." && pwd)
experiment_root=$(mktemp -d /tmp/lain-partial-inventory.XXXXXX)
runtime_root=${LAIN_RUNTIME_ROOT:-"$(dirname "$harness_root")/lain-harness-runtime"}
evidence_root=${LAIN_EVIDENCE_DIR:-"$harness_root/runtime/inspection-inventory-$(date +%Y%m%d-%H%M%S)"}
mkdir -p "$evidence_root"
git -C "$harness_root" archive HEAD src lib tools model-refined.lain | tar -x -C "$experiment_root"
cp "$harness_root/examples/lain/inspection-inventory.lain" "$experiment_root/soul-refined.lain"
cp "$harness_root/tools/probe_partial_inventory.clj" "$experiment_root/tools/probe_partial_inventory.clj"
cp "$harness_root/tools/evaluate_partial_inventory.clj" "$experiment_root/tools/evaluate_partial_inventory.clj"
printf '%s\n' "$experiment_root" > "$evidence_root/isolated-root.txt"
git -C "$harness_root" rev-parse HEAD > "$evidence_root/harness-head.txt"
git -C "$runtime_root" rev-parse HEAD > "$evidence_root/runtime-head.txt"
git -C "$runtime_root" diff --stat > "$evidence_root/runtime-local-changes.txt"
cp "$harness_root/lib/inspection-context.lain" "$experiment_root/lib/"
cp "$harness_root/src/lain_harness/inspection_experiment.clj" "$experiment_root/src/lain_harness/"
cp "$harness_root/examples/lain/inspection-inventory.lain" "$evidence_root/parent.lain"
cp "$harness_root/lib/inspection-context.lain" "$evidence_root/inspection-context.lain"
cp "$harness_root/src/lain_harness/inspection_experiment.clj" "$evidence_root/inspection_experiment.clj"
cp "$harness_root/tools/probe_partial_inventory.clj" "$evidence_root/probe.clj"
cp "$harness_root/tools/evaluate_partial_inventory.clj" "$evidence_root/evaluator.clj"
export LAIN_INSPECTION=1
export LAIN_HARNESS_ROOT="$experiment_root" LAIN_OFFLINE_UUID=1
cd "$runtime_root"
run_trial() {
  local label=$1 control=$2
  if [ -e "$evidence_root/$label" ]; then
    echo "Refusing to overwrite trial evidence: $evidence_root/$label" >&2
    return 1
  fi
  mkdir -p "$evidence_root/$label"
  LAIN_EVIDENCE_DIR="$evidence_root/$label" LAIN_CONTROL="$control" \
    clojure -J-Xmx2g -Sdeps "{:aliases {:experiment {:extra-paths [\"$experiment_root/src\" \"editor/src\"]}}}" \
    -M:experiment "$experiment_root/tools/probe_partial_inventory.clj" > "$evidence_root/$label/run.log" 2>&1 || {
      printf 'Process failed; evidence retained: %s\n' "$label" >&2
      printf 'process-failed\n' > "$evidence_root/$label/process-failure.txt"
    }
}
case "${1:-controls}" in
  controls) for mode in connected disconnected reuse copied cross; do run_trial "control-$mode" "$mode"; done ;;
  live) for i in 1 2 3; do run_trial "trial-$i" ""; done ;;
  replay)
    : "${LAIN_REPLAY_SOURCE_DIR:?Set the evidence root containing trial-1 through trial-3}"
    replay_source_root=$LAIN_REPLAY_SOURCE_DIR
    for i in 1 2 3; do LAIN_REPLAY_SOURCE_DIR="$replay_source_root/trial-$i" run_trial "trial-$i" replay; done ;;
  *) echo 'Usage: run_inspection_inventory.sh [controls|live|replay]' >&2; exit 2 ;;
esac
clojure -M "$experiment_root/tools/evaluate_partial_inventory.clj" "$evidence_root" > "$evidence_root/evaluation.log" 2>&1
printf 'Evidence: %s\n'  "$evidence_root"

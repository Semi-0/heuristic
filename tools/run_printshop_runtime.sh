#!/usr/bin/env bash
set -euo pipefail
harness_root=$(cd "$(dirname "$0")/.." && pwd)
experiment_root=$(mktemp -d /tmp/lain-printshop-three-stage.XXXXXX)
runtime_root=${LAIN_RUNTIME_ROOT:-"$(dirname "$harness_root")/lain-harness-runtime"}
evidence_root=${LAIN_EVIDENCE_DIR:?Set a fresh evidence directory}
if [ -e "$evidence_root" ]; then echo 'Evidence directory already exists' >&2; exit 1; fi
mkdir -p "$evidence_root"
git -C "$harness_root" archive HEAD src lib tools model-refined.lain | tar -x -C "$experiment_root"
cp -R "$harness_root/src/lain_harness/." "$experiment_root/src/lain_harness/"
cp "$harness_root/soul-refined.lain" "$experiment_root/soul-refined.lain"
cp "$harness_root/lib/compiler.lain" "$experiment_root/lib/compiler.lain"
cp "$harness_root/lib/compiler-streaming.lain" "$experiment_root/lib/compiler-streaming.lain"
mkdir -p "$experiment_root/environments" "$experiment_root/experiments" "$experiment_root/examples/lain"
cp "$harness_root/environments/printshop-three-stage.lain" "$experiment_root/environments/"
cp "$harness_root/environments/repair-guidance.lain" "$experiment_root/environments/"
cp "$harness_root/experiments/phase-orchestration.lain" "$experiment_root/experiments/"
cp "$harness_root/src/lain_harness/soul_session.clj" "$experiment_root/src/lain_harness/"
cp "$harness_root/src/lain_harness/environment.clj" "$experiment_root/src/lain_harness/"
cp "$harness_root/src/lain_harness/lexical_observation.clj" "$experiment_root/src/lain_harness/"
cp "$harness_root/src/lain_harness/experiment_observation.clj" "$experiment_root/src/lain_harness/"
cp "$harness_root/experiments/printshop-three-stage-requests.lain" "$experiment_root/experiments/"
cp "$harness_root/examples/lain/printshop-runtime.lain" "$experiment_root/examples/lain/"
export LAIN_EXPERIMENT_ENTRY=examples/lain/printshop-runtime.lain
cp "$harness_root/tools/probe_printshop_runtime.clj" "$experiment_root/tools/probe_printshop_runtime.clj"
mkdir -p "$experiment_root/test/fixtures/printshop"
cp "$harness_root/test/fixtures/printshop/quote.lain" "$experiment_root/test/fixtures/printshop/quote.lain"
printf '%s\n' "$experiment_root" > "$evidence_root/isolated-root.txt"
git -C "$harness_root" rev-parse HEAD > "$evidence_root/harness-head.txt"
git -C "$runtime_root" rev-parse HEAD > "$evidence_root/runtime-head.txt"
case "${1:-control}" in control) export LAIN_CONTROL=control;; recovery-control) export LAIN_CONTROL=recovery-control;; live) export LAIN_CONTROL=;; replay) : "${LAIN_REPLAY_SOURCE_DIR:?Set the original real-provider evidence directory}"; export LAIN_CONTROL=replay;; *) exit 2;; esac
export LAIN_HARNESS_ROOT="$experiment_root" LAIN_OFFLINE_UUID=1 LAIN_EVIDENCE_DIR="$evidence_root"
cd "$runtime_root"
clojure -J-Xmx2g -Sdeps "{:aliases {:experiment {:extra-paths [\"$experiment_root/src\" \"editor/src\"]}}}" -M:experiment "$experiment_root/tools/probe_printshop_runtime.clj" > "$evidence_root/run.log" 2>&1

cp "$harness_root/tools/evaluate_printshop_runtime.clj" "$evidence_root/evaluator.clj"
cp "$experiment_root/tools/probe_printshop_runtime.clj" "$evidence_root/probe.clj"
cp "$harness_root/tools/run_printshop_runtime.sh" "$evidence_root/runner.sh"
cp "$harness_root/test/fixtures/printshop/quote.lain" "$evidence_root/controlled-source.lain"
git -C "$runtime_root" diff --stat > "$evidence_root/runtime-local-changes.txt"
git -C "$runtime_root" diff --binary > "$evidence_root/runtime-local-changes.patch"
clojure -M "$harness_root/tools/evaluate_printshop_runtime.clj" "$evidence_root" > "$evidence_root/evaluation.log" 2>&1

cp "$experiment_root/soul-refined.lain" "$evidence_root/runtime.lain"
cp "$experiment_root/lib/compiler.lain" "$evidence_root/compiler.lain"
cp "$experiment_root/environments/printshop-three-stage.lain" "$evidence_root/environment.lain"
cp "$experiment_root/experiments/printshop-three-stage-requests.lain" "$evidence_root/requests.lain"
cp "$experiment_root/examples/lain/printshop-runtime.lain" "$evidence_root/entry.lain"

cp "$experiment_root/src/lain_harness/experiment_observation.clj" "$evidence_root/observation-adapters.clj"

cp "$experiment_root/experiments/phase-orchestration.lain" "$evidence_root/phase-orchestration.lain"
cp "$experiment_root/src/lain_harness/soul_session.clj" "$evidence_root/admission-adapter.clj"

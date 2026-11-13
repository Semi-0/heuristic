#!/usr/bin/env bash
set -euo pipefail
harness_root=$(cd "$(dirname "$0")/.." && pwd)
runtime_root=${LAIN_RUNTIME_ROOT:-"$harness_root/../lain-harness-runtime"}
if [ -n "${LAIN_ACCUMULATED_TRIAL:-}" ]; then
  LAIN_SAVED_DATABASE="$LAIN_ACCUMULATED_TRIAL/database"
fi
: "${LAIN_SAVED_DATABASE:?Set the closed cumulative experiment database directory}"
: "${LAIN_EVIDENCE_DIR:?Set a fresh absolute evidence directory}"
if [ -e "$LAIN_EVIDENCE_DIR" ]; then echo 'Evidence directory already exists' >&2; exit 1; fi
mkdir -p "$LAIN_EVIDENCE_DIR"
# The old database is closed. Work on a copy; preserve its recorded evidence.
cp -R "$LAIN_SAVED_DATABASE" "$LAIN_EVIDENCE_DIR/database"
if [ -n "${LAIN_ACCUMULATED_TRIAL:-}" ]; then
  cp "$LAIN_ACCUMULATED_TRIAL/accumulate-result.edn" "$LAIN_EVIDENCE_DIR/accumulate-result.edn"
  printf '%s\n' "$LAIN_ACCUMULATED_TRIAL" > "$LAIN_EVIDENCE_DIR/producer-trial.txt"
fi
experiment_root=$(mktemp -d /tmp/lain-directed.XXXXXX)
cp -R "$harness_root/src" "$harness_root/lib" "$harness_root/environments" "$harness_root/experiments" "$experiment_root/"
cp "$harness_root/soul-refined.lain" "$harness_root/model-refined.lain" "$experiment_root/"
mkdir -p "$experiment_root/tools"
cp "$harness_root/tools/offline_uuid.clj" "$harness_root/tools/probe_catalog_directed.clj" "$experiment_root/tools/"
export LAIN_HARNESS_ROOT="$experiment_root" LAIN_OFFLINE_UUID=1
printf '%s\n' "$experiment_root" > "$LAIN_EVIDENCE_DIR/isolated-root.txt"
git -C "$runtime_root" rev-parse HEAD > "$LAIN_EVIDENCE_DIR/runtime-head.txt"
git -C "$harness_root" rev-parse HEAD > "$LAIN_EVIDENCE_DIR/harness-head.txt"
cp "$harness_root/tools/probe_catalog_directed.clj" "$LAIN_EVIDENCE_DIR/probe.clj"
cp "$harness_root/src/lain_harness/source_restore.clj" "$LAIN_EVIDENCE_DIR/source-restore.clj"
cp "$harness_root/environments/catalog-directed.lain" "$LAIN_EVIDENCE_DIR/environment.lain"
cp "$harness_root/experiments/catalog-directed.lain" "$LAIN_EVIDENCE_DIR/orchestration.lain"
cp "$harness_root/src/lain_harness/catalog_selection.clj" "$LAIN_EVIDENCE_DIR/catalog-selection.clj"
cd "$runtime_root"
# Distinct JVMs: the first command must exit successfully before the second starts.
if [ -z "${LAIN_ACCUMULATED_TRIAL:-}" ]; then
  clojure -J-Xmx2g -Sdeps "{:deps {com.datomic/local {:mvn/version \"1.0.301\"}} :aliases {:durable {:extra-paths [\"$experiment_root/src\"]}}}" -M:durable "$experiment_root/tools/probe_catalog_directed.clj" accumulate > "$LAIN_EVIDENCE_DIR/accumulate.log" 2>&1
fi
clojure -J-Xmx2g -Sdeps "{:deps {com.datomic/local {:mvn/version \"1.0.301\"}} :aliases {:durable {:extra-paths [\"$experiment_root/src\"]}}}" -M:durable "$experiment_root/tools/probe_catalog_directed.clj" reuse > "$LAIN_EVIDENCE_DIR/reuse.log" 2>&1

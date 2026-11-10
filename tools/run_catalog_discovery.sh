#!/usr/bin/env bash
set -euo pipefail
harness_root=$(cd "$(dirname "$0")/.." && pwd)
export LAIN_OFFLINE_UUID=1
: "${LAIN_EVIDENCE_DIR:?Set a fresh evidence directory}"
if [ -e "$LAIN_EVIDENCE_DIR" ]; then echo 'Evidence directory already exists' >&2; exit 1; fi
mkdir -p "$LAIN_EVIDENCE_DIR"
experiment_root=$(mktemp -d /tmp/lain-catalog.XXXXXX)
cp -R "$harness_root/src" "$harness_root/lib" "$harness_root/experiments" "$harness_root/environments" "$experiment_root/"
cp "$harness_root/soul-refined.lain" "$harness_root/model-refined.lain" "$experiment_root/"
mkdir -p "$experiment_root/tools" "$experiment_root/test/fixtures/catalog"
cp "$harness_root/test/fixtures/catalog/library.lain" "$experiment_root/test/fixtures/catalog/"
cp "$harness_root/tools/offline_uuid.clj" "$harness_root/tools/probe_catalog_discovery.clj" "$experiment_root/tools/"
export LAIN_HARNESS_ROOT="$experiment_root"
printf '%s\n' "$experiment_root" > "$LAIN_EVIDENCE_DIR/isolated-root.txt"
git -C "$harness_root" rev-parse HEAD > "$LAIN_EVIDENCE_DIR/harness-head.txt"
git -C "$harness_root/../lain-harness-runtime" rev-parse HEAD > "$LAIN_EVIDENCE_DIR/runtime-head.txt"
cp "$harness_root/environments/catalog-discovery.lain" "$LAIN_EVIDENCE_DIR/context-source.lain"
cp "$harness_root/experiments/catalog-common.lain" "$LAIN_EVIDENCE_DIR/orchestration.lain"
cp "$harness_root/environments/catalog-cumulative.lain" "$LAIN_EVIDENCE_DIR/cumulative-context-source.lain"
cp "$harness_root/experiments/catalog-cumulative.lain" "$LAIN_EVIDENCE_DIR/cumulative-orchestration.lain"
cp "$harness_root/src/lain_harness/catalog.clj" "$LAIN_EVIDENCE_DIR/catalog.clj"
cp "$harness_root/tools/probe_catalog_discovery.clj" "$LAIN_EVIDENCE_DIR/probe.clj"
cd "${LAIN_RUNTIME_ROOT:-$harness_root/../lain-harness-runtime}"
clojure -J-Xmx2g -Sdeps "{:deps {com.datomic/local {:mvn/version \"1.0.301\"}} :aliases {:discovery {:extra-paths [\"$experiment_root/src\"]}}}" -M:discovery "$experiment_root/tools/probe_catalog_discovery.clj" > "$LAIN_EVIDENCE_DIR/run.log" 2>&1

#!/usr/bin/env bash
set -euo pipefail
harness_root=$(cd "$(dirname "$0")/.." && pwd)
: "${LAIN_EVIDENCE_DIR:?Set fresh evidence directory}"
if [ -e "$LAIN_EVIDENCE_DIR" ]; then echo 'Evidence path already exists' >&2; exit 1; fi
mkdir -p "$LAIN_EVIDENCE_DIR"
cp -R "$harness_root/src" "$harness_root/lib" "$harness_root/environments" "$harness_root/experiments" "$harness_root/tools" "$LAIN_EVIDENCE_DIR/"
cp "$harness_root/soul-refined.lain" "$harness_root/model-refined.lain" "$LAIN_EVIDENCE_DIR/"
git -C "$harness_root" rev-parse HEAD > "$LAIN_EVIDENCE_DIR/harness-head.txt"
git -C "$harness_root/../lain-harness-runtime" rev-parse HEAD > "$LAIN_EVIDENCE_DIR/runtime-head.txt"
python3 - "$LAIN_EVIDENCE_DIR" <<'SNAPSHOT'
import hashlib, json, pathlib, sys
root = pathlib.Path(sys.argv[1])
manifest = {str(p.relative_to(root)): hashlib.sha256(p.read_bytes()).hexdigest()
            for p in sorted(root.rglob("*")) if p.is_file()}
(root / "source-sha256.json").write_text(json.dumps(manifest, indent=2) + "\n")
SNAPSHOT
cd "${LAIN_RUNTIME_ROOT:-$harness_root/../lain-harness-runtime}"
exec python3 /home/lain/projects/datalog-research/scripts/resource_watchdog.py run \
 --memory-mib 3072 --cpus 2 -- env LAIN_OFFLINE_UUID=1 LAIN_HARNESS_ROOT="$LAIN_EVIDENCE_DIR" \
 LAIN_EVIDENCE_DIR="$LAIN_EVIDENCE_DIR" LAIN_CONTEXT_MODE="${LAIN_CONTEXT_MODE:-prepare}" \
 LAIN_CONTEXT_PREPARATION="${LAIN_CONTEXT_PREPARATION:-}" \
 LAIN_TOKENIZER_VERIFICATION="${LAIN_TOKENIZER_VERIFICATION:-}" \
 LAIN_CONTEXT_LAST_TASK="${LAIN_CONTEXT_LAST_TASK:-4}" \
 LAIN_CONTEXT_ABLATION="${LAIN_CONTEXT_ABLATION:-1}" \
 clojure -J-Xmx1900m -J-XX:ActiveProcessorCount=2 \
 -Sdeps "{:deps {com.datomic/local {:mvn/version \"1.0.301\"}} :aliases {:context {:extra-paths [\"$LAIN_EVIDENCE_DIR/src\"]}}}" \
 -M:context "$LAIN_EVIDENCE_DIR/tools/${LAIN_CONTEXT_PROBE:-probe_context.clj}"

#!/usr/bin/env bash
set -euo pipefail
if (( $# < 2 || $# > 4 )); then
  echo "Usage: $0 MODE EVIDENCE_DIR [MAX_TOKENS [HTTP_TIMEOUT_MS]]" >&2
  exit 2
fi
case "$1" in
  control|live|repair-cli|guided-repair-cli|remaining-repair) ;;
  *) echo "Unknown experiment mode: $1" >&2; exit 2 ;;
esac
export LAIN_HARNESS_ROOT
LAIN_HARNESS_ROOT=$(cd "$(dirname "$0")/../.." && pwd)
runtime=${LAIN_RUNTIME_ROOT:-"$LAIN_HARNESS_ROOT/../lain-harness-runtime"}
mode=$1
mkdir -p "$2"
evidence=$(cd "$2" && pwd)
shift 2
cd "$runtime"
exec clojure -J-Xmx1200m -J-XX:ActiveProcessorCount=1 -Sdeps "{
 :deps {io.github.Semi-0/lain-compiler {:git/url \"https://github.com/Semi-0/lain-compiler.git\" :git/sha \"343f08d1241b0e23549c0425dcdae9f7a0d8bff0\"}
        com.datomic/local {:mvn/version \"1.0.301\"}
        org.clojure/data.json {:mvn/version \"2.5.2\"}}
 :aliases {:attempt {:extra-paths [\"$LAIN_HARNESS_ROOT/src\"]}}}" \
 -M:attempt "$LAIN_HARNESS_ROOT/experiments/attempt-cli/probe.clj" "$mode" "$evidence" "$@"

#!/usr/bin/env bash
set -euo pipefail
export LAIN_HARNESS_ROOT
LAIN_HARNESS_ROOT=$(cd "$(dirname "$0")/../.." && pwd)
export LAIN_OFFLINE_UUID=1
cd "${LAIN_RUNTIME_ROOT:-$LAIN_HARNESS_ROOT/../lain-harness-runtime}"
exec clojure -J-Xmx1300m -J-XX:ActiveProcessorCount=2 \
  -Sdeps "{:deps {io.github.Semi-0/lain-compiler {:git/url \"https://github.com/Semi-0/lain-compiler.git\" :git/sha \"343f08d1241b0e23549c0425dcdae9f7a0d8bff0\"} com.datomic/local {:mvn/version \"1.0.301\"}} :aliases {:dependency-context {:extra-paths [\"$LAIN_HARNESS_ROOT/src\"]}}}" \
  -M:dependency-context "$LAIN_HARNESS_ROOT/experiments/dependency-context/probe.clj" "$@"

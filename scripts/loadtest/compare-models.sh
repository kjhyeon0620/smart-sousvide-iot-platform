#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

CONN="${1:-1000}"
PART="${2:-2}"
PARA="${3:-80}"
MPS="${4:-1}"
DUR="${5:-60}"
QOS="${6:-1}"

run_model() {
  local model="$1"
  local task="$2"

  echo "[COMPARE] model=${model} task=${task}"
  SIM_TASK="$task" MAX_ATTEMPTS=3 MIN_PARALLELISM=30 MAX_PARTITIONS=8 \
    ./scripts/loadtest/run-distributed.sh "$CONN" "$PART" "$PARA" "$MPS" "$DUR" "$QOS"
}

run_model "paho" "mqttLoadTest"
run_model "hivemq" "mqttLoadTestHive"

echo "[COMPARE] completed (check latest docs/loadtest-runs/* logs)"

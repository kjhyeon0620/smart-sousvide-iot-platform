#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

TOTAL_CONNECTIONS="${1:-2500}"
PARTITIONS="${2:-2}"
PARALLELISM="${3:-120}"
MPS="${4:-1}"
DURATION="${5:-60}"
QOS="${6:-1}"

if (( PARTITIONS < 1 )); then
  echo "PARTITIONS must be >= 1" >&2
  exit 1
fi

if (( TOTAL_CONNECTIONS < PARTITIONS )); then
  echo "TOTAL_CONNECTIONS must be >= PARTITIONS" >&2
  exit 1
fi

RUN_ID="$(date +%Y%m%d-%H%M%S)"
OUT_DIR="docs/loadtest-runs/${RUN_ID}"
mkdir -p "$OUT_DIR"

BASE=$(( TOTAL_CONNECTIONS / PARTITIONS ))
REM=$(( TOTAL_CONNECTIONS % PARTITIONS ))
START=0

PIDS=()

for (( p=0; p<PARTITIONS; p++ )); do
  CONN="$BASE"
  if (( p < REM )); then
    CONN=$(( CONN + 1 ))
  fi

  LOG_FILE="$OUT_DIR/part-${p}.log"

  CMD=(
    ./gradlew mqttLoadTest
    --args="--connections=${CONN} --start-index=${START} --connect-parallelism=${PARALLELISM} --messages-per-second=${MPS} --duration-seconds=${DURATION} --qos=${QOS} --client-prefix=sim-p${p}"
  )

  echo "[DIST] start part=${p} connections=${CONN} startIndex=${START} log=${LOG_FILE}"
  "${CMD[@]}" > "$LOG_FILE" 2>&1 &
  PIDS+=("$!")

  START=$(( START + CONN ))
done

FAIL=0
for PID in "${PIDS[@]}"; do
  if ! wait "$PID"; then
    FAIL=1
  fi
done

if (( FAIL != 0 )); then
  echo "[DIST] one or more partitions failed" >&2
  "$ROOT_DIR/scripts/loadtest/summarize-results.sh" "$OUT_DIR" || true
  exit 1
fi

"$ROOT_DIR/scripts/loadtest/summarize-results.sh" "$OUT_DIR"
echo "[DIST] completed run_id=${RUN_ID}"

#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <run_dir>" >&2
  exit 1
fi

RUN_DIR="$1"
if [[ ! -d "$RUN_DIR" ]]; then
  echo "Run directory not found: $RUN_DIR" >&2
  exit 1
fi

PUBLISHED=0
FAILED=0
THROUGHPUT=0
PARTS=0

for LOG in "$RUN_DIR"/part-*.log; do
  [[ -f "$LOG" ]] || continue
  PARTS=$(( PARTS + 1 ))

  PUB_LINE="$(grep -E "\[(SIM|SIM-HIVE)\] published=" "$LOG" | tail -n1 || true)"
  FAIL_LINE="$(grep -E "\[(SIM|SIM-HIVE)\] failed=" "$LOG" | tail -n1 || true)"
  TPS_LINE="$(grep -E "\[(SIM|SIM-HIVE)\] throughput\(msg/sec\)=" "$LOG" | tail -n1 || true)"

  PUB="${PUB_LINE##*=}"
  FLD="${FAIL_LINE##*=}"
  TPS="${TPS_LINE##*=}"

  PUB="${PUB:-0}"
  FLD="${FLD:-0}"
  TPS="${TPS:-0}"

  PUBLISHED=$(( PUBLISHED + PUB ))
  FAILED=$(( FAILED + FLD ))
  THROUGHPUT="$(awk -v a="$THROUGHPUT" -v b="$TPS" 'BEGIN { printf "%.2f", a + b }')"

done

echo "[DIST] parts=${PARTS}"
echo "[DIST] published_total=${PUBLISHED}"
echo "[DIST] failed_total=${FAILED}"
echo "[DIST] throughput_total(msg/sec)=${THROUGHPUT}"
echo "[DIST] logs_dir=${RUN_DIR}"

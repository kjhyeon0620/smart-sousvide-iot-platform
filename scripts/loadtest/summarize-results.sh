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

RUN_ID="$(basename "$(dirname "$RUN_DIR")")"
ATTEMPT_NAME="$(basename "$RUN_DIR")"
ATTEMPT="${ATTEMPT_NAME#attempt-}"
SUMMARY_FILE="${RUN_DIR}/connection-summary.json"

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

ATTEMPTED=$(( PUBLISHED + FAILED ))
SUCCESS_RATE="$(awk -v p="$PUBLISHED" -v t="$ATTEMPTED" 'BEGIN { if (t == 0) { printf "0.0000" } else { printf "%.4f", p / t } }')"
SUCCESS_RATE_PCT="$(awk -v r="$SUCCESS_RATE" 'BEGIN { printf "%.2f", r * 100 }')"

cat > "$SUMMARY_FILE" <<EOF
{
  "run_id": "${RUN_ID}",
  "attempt": "${ATTEMPT}",
  "parts": ${PARTS},
  "published_total": ${PUBLISHED},
  "failed_total": ${FAILED},
  "attempted_total": ${ATTEMPTED},
  "success_rate": ${SUCCESS_RATE},
  "success_rate_pct": ${SUCCESS_RATE_PCT},
  "throughput_total_msg_per_sec": ${THROUGHPUT},
  "success_rate_formula": "published_total / (published_total + failed_total)"
}
EOF

echo "[DIST] parts=${PARTS}"
echo "[DIST] published_total=${PUBLISHED}"
echo "[DIST] failed_total=${FAILED}"
echo "[DIST] throughput_total(msg/sec)=${THROUGHPUT}"
echo "[DIST] connection_success_rate=${SUCCESS_RATE_PCT}%"
echo "[DIST] logs_dir=${RUN_DIR}"
echo "[DIST] connection_summary=${SUMMARY_FILE}"

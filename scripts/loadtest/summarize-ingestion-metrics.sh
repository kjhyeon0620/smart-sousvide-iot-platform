#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 || $# -gt 2 ]]; then
  echo "Usage: $0 <attempt_dir> [backend_log]" >&2
  exit 1
fi

ATTEMPT_DIR="$1"
if [[ ! -d "$ATTEMPT_DIR" ]]; then
  echo "Attempt directory not found: $ATTEMPT_DIR" >&2
  exit 1
fi

BACKEND_LOG="${2:-$ATTEMPT_DIR/backend.log}"
if [[ ! -f "$BACKEND_LOG" ]]; then
  echo "Backend log not found: $BACKEND_LOG" >&2
  exit 1
fi

RUN_ID="$(basename "$(dirname "$ATTEMPT_DIR")")"
ATTEMPT_NAME="$(basename "$ATTEMPT_DIR")"
ATTEMPT="${ATTEMPT_NAME#attempt-}"
SUMMARY_FILE="${ATTEMPT_DIR}/business-summary.json"

METRICS_LINE="$(grep -E "\[INGEST-METRICS/1s\].*\|[[:space:]]*totals" "$BACKEND_LOG" | tail -n1 || true)"
if [[ -z "$METRICS_LINE" ]]; then
  echo "No [INGEST-METRICS/1s] totals line found in: $BACKEND_LOG" >&2
  exit 1
fi
TOTALS_SEGMENT="$(echo "$METRICS_LINE" | sed -E 's/^.*\|[[:space:]]*totals[[:space:]]*//' | tr ',' ' ')"

extract_total() {
  local key="$1"
  local segment="$2"
  local value
  value="$(
    echo "$segment" \
      | grep -oE "(^|[[:space:]])${key}[[:space:]]*=[[:space:]]*[^[:space:]]+" \
      | tail -n1 \
      | sed -E 's/^.*=[[:space:]]*//' || true
  )"

  if [[ -z "$value" || ! "$value" =~ ^[0-9]+$ ]]; then
    return 1
  fi

  echo "$value"
}

extract_total_optional() {
  local key="$1"
  local segment="$2"
  local default_value="$3"
  local value

  value="$(extract_total "$key" "$segment" || true)"
  if [[ -z "$value" ]]; then
    echo "$default_value"
    return 0
  fi

  echo "$value"
}

if ! RECV_TOTAL="$(extract_total "recv" "$TOTALS_SEGMENT")" \
  || ! PARSE_OK_TOTAL="$(extract_total "parseOk" "$TOTALS_SEGMENT")" \
  || ! PARSE_FAIL_TOTAL="$(extract_total "parseFail" "$TOTALS_SEGMENT")" \
  || ! INFLUX_OK_TOTAL="$(extract_total "influxOk" "$TOTALS_SEGMENT")" \
  || ! INFLUX_FAIL_TOTAL="$(extract_total "influxFail" "$TOTALS_SEGMENT")" \
  || ! REDIS_OK_TOTAL="$(extract_total "redisOk" "$TOTALS_SEGMENT")" \
  || ! REDIS_FAIL_TOTAL="$(extract_total "redisFail" "$TOTALS_SEGMENT")"; then
  echo "Failed to parse totals from backend log line: $METRICS_LINE" >&2
  exit 1
fi

INFLUX_BYPASS_TOTAL="$(extract_total_optional "influxBypass" "$TOTALS_SEGMENT" "0")"

PIPELINE_SUCCESS_TOTAL="$PARSE_OK_TOTAL"
if (( INFLUX_OK_TOTAL < PIPELINE_SUCCESS_TOTAL )); then
  PIPELINE_SUCCESS_TOTAL="$INFLUX_OK_TOTAL"
fi
if (( REDIS_OK_TOTAL < PIPELINE_SUCCESS_TOTAL )); then
  PIPELINE_SUCCESS_TOTAL="$REDIS_OK_TOTAL"
fi

PIPELINE_FAILURE_TOTAL=$(( RECV_TOTAL - PIPELINE_SUCCESS_TOTAL ))
if (( PIPELINE_FAILURE_TOTAL < 0 )); then
  PIPELINE_FAILURE_TOTAL=0
fi

PIPELINE_SUCCESS_RATE="$(awk -v ok="$PIPELINE_SUCCESS_TOTAL" -v recv="$RECV_TOTAL" 'BEGIN { if (recv == 0) { printf "0.0000" } else { printf "%.4f", ok / recv } }')"
PIPELINE_SUCCESS_RATE_PCT="$(awk -v r="$PIPELINE_SUCCESS_RATE" 'BEGIN { printf "%.2f", r * 100 }')"

CORE_PIPELINE_SUCCESS_TOTAL="$PARSE_OK_TOTAL"
if (( REDIS_OK_TOTAL < CORE_PIPELINE_SUCCESS_TOTAL )); then
  CORE_PIPELINE_SUCCESS_TOTAL="$REDIS_OK_TOTAL"
fi

CORE_PIPELINE_FAILURE_TOTAL=$(( RECV_TOTAL - CORE_PIPELINE_SUCCESS_TOTAL ))
if (( CORE_PIPELINE_FAILURE_TOTAL < 0 )); then
  CORE_PIPELINE_FAILURE_TOTAL=0
fi

CORE_PIPELINE_SUCCESS_RATE="$(awk -v ok="$CORE_PIPELINE_SUCCESS_TOTAL" -v recv="$RECV_TOTAL" 'BEGIN { if (recv == 0) { printf "0.0000" } else { printf "%.4f", ok / recv } }')"
CORE_PIPELINE_SUCCESS_RATE_PCT="$(awk -v r="$CORE_PIPELINE_SUCCESS_RATE" 'BEGIN { printf "%.2f", r * 100 }')"

cat > "$SUMMARY_FILE" <<EOF
{
  "run_id": "${RUN_ID}",
  "attempt": "${ATTEMPT}",
  "recv_total": ${RECV_TOTAL},
  "parse_ok_total": ${PARSE_OK_TOTAL},
  "parse_fail_total": ${PARSE_FAIL_TOTAL},
  "influx_ok_total": ${INFLUX_OK_TOTAL},
  "influx_fail_total": ${INFLUX_FAIL_TOTAL},
  "influx_bypass_total": ${INFLUX_BYPASS_TOTAL},
  "redis_ok_total": ${REDIS_OK_TOTAL},
  "redis_fail_total": ${REDIS_FAIL_TOTAL},
  "pipeline_success_total": ${PIPELINE_SUCCESS_TOTAL},
  "pipeline_failure_total": ${PIPELINE_FAILURE_TOTAL},
  "pipeline_success_rate": ${PIPELINE_SUCCESS_RATE},
  "pipeline_success_rate_pct": ${PIPELINE_SUCCESS_RATE_PCT},
  "pipeline_success_formula": "min(parse_ok_total, influx_ok_total, redis_ok_total) / recv_total",
  "core_pipeline_success_total": ${CORE_PIPELINE_SUCCESS_TOTAL},
  "core_pipeline_failure_total": ${CORE_PIPELINE_FAILURE_TOTAL},
  "core_pipeline_success_rate": ${CORE_PIPELINE_SUCCESS_RATE},
  "core_pipeline_success_rate_pct": ${CORE_PIPELINE_SUCCESS_RATE_PCT},
  "core_pipeline_success_formula": "min(parse_ok_total, redis_ok_total) / recv_total"
}
EOF

echo "[DIST] business recv_total=${RECV_TOTAL}"
echo "[DIST] business pipeline_success_total=${PIPELINE_SUCCESS_TOTAL}"
echo "[DIST] business pipeline_success_rate=${PIPELINE_SUCCESS_RATE_PCT}%"
echo "[DIST] business core_pipeline_success_total=${CORE_PIPELINE_SUCCESS_TOTAL}"
echo "[DIST] business core_pipeline_success_rate=${CORE_PIPELINE_SUCCESS_RATE_PCT}%"
echo "[DIST] business_summary=${SUMMARY_FILE}"

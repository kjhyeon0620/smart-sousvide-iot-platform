#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
FIXTURE_DIR="${ROOT_DIR}/tests/fixtures/loadtest"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "${WORK_DIR}"' EXIT

fail() {
  echo "[FAIL] $1" >&2
  exit 1
}

assert_contains() {
  local file="$1"
  local needle="$2"
  if ! grep -F -- "$needle" "$file" >/dev/null 2>&1; then
    fail "expected '${needle}' in ${file}"
  fi
}

prepare_attempt_dir() {
  local run_id="$1"
  local attempt="$2"
  local attempt_dir="${WORK_DIR}/${run_id}/attempt-${attempt}"
  mkdir -p "${attempt_dir}"
  echo "${attempt_dir}"
}

run_test() {
  local name="$1"
  shift
  "$@"
  echo "[PASS] ${name}"
}

test_summarize_results_happy_path() {
  local attempt_dir
  attempt_dir="$(prepare_attempt_dir run-happy 1)"

  cp "${FIXTURE_DIR}/results/happy/part-1.log" "${attempt_dir}/part-1.log"
  cp "${FIXTURE_DIR}/results/happy/part-2.log" "${attempt_dir}/part-2.log"

  "${ROOT_DIR}/scripts/loadtest/summarize-results.sh" "${attempt_dir}" >/dev/null

  local summary="${attempt_dir}/connection-summary.json"
  [[ -f "${summary}" ]] || fail "connection-summary.json was not created"

  assert_contains "${summary}" '"run_id": "run-happy"'
  assert_contains "${summary}" '"attempt": "1"'
  assert_contains "${summary}" '"parts": 2'
  assert_contains "${summary}" '"published_total": 30'
  assert_contains "${summary}" '"failed_total": 3'
  assert_contains "${summary}" '"attempted_total": 33'
  assert_contains "${summary}" '"success_rate": 0.9091'
  assert_contains "${summary}" '"success_rate_pct": 90.91'
  assert_contains "${summary}" '"throughput_total_msg_per_sec": 7.75'
  assert_contains "${summary}" '"success_rate_formula": "published_total / (published_total + failed_total)"'
}

test_summarize_results_zero_attempted_boundary() {
  local attempt_dir
  attempt_dir="$(prepare_attempt_dir run-zero 2)"

  cp "${FIXTURE_DIR}/results/zero/part-1.log" "${attempt_dir}/part-1.log"

  "${ROOT_DIR}/scripts/loadtest/summarize-results.sh" "${attempt_dir}" >/dev/null

  local summary="${attempt_dir}/connection-summary.json"
  [[ -f "${summary}" ]] || fail "connection-summary.json was not created for zero boundary"

  assert_contains "${summary}" '"run_id": "run-zero"'
  assert_contains "${summary}" '"attempt": "2"'
  assert_contains "${summary}" '"parts": 1'
  assert_contains "${summary}" '"published_total": 0'
  assert_contains "${summary}" '"failed_total": 0'
  assert_contains "${summary}" '"attempted_total": 0'
  assert_contains "${summary}" '"success_rate": 0.0000'
  assert_contains "${summary}" '"success_rate_pct": 0.00'
  assert_contains "${summary}" '"throughput_total_msg_per_sec": 0'
}

test_summarize_ingestion_happy_path() {
  local attempt_dir
  attempt_dir="$(prepare_attempt_dir run-biz 3)"

  cp "${FIXTURE_DIR}/ingestion/happy/backend.log" "${attempt_dir}/backend.log"

  "${ROOT_DIR}/scripts/loadtest/summarize-ingestion-metrics.sh" "${attempt_dir}" >/dev/null

  local summary="${attempt_dir}/business-summary.json"
  [[ -f "${summary}" ]] || fail "business-summary.json was not created"

  assert_contains "${summary}" '"run_id": "run-biz"'
  assert_contains "${summary}" '"attempt": "3"'
  assert_contains "${summary}" '"recv_total": 200'
  assert_contains "${summary}" '"parse_ok_total": 190'
  assert_contains "${summary}" '"parse_fail_total": 10'
  assert_contains "${summary}" '"influx_ok_total": 180'
  assert_contains "${summary}" '"influx_fail_total": 20'
  assert_contains "${summary}" '"influx_bypass_total": 0'
  assert_contains "${summary}" '"redis_ok_total": 170'
  assert_contains "${summary}" '"redis_fail_total": 30'
  assert_contains "${summary}" '"pipeline_success_total": 170'
  assert_contains "${summary}" '"pipeline_failure_total": 30'
  assert_contains "${summary}" '"pipeline_success_rate": 0.8500'
  assert_contains "${summary}" '"pipeline_success_rate_pct": 85.00'
  assert_contains "${summary}" '"pipeline_success_formula": "min(parse_ok_total, influx_ok_total, redis_ok_total) / recv_total"'
  assert_contains "${summary}" '"core_pipeline_success_total": 170'
  assert_contains "${summary}" '"core_pipeline_failure_total": 30'
  assert_contains "${summary}" '"core_pipeline_success_rate": 0.8500'
  assert_contains "${summary}" '"core_pipeline_success_rate_pct": 85.00'
  assert_contains "${summary}" '"core_pipeline_success_formula": "min(parse_ok_total, redis_ok_total) / recv_total"'
}

test_summarize_ingestion_happy_path_with_influx_bypass_and_core_pipeline_divergence() {
  local attempt_dir
  attempt_dir="$(prepare_attempt_dir run-biz-core-diff 4)"

  cp "${FIXTURE_DIR}/ingestion/core-pipeline-diff/backend.log" "${attempt_dir}/backend.log"

  "${ROOT_DIR}/scripts/loadtest/summarize-ingestion-metrics.sh" "${attempt_dir}" >/dev/null

  local summary="${attempt_dir}/business-summary.json"
  [[ -f "${summary}" ]] || fail "business-summary.json was not created for core pipeline divergence"

  assert_contains "${summary}" '"run_id": "run-biz-core-diff"'
  assert_contains "${summary}" '"attempt": "4"'
  assert_contains "${summary}" '"recv_total": 300'
  assert_contains "${summary}" '"parse_ok_total": 280'
  assert_contains "${summary}" '"parse_fail_total": 20'
  assert_contains "${summary}" '"influx_ok_total": 200'
  assert_contains "${summary}" '"influx_fail_total": 100'
  assert_contains "${summary}" '"influx_bypass_total": 15'
  assert_contains "${summary}" '"redis_ok_total": 250'
  assert_contains "${summary}" '"redis_fail_total": 50'
  assert_contains "${summary}" '"pipeline_success_total": 200'
  assert_contains "${summary}" '"pipeline_failure_total": 100'
  assert_contains "${summary}" '"pipeline_success_rate": 0.6667'
  assert_contains "${summary}" '"pipeline_success_rate_pct": 66.67'
  assert_contains "${summary}" '"pipeline_success_formula": "min(parse_ok_total, influx_ok_total, redis_ok_total) / recv_total"'
  assert_contains "${summary}" '"core_pipeline_success_total": 250'
  assert_contains "${summary}" '"core_pipeline_failure_total": 50'
  assert_contains "${summary}" '"core_pipeline_success_rate": 0.8333'
  assert_contains "${summary}" '"core_pipeline_success_rate_pct": 83.33'
  assert_contains "${summary}" '"core_pipeline_success_formula": "min(parse_ok_total, redis_ok_total) / recv_total"'
}

test_summarize_ingestion_zero_recv_boundary() {
  local attempt_dir
  attempt_dir="$(prepare_attempt_dir run-biz-zero 1)"

  cp "${FIXTURE_DIR}/ingestion/zero/backend.log" "${attempt_dir}/backend.log"

  "${ROOT_DIR}/scripts/loadtest/summarize-ingestion-metrics.sh" "${attempt_dir}" >/dev/null

  local summary="${attempt_dir}/business-summary.json"
  [[ -f "${summary}" ]] || fail "business-summary.json was not created for zero recv"

  assert_contains "${summary}" '"recv_total": 0'
  assert_contains "${summary}" '"influx_bypass_total": 0'
  assert_contains "${summary}" '"pipeline_success_total": 0'
  assert_contains "${summary}" '"pipeline_failure_total": 0'
  assert_contains "${summary}" '"pipeline_success_rate": 0.0000'
  assert_contains "${summary}" '"pipeline_success_rate_pct": 0.00'
  assert_contains "${summary}" '"core_pipeline_success_total": 0'
  assert_contains "${summary}" '"core_pipeline_failure_total": 0'
  assert_contains "${summary}" '"core_pipeline_success_rate": 0.0000'
  assert_contains "${summary}" '"core_pipeline_success_rate_pct": 0.00'
  assert_contains "${summary}" '"core_pipeline_success_formula": "min(parse_ok_total, redis_ok_total) / recv_total"'
}

test_summarize_ingestion_missing_totals_failure() {
  local attempt_dir
  attempt_dir="$(prepare_attempt_dir run-biz-fail 4)"

  cp "${FIXTURE_DIR}/ingestion/missing-totals/backend.log" "${attempt_dir}/backend.log"

  local stderr_file="${attempt_dir}/stderr.log"
  if "${ROOT_DIR}/scripts/loadtest/summarize-ingestion-metrics.sh" "${attempt_dir}" >"${attempt_dir}/stdout.log" 2>"${stderr_file}"; then
    fail "summarize-ingestion-metrics.sh should fail when totals line is missing"
  fi

  assert_contains "${stderr_file}" 'No [INGEST-METRICS/1s] totals line found'
  [[ ! -f "${attempt_dir}/business-summary.json" ]] || fail "business-summary.json must not be created on parse failure"
}

test_summarize_ingestion_malformed_totals_failure() {
  local attempt_dir
  attempt_dir="$(prepare_attempt_dir run-biz-malformed 5)"

  cp "${FIXTURE_DIR}/ingestion/malformed-totals/backend.log" "${attempt_dir}/backend.log"

  local stderr_file="${attempt_dir}/stderr.log"
  if "${ROOT_DIR}/scripts/loadtest/summarize-ingestion-metrics.sh" "${attempt_dir}" >"${attempt_dir}/stdout.log" 2>"${stderr_file}"; then
    fail "summarize-ingestion-metrics.sh should fail when totals line is malformed"
  fi

  assert_contains "${stderr_file}" "Failed to parse totals from backend log line:"
  [[ ! -f "${attempt_dir}/business-summary.json" ]] || fail "business-summary.json must not be created on parse failure"
}

test_summarize_ingestion_missing_required_totals_key_with_numeric_window_failure() {
  local attempt_dir
  attempt_dir="$(prepare_attempt_dir run-biz-missing-required-key 6)"

  cp "${FIXTURE_DIR}/ingestion/missing-required-totals-key/backend.log" "${attempt_dir}/backend.log"

  local stderr_file="${attempt_dir}/stderr.log"
  if "${ROOT_DIR}/scripts/loadtest/summarize-ingestion-metrics.sh" "${attempt_dir}" >"${attempt_dir}/stdout.log" 2>"${stderr_file}"; then
    fail "summarize-ingestion-metrics.sh should fail when totals segment is missing a required key"
  fi

  assert_contains "${stderr_file}" "Failed to parse totals from backend log line:"
  [[ ! -f "${attempt_dir}/business-summary.json" ]] || fail "business-summary.json must not be created when totals key is missing"
}

run_test "summarize-results happy path" test_summarize_results_happy_path
run_test "summarize-results zero attempted boundary" test_summarize_results_zero_attempted_boundary
run_test "summarize-ingestion happy path" test_summarize_ingestion_happy_path
run_test "summarize-ingestion happy path with bypass and core divergence" test_summarize_ingestion_happy_path_with_influx_bypass_and_core_pipeline_divergence
run_test "summarize-ingestion zero recv boundary" test_summarize_ingestion_zero_recv_boundary
run_test "summarize-ingestion missing totals failure" test_summarize_ingestion_missing_totals_failure
run_test "summarize-ingestion malformed totals failure" test_summarize_ingestion_malformed_totals_failure
run_test "summarize-ingestion missing required totals key with numeric window failure" test_summarize_ingestion_missing_required_totals_key_with_numeric_window_failure

echo "All summarize script tests passed."

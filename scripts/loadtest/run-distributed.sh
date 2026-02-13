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

MAX_ATTEMPTS="${MAX_ATTEMPTS:-4}"
MIN_PARALLELISM="${MIN_PARALLELISM:-40}"
MAX_PARTITIONS="${MAX_PARTITIONS:-6}"

if (( PARTITIONS < 1 )); then
  echo "PARTITIONS must be >= 1" >&2
  exit 1
fi

if (( TOTAL_CONNECTIONS < PARTITIONS )); then
  echo "TOTAL_CONNECTIONS must be >= PARTITIONS" >&2
  exit 1
fi

contains_thread_limit_error() {
  local dir="$1"
  grep -R -E "unable to create native thread|pthread_create failed \(EAGAIN\)" "$dir" >/dev/null 2>&1
}

run_once() {
  local run_dir="$1"
  local partitions="$2"
  local parallelism="$3"

  mkdir -p "$run_dir"

  local base rem start conn log_file p
  base=$(( TOTAL_CONNECTIONS / partitions ))
  rem=$(( TOTAL_CONNECTIONS % partitions ))
  start=0

  local pids=()

  for (( p=0; p<partitions; p++ )); do
    conn="$base"
    if (( p < rem )); then
      conn=$(( conn + 1 ))
    fi

    log_file="$run_dir/part-${p}.log"

    local cmd=(
      ./gradlew mqttLoadTest
      --args="--connections=${conn} --start-index=${start} --connect-parallelism=${parallelism} --messages-per-second=${MPS} --duration-seconds=${DURATION} --qos=${QOS} --client-prefix=sim-p${p}"
    )

    echo "[DIST] start part=${p} connections=${conn} startIndex=${start} parallelism=${parallelism} log=${log_file}"
    "${cmd[@]}" > "$log_file" 2>&1 &
    pids+=("$!")

    start=$(( start + conn ))
  done

  local fail=0 pid
  for pid in "${pids[@]}"; do
    if ! wait "$pid"; then
      fail=1
    fi
  done

  "$ROOT_DIR/scripts/loadtest/summarize-results.sh" "$run_dir" || true
  return "$fail"
}

ROOT_RUN_ID="$(date +%Y%m%d-%H%M%S)"
ROOT_OUT_DIR="docs/loadtest-runs/${ROOT_RUN_ID}"
mkdir -p "$ROOT_OUT_DIR"

attempt=1
current_partitions="$PARTITIONS"
current_parallelism="$PARALLELISM"

while (( attempt <= MAX_ATTEMPTS )); do
  attempt_dir="$ROOT_OUT_DIR/attempt-${attempt}"
  echo "[DIST] attempt=${attempt}/${MAX_ATTEMPTS} partitions=${current_partitions} parallelism=${current_parallelism}"

  if run_once "$attempt_dir" "$current_partitions" "$current_parallelism"; then
    echo "[DIST] completed run_id=${ROOT_RUN_ID} attempt=${attempt}"
    echo "[DIST] logs_dir=${attempt_dir}"
    exit 0
  fi

  echo "[DIST] attempt failed: ${attempt_dir}" >&2

  if ! contains_thread_limit_error "$attempt_dir"; then
    echo "[DIST] non-thread-limit failure detected; stop retrying" >&2
    exit 1
  fi

  # Native thread ceiling is usually dominated by total live client threads,
  # so increasing partition count first is more effective than only lowering connect parallelism.
  if (( current_partitions < MAX_PARTITIONS )); then
    next_partitions=$(( current_partitions + 1 ))
    echo "[DIST] fallback: increase partitions ${current_partitions} -> ${next_partitions}" >&2
    current_partitions="$next_partitions"
  elif (( current_parallelism > MIN_PARALLELISM )); then
    next_parallelism=$(( current_parallelism / 2 ))
    if (( next_parallelism < MIN_PARALLELISM )); then
      next_parallelism="$MIN_PARALLELISM"
    fi
    echo "[DIST] fallback: reduce connect parallelism ${current_parallelism} -> ${next_parallelism}" >&2
    current_parallelism="$next_parallelism"
  else
    echo "[DIST] reached fallback limit (partitions=${current_partitions}, parallelism=${current_parallelism})" >&2
    exit 1
  fi

  attempt=$(( attempt + 1 ))
done

echo "[DIST] exhausted attempts without success" >&2
exit 1

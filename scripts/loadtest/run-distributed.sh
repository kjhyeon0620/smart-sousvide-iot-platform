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
SIM_TASK="${SIM_TASK:-mqttLoadTest}"
RUN_ID="${RUN_ID:-$(date +%Y%m%d-%H%M%S)}"
GRADLE_USER_HOME_DIR="${GRADLE_USER_HOME_DIR:-$ROOT_DIR/.gradle-local}"

MAX_ATTEMPTS="${MAX_ATTEMPTS:-4}"
MIN_PARALLELISM="${MIN_PARALLELISM:-40}"
MAX_PARTITIONS="${MAX_PARTITIONS:-6}"
PART_TIMEOUT_SECONDS="${PART_TIMEOUT_SECONDS:-$((DURATION + 120))}"
GRADLE_DAEMON_FLAG="${GRADLE_DAEMON_FLAG:---no-daemon}"
JVM_OPTS="${JVM_OPTS:-}"
REQUIRE_BUSINESS_SUMMARY="${REQUIRE_BUSINESS_SUMMARY:-0}"

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

bootstrap_gradle_cache() {
  mkdir -p "$GRADLE_USER_HOME_DIR"
  if [[ -d "$GRADLE_USER_HOME_DIR/wrapper/dists" ]]; then
    return
  fi

  local default_cache="$HOME/.gradle/wrapper/dists"
  if [[ -d "$default_cache" ]]; then
    mkdir -p "$GRADLE_USER_HOME_DIR/wrapper"
    cp -a "$default_cache" "$GRADLE_USER_HOME_DIR/wrapper/" >/dev/null 2>&1 || true
  fi
}

resolve_main_class() {
  case "$SIM_TASK" in
    mqttLoadTest)
      echo "com.iot.IoT.loadtest.MqttLoadSimulator"
      ;;
    mqttLoadTestHive)
      echo "com.iot.IoT.loadtest.MqttLoadSimulatorHive"
      ;;
    *)
      echo "Unsupported SIM_TASK: $SIM_TASK" >&2
      exit 1
      ;;
  esac
}

prepare_runtime() {
  local prep_home="${GRADLE_USER_HOME_DIR}/prep"
  mkdir -p "$prep_home/wrapper"
  if [[ -d "$GRADLE_USER_HOME_DIR/wrapper/dists" && ! -d "$prep_home/wrapper/dists" ]]; then
    cp -a "$GRADLE_USER_HOME_DIR/wrapper/dists" "$prep_home/wrapper/" >/dev/null 2>&1 || true
  fi

  echo "[DIST] preparing runtime classpath (one-time)"
  env "GRADLE_USER_HOME=${prep_home}" ./gradlew "${GRADLE_DAEMON_FLAG}" classes >/dev/null
  LOADTEST_CLASSPATH="$(env "GRADLE_USER_HOME=${prep_home}" ./gradlew "${GRADLE_DAEMON_FLAG}" -q printLoadTestRuntimeClasspath | tail -n1)"
  LOADTEST_MAIN_CLASS="$(resolve_main_class)"

  if [[ -z "${LOADTEST_CLASSPATH}" ]]; then
    echo "Failed to resolve runtime classpath." >&2
    exit 1
  fi
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
  local parts=()

  for (( p=0; p<partitions; p++ )); do
    conn="$base"
    if (( p < rem )); then
      conn=$(( conn + 1 ))
    fi

    log_file="$run_dir/part-${p}.log"
    local cmd=(java)
    if [[ -n "$JVM_OPTS" ]]; then
      # shellcheck disable=SC2206
      cmd+=( $JVM_OPTS )
    fi
    cmd+=(
      -cp "$LOADTEST_CLASSPATH"
      "$LOADTEST_MAIN_CLASS"
      "--connections=${conn}"
      "--start-index=${start}"
      "--connect-parallelism=${parallelism}"
      "--messages-per-second=${MPS}"
      "--duration-seconds=${DURATION}"
      "--qos=${QOS}"
      "--client-prefix=sim-p${p}"
    )

    echo "[DIST] start part=${p} connections=${conn} startIndex=${start} parallelism=${parallelism} timeout=${PART_TIMEOUT_SECONDS}s log=${log_file}"
    if command -v timeout >/dev/null 2>&1; then
      timeout --preserve-status "${PART_TIMEOUT_SECONDS}s" "${cmd[@]}" > "$log_file" 2>&1 &
    else
      "${cmd[@]}" > "$log_file" 2>&1 &
    fi
    pids+=("$!")
    parts+=("$p")

    start=$(( start + conn ))
  done

  local fail=0 pid rc idx
  for idx in "${!pids[@]}"; do
    pid="${pids[$idx]}"
    wait "$pid" || rc=$?
    if [[ "${rc:-0}" -ne 0 ]]; then
      echo "[DIST] part=${parts[$idx]} exited with code=${rc}" >&2
      fail=1
    fi
    rc=0
  done

  if ! "$ROOT_DIR/scripts/loadtest/summarize-results.sh" "$run_dir"; then
    echo "[DIST] connection summary generation failed (${run_dir})" >&2
    fail=1
  fi
  local summary_file="$run_dir/business-summary.json"
  if [[ -f "$run_dir/backend.log" ]]; then
    if ! "$ROOT_DIR/scripts/loadtest/summarize-ingestion-metrics.sh" "$run_dir"; then
      echo "[DIST] business summary generation failed (${run_dir}/backend.log)" >&2
      if (( REQUIRE_BUSINESS_SUMMARY == 1 )); then
        fail=1
      fi
    fi
  else
    echo "[DIST] backend log not found; skip business summary (${run_dir}/backend.log)" >&2
    if (( REQUIRE_BUSINESS_SUMMARY == 1 )); then
      fail=1
    fi
  fi

  if (( REQUIRE_BUSINESS_SUMMARY == 1 )); then
    if [[ ! -s "$summary_file" ]]; then
      echo "[DIST] required business summary missing or empty: ${summary_file}" >&2
      fail=1
    fi
  fi
  return "$fail"
}

ROOT_RUN_ID="$RUN_ID"
ROOT_OUT_DIR="docs/loadtest-runs/${ROOT_RUN_ID}"
DASH_MANIFEST_SCRIPT="$ROOT_DIR/scripts/loadtest/generate-dashboard-manifest.sh"
echo "[DIST] run_id=${ROOT_RUN_ID}"
echo "[DIST] run_root=${ROOT_OUT_DIR}"
if ! mkdir -p "$(dirname "$ROOT_OUT_DIR")"; then
  echo "[DIST] failed to prepare parent directory for: ${ROOT_OUT_DIR}" >&2
  exit 1
fi
if ! mkdir "$ROOT_OUT_DIR"; then
  echo "[DIST] duplicate run directory exists: ${ROOT_OUT_DIR}" >&2
  exit 1
fi
bootstrap_gradle_cache
prepare_runtime

attempt=1
current_partitions="$PARTITIONS"
current_parallelism="$PARALLELISM"

while (( attempt <= MAX_ATTEMPTS )); do
  attempt_dir="$ROOT_OUT_DIR/attempt-${attempt}"
  echo "[DIST] attempt=${attempt}/${MAX_ATTEMPTS} task=${SIM_TASK} partitions=${current_partitions} parallelism=${current_parallelism}"

  if run_once "$attempt_dir" "$current_partitions" "$current_parallelism"; then
    if [[ -x "$DASH_MANIFEST_SCRIPT" ]]; then
      "$DASH_MANIFEST_SCRIPT" || echo "[DIST] dashboard manifest refresh failed" >&2
    fi
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

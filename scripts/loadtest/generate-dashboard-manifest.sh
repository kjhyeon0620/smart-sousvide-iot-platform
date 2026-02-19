#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

RUNS_DIR="docs/loadtest-runs"
OUT_FILE="${RUNS_DIR}/manifest.json"

mkdir -p "$RUNS_DIR"

tmp_file="$(mktemp)"
trap 'rm -f "$tmp_file"' EXIT

{
  echo "{"
  echo "  \"generated_at\": \"$(date +%Y-%m-%dT%H:%M:%S%z)\","
  echo "  \"entries\": ["

  first=1
  while IFS= read -r conn; do
    attempt_dir="$(dirname "$conn")"
    biz="${attempt_dir}/business-summary.json"
    if [[ ! -f "$biz" ]]; then
      continue
    fi

    run_id="$(basename "$(dirname "$attempt_dir")")"
    attempt_name="$(basename "$attempt_dir")"
    attempt="${attempt_name#attempt-}"

    if [[ $first -eq 0 ]]; then
      echo ","
    fi
    first=0

    printf '    {\n'
    printf '      "run_id": "%s",\n' "$run_id"
    printf '      "attempt": "%s",\n' "$attempt"
    printf '      "connection_path": "%s",\n' "$conn"
    printf '      "business_path": "%s"\n' "$biz"
    printf '    }'
  done < <(find "$RUNS_DIR" -type f -name "connection-summary.json" | sort)

  echo
  echo "  ]"
  echo "}"
} > "$tmp_file"

mv "$tmp_file" "$OUT_FILE"
echo "[DASH] manifest generated: $OUT_FILE"

#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  scripts/dev/pr-guard.sh
  scripts/dev/pr-guard.sh <allowed-file-1> <allowed-file-2> ...

Checks:
1) Current branch must not be main/master.
2) If no allowed files are provided, working tree must be clean.
3) If allowed files are provided, only those files may be changed/untracked.
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

branch="$(git branch --show-current)"
if [[ "$branch" == "main" || "$branch" == "master" ]]; then
  echo "[GUARD] Refusing to continue on branch: $branch" >&2
  echo "[GUARD] Create/switch to an issue branch first." >&2
  exit 1
fi

mapfile -t changed < <(git status --porcelain=v1 | sed -E 's/^.. //')

if [[ "${#changed[@]}" -eq 0 ]]; then
  echo "[GUARD] Working tree is clean on branch '$branch'."
  exit 0
fi

if [[ "$#" -eq 0 ]]; then
  echo "[GUARD] Working tree is not clean." >&2
  printf ' - %s\n' "${changed[@]}" >&2
  exit 1
fi

declare -A allow
for f in "$@"; do
  allow["$f"]=1
done

disallowed=()
for f in "${changed[@]}"; do
  if [[ -z "${allow[$f]+x}" ]]; then
    disallowed+=("$f")
  fi
done

if [[ "${#disallowed[@]}" -gt 0 ]]; then
  echo "[GUARD] Disallowed changed files detected." >&2
  printf ' - %s\n' "${disallowed[@]}" >&2
  echo "[GUARD] Allowed set:" >&2
  printf ' - %s\n' "$@" >&2
  exit 1
fi

echo "[GUARD] OK: only allowed files are changed on branch '$branch'."

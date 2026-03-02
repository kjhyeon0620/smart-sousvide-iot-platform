#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 3 ]]; then
  echo "Usage: $0 <issue-title> <branch-type> <branch-suffix-kebab>" >&2
  echo "Example: $0 \"PR2: HiveMQ 10k path\" \"techdebt\" \"hive-10k-path\"" >&2
  exit 1
fi

ISSUE_TITLE="$1"
BRANCH_TYPE="$2"
BRANCH_SUFFIX="$3"

if [[ "$BRANCH_TYPE" != "feature" && "$BRANCH_TYPE" != "techdebt" && "$BRANCH_TYPE" != "bugfix" ]]; then
  echo "branch-type must be one of: feature | techdebt | bugfix" >&2
  exit 1
fi

if [[ ! "$BRANCH_SUFFIX" =~ ^[a-z0-9-]+$ ]]; then
  echo "branch-suffix must be kebab-case: [a-z0-9-]+" >&2
  exit 1
fi

ISSUE_URL="$(gh issue create --title "$ISSUE_TITLE" --body "Created by workflow bootstrap script.")"
ISSUE_NO="$(echo "$ISSUE_URL" | sed -E 's#^.*/issues/([0-9]+)$#\1#')"

if [[ ! "$ISSUE_NO" =~ ^[0-9]+$ ]]; then
  echo "Failed to parse issue number from URL: $ISSUE_URL" >&2
  exit 1
fi

BRANCH_NAME="${BRANCH_TYPE}/#${ISSUE_NO}-${BRANCH_SUFFIX}"

git switch main
git pull --ff-only
git switch -c "$BRANCH_NAME"

echo "ISSUE_URL=$ISSUE_URL"
echo "BRANCH_NAME=$BRANCH_NAME"

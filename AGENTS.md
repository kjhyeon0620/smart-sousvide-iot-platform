# Agent Workflow Guardrails

This repository uses a strict Git workflow for all agent-driven changes.
Do not rely on memory or ad-hoc behavior.

## Mandatory Order
1. Create issue.
2. Create/switch branch from `main` using issue number naming.
3. Implement only approved scope files.
4. Run `scripts/dev/pr-guard.sh <allowed-files...>` before commit.
5. Commit, push, open PR.
6. After merge: switch `main`, pull, delete local/remote work branch.

## Branch Naming
- Format: `<type>/#<issue-number>-<short-kebab-summary>`
- Allowed `type`:
  - `feature`: 사용자 기능 추가
  - `techdebt`: 내부 구조 개선/리팩토링/성능 개선
  - `bugfix`: 결함 수정
- Example:
  - `feature/#26-device-domain-api-foundation`
  - `techdebt/#20-hive-10k-influx-bypass-isolation`
  - `bugfix/#31-watchdog-duplicate-notify-fix`

## Hard Rules
- Never start implementation work directly on `main`.
- Never commit files outside explicit PR scope.
- If working tree has unrelated changes, stop and isolate before proceeding.
- Always show staged file list before final commit/push.

## Required Commands
- Guard check:
  - `scripts/dev/pr-guard.sh <allowed-file-1> <allowed-file-2> ...`
- Issue + branch bootstrap:
  - `scripts/dev/issue-branch.sh "<issue title>" "<branch-type>" "<branch-suffix>"`

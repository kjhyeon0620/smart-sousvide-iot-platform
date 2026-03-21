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

## WSL/Windows Working Tree Note
- In this repository, WSL and Windows filesystem differences can produce noisy `git status` output.
- Do not block progress only because of broad noisy changes when they are known environment artifacts.
- In that case, enforce scope by:
  - fixed issue branch + explicit allowed file list
  - `scripts/dev/pr-guard.sh <allowed-files...>`
  - staged file list verification before commit/push

## Documentation Update Rule
- If code behavior/spec/workflow changes and is not yet documented, update docs in the same PR.
- Every implementation PR must include:
  - what changed
  - why it changed
  - how to verify
  - risks/limits

## GitHub Operation Ownership
- GitHub-related actions are user-owned.
- The user directly performs:
  - issue creation/updates
  - branch creation/switch/push
  - PR create/edit/merge
  - post-merge cleanup
- The coding agent must not execute GitHub workflow commands by default.
- The coding agent should provide only actionable outputs for the user:
  - command list
  - commit message
  - PR title/body template
  - allowed-file list and verification steps

## Why `pr-guard` Exists
- `scripts/dev/pr-guard.sh` is a local scope safety check.
- It prevents accidental changes outside approved files before commit/push.
- Even when GitHub actions are user-owned, `pr-guard` remains required as pre-commit verification.

## Required Commands
- Guard check:
  - `scripts/dev/pr-guard.sh <allowed-file-1> <allowed-file-2> ...`
- Issue + branch bootstrap:
  - `scripts/dev/issue-branch.sh "<issue title>" "<branch-type>" "<branch-suffix>"`

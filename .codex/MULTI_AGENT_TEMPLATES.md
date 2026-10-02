# Multi-Agent Operation Templates

## 1) Standard task kickoff (send to leader)
You are Leader. Do NOT edit code or run commands.
Goal: <feature/fix in 1-2 lines>

Process (AGENTS.md, docs/specs/README.md):
1) Ask spec agent for size (S/M/L) and, for M/L, the spec file docs/specs/NNNN-<slug>/spec.md. Relay Open questions to me; continue only when status: ready.
2) Spawn github agent to reuse/create the issue (template) and create the branch.
3) Spawn implementer for PR1 implementation.
4) Spawn tester to add tests for PR1.
5) Spawn reviewer to review PR1 diff against the spec.
6) Report a work summary to me (changed files, approach, reasons, risks, test results). No commit message or PR drafts. Stop and wait for my approval.
7) After I approve: github commits, pushes, and opens the PR using the templates. Never merge.
Stop after PR is opened and summarize: PR link, what changed, how to test, risks.

## 2) Auto split when task is large
Leader: If this task is bigger than one small PR, split into max 3 PRs.
For each PR:
- spec: acceptance criteria
- implementer: code
- tester: tests
- reviewer: review
- github: PR
Proceed PR1 only, then wait for my "go" to continue.

## 3) Emergency debugging flow
Leader: Tests/build failing.
Spawn:
- implementer: find root cause and propose fix
- tester: isolate failing case and improve test reliability
- reviewer: check proposed fix for regressions
Return: root cause, fix diff summary, how to verify.

## 4) Diff to blog_facts (only when I ask for a blog report)
Leader: After PR1 review is done, ask github to create .codex/blog_diff.patch from origin/main...HEAD and delegate that diff content to blog_facts.
Return blog_facts output exactly in the schema.
[PASTE DIFF OR FILE CONTENT HERE]

## 5) Diff artifact commands (portable)
git diff origin/main...HEAD > .codex/blog_diff.patch
# optional split by file
git diff origin/main...HEAD -- src/main/java/... > .codex/blog_diff_app.patch

## 6) Low-context / retire protocol
Leader: If session/context budget is low OR a major task chunk is complete,
retire the current agent before spawning the next one.
Before retire, require this exact Handoff Card:
1) Completed
2) In Progress
3) Pending/Next
4) Risks/Blockers
5) Files/Artifacts
6) Recommended next agent and first instruction
Then pass the card as the first message to the next agent.

## 7) Command safety for approval UI
- Use one command per line.
- Avoid chaining with && or ; in approval-sensitive steps.
- If github is blocked by approval/permission, require MANUAL_RUNBOOK:
  - exact commands in order
  - expected checkpoints

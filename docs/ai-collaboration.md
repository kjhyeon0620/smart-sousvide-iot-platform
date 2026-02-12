# AI Collaboration Protocol (Codex)

## Purpose
이 문서는 Codex와 협업할 때의 운영 규칙을 정의한다.
사람 기여자 일반 규칙은 `CONTRIBUTING.md`를 따른다.

## Operating Model
- GitHub 조작(이슈 생성, 브랜치 푸시, PR 생성/머지)은 엔지니어가 직접 수행한다.
- Codex는 GitHub 템플릿에 맞춘 텍스트 산출물을 제공한다.

## Outputs Codex Provides
- 이슈 본문: `.github/ISSUE_TEMPLATE/*.yml` 필드에 맞춘 완성 텍스트
- 커밋 메시지: Conventional Commits 규칙 준수 메시지
- PR 본문: `.github/pull_request_template.md` 항목을 채운 완성 텍스트
- 필요 시 테스트 증거/체크리스트/릴리스 노트 초안

## Request Format (Recommended)
- 이슈 본문 요청: 목적, 범위(include/exclude), 완료조건(DoD), 우선순위
- 커밋 메시지 요청: 변경 파일 목록, 핵심 변경사항, 영향 범위
- PR 본문 요청: 배경, 변경사항, 테스트 결과, 리스크/롤백

## Quality Bar
- 고트래픽/운영 안정성 기준으로 문구를 작성한다.
- 모호한 표현 대신 측정 가능한 완료 기준을 사용한다.
- 파싱 실패/저장 실패/재시도/관측성(로그/메트릭) 관점을 항상 포함한다.

## Work Report Standard (Mandatory Per Task)
- Codex는 각 작업 단위가 끝날 때마다 아래 5가지를 반드시 보고한다.
1. 변경 파일: 어떤 파일을 수정/추가했는지 명시
2. 구현 방식: 어떤 흐름과 구조로 구현했는지 설명
3. 설계 이유/이론: 왜 그런 설계를 선택했는지 근거 설명
4. 리스크: 현재 변경의 잠재 리스크와 영향 범위
5. 테스트 결과: 실행 커맨드, 통과/실패 상태, 핵심 로그 요약

## Explanation Depth
- 단순 결과 요약이 아니라 기능 단위로 `무엇(What)`, `어떻게(How)`, `왜(Why)`를 함께 설명한다.
- 필요한 경우 아키텍처 패턴(예: Layered, Port/Adapter), 장애 격리, 확장성 관점의 근거를 포함한다.

## Issue Closure Deliverable (Mandatory)
- 이슈 1개가 완료될 때마다 Codex는 일반 작업 보고와 별도로 아래 블로그용 분석 리포트를 반드시 제공한다.
1. 작업 배경 (Context): 왜 필요한 작업이었는지 1줄 요약
2. 문제 상황 (Problem): 에러/비효율/리스크 등 해결 대상
3. 해결 과정 (Solution): 적용한 설계/로직/패턴과 의사결정 근거
4. 핵심 코드 (Key Code): 해결의 열쇠가 되는 핵심 로직 발췌
5. 배운 점 (Lesson): 기술적 인사이트, 트레이드오프, 주의사항

## Issue Completion Response Bundle
- 이슈 종료 시 Codex 응답은 아래 4가지를 항상 한 번에 제공한다.
1. 작업 결과 상세 보고 (What/How/Why/Risk/Test)
2. 커밋 메시지 제안 (Conventional Commits)
3. PR 본문 초안 (`.github/pull_request_template.md` 형식)
4. 블로그용 팩트 리포트 (`Context/Problem/Solution/Key Code/Lesson`)

## Blog Fact Reporting Style
- 역할은 '취재 기자' 관점으로, 과장 없이 팩트 중심으로 작성한다.
- 단순 코드 나열이 아니라 Problem Solving 관점으로 맥락을 연결한다.
- 특정 프로젝트 문맥에만 갇히지 않고, 코드로 입증 가능한 일반화된 교훈을 포함한다.

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

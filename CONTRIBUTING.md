# Contributing Guide

## Branch Strategy
- `main`: 배포 기준 브랜치(직접 푸시 금지)
- 작업 브랜치 prefix:
  - `feature`: 사용자 기능 추가
  - `techdebt`: 내부 구조 개선/리팩토링/성능 개선
  - `bugfix`: 결함 수정
- 브랜치 네이밍:
  - `<type>/#<issue-number>-<short-kebab-summary>`
  - 예: `feature/#26-device-domain-api-foundation`

## Commit Convention
- Conventional Commits 사용
- 예시: `feat(ingestion): parse device status payload`

## Workflow
1. Issue 생성 후 범위와 완료 조건(DoD) 확정
2. 작업 브랜치 생성
3. 코드/테스트/문서 반영
4. PR 생성 후 템플릿 항목 모두 작성
5. 리뷰 반영 후 머지

## Documentation Policy
- 문서에 없는 신규 동작/제약/운영 절차를 구현하면 같은 PR에서 문서를 반드시 업데이트한다.
- 최소 업데이트 대상:
  - API 변경: `docs/device-api.md`, 필요 시 `docs/swagger.md`
  - 운영/관측 변경: `docs/operations-runbook.md`, `docs/observability.md`
  - 방향성/우선순위 변경: `docs/project-plan.md`, `docs/refactoring-roadmap.md`
- 문서에는 최소한 다음을 포함한다:
  - 변경 요약
  - 구현/운영 이유
  - 검증 방법
  - 리스크/후속 과제

## Planning Record Rule
- 구현 전에 간단한 실행 계획(범위, 파일 후보, 테스트 전략)을 작성하고 이슈/PR 본문 또는 관련 문서에 남긴다.
- 계획이 변경되면 최종 반영 상태를 PR 본문과 문서에 동기화한다.

## GitHub Ownership Rule
- GitHub 관련 작업은 사용자 직접 수행을 원칙으로 한다.
- 사용자 수행 범위:
  - 이슈 생성/수정/종료
  - 브랜치 생성/전환/푸시
  - PR 생성/수정/머지
  - 머지 후 브랜치 정리
- 에이전트 수행 범위:
  - 코드/테스트/문서 변경
  - 로컬 검증 수행
  - 사용자가 실행할 명령/커밋 메시지/PR 본문 템플릿 제공

## `pr-guard` Policy
- `scripts/dev/pr-guard.sh`는 GitHub 작업 자동화를 위한 스크립트가 아니라 로컬 변경 범위 검증용 가드다.
- 커밋/푸시 전에 반드시 실행해 허용 파일 외 변경이 없는지 확인한다.

## PR Rules
- 최소 1개 이슈 링크 필수
- 테스트 증거(로그/스크린샷) 첨부 필수
- 리스크 및 롤백 방법 명시 필수

## Coding Standards
- Java 17, Spring Boot 기준 준수
- DTO/도메인 타입은 명시적이고 엄격하게 설계
- 예외 경로(파싱 실패/저장 실패) 반드시 처리

## Testing Expectations
- 단위 테스트: 파싱/제어 로직 필수
- 통합 테스트: MQTT 수신 -> Influx/Redis 반영 경로 검증
- 부하 테스트: 시뮬레이터 기반 동시 연결 시나리오 별도 관리

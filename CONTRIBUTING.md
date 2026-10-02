# Contributing Guide

## Workflow

이 저장소는 이슈 단위의 Spec-Driven Development로 진행한다. 절차 전체는 [`docs/specs/README.md`](docs/specs/README.md)에 있다.

1. 이슈 등급(S/M/L)을 정한다. M 이상은 `docs/specs/`에 스펙을 쓰고 `status: ready`로 만든다.
2. 이슈를 생성한다 (feature / bug / tech-debt 템플릿, 스펙 링크 포함). 이미 열린 이슈가 있으면 그 이슈를 쓴다.
3. 최신 `main`에서 `<type>/#<issue>-<short-kebab-summary>` 브랜치를 만든다.
   - `feature`: 사용자 기능, `techdebt`: 내부 구조·리팩토링·성능, `bugfix`: 결함 수정
   - 예: `feature/#55-user-device-dashboard-mvp`
4. 코드, 테스트, 문서를 반영한다. 구현 중 스펙이 바뀌면 [구현 중 스펙 변경](docs/specs/README.md#구현-중-스펙-변경) 기준을 따른다. 범위 변경은 승인 후 진행한다.
5. 커밋하기 전에 작업 내용을 보고하고 승인을 받는다 (AI 에이전트가 작업한 경우).
6. 이번 이슈 범위의 파일만 stage했는지 확인한 뒤 커밋, 푸시하고, PR 템플릿을 모두 채워 PR을 만든다.
7. 스펙의 회고를 채우고 status를 `done`으로 바꾼 상태로 PR에 포함한다. 사용자가 리뷰하고 머지한다. 머지 후 배포 결과는 다음 이슈 PR에서 스펙의 `운영 반영`에 기록한다.

## Branch / Commit
- `main`은 배포 기준 브랜치다. 직접 푸시하지 않는다. `main`에 push되면 K-Le-PaaS 배포가 실행된다.
- Conventional Commits를 쓴다. 예: `feat(ingestion): parse device status payload`

## Documentation
- 어떤 변경에 어떤 문서를 함께 고쳐야 하는지는 [문서 동기화 표](docs/specs/README.md#6-문서-동기화-완료-조건)를 따른다. 문서를 반영하지 않으면 완료로 보지 않는다.
- 문서의 역할
  - 제품 범위와 백로그: `docs/product.md`
  - 구조와 공통 계약: `docs/architecture.md`
  - 결정 근거: `docs/adr/`
  - 이슈 스펙: `docs/specs/`
- 같은 내용을 여러 문서에 복사하지 않는다. 한 곳에 쓰고 나머지는 링크한다.

## GitHub Ownership
- AI 에이전트: 이슈 생성, 브랜치 생성, 구현과 검증, **커밋 전 보고**, 승인 후 커밋·푸시·PR 생성
- 사용자: 보고 승인, PR 리뷰와 **머지**. 에이전트는 머지하지 않는다.
- 이슈, 브랜치, 커밋, PR은 템플릿과 최근 이력의 형식을 따른다 ([`AGENTS.md` §3.1](AGENTS.md)).

## PR Rules
- 이슈 링크 1개 이상, M 이상이면 스펙 링크를 포함한다.
- 테스트 증거(명령, 결과, 로그 또는 스크린샷)를 포함한다.
- 리스크와 롤백 방법을 적는다.

## Coding / Testing
- Java 17, Spring Boot 4. DTO와 도메인 타입은 명시적이고 엄격하게 만든다.
- 예외 경로(파싱 실패, 저장 실패, publish 실패)를 반드시 처리하고 메트릭으로 드러낸다.
- 단위 테스트: 파싱, 제어, 명령 상태 전이는 필수
- 통합 테스트: MQTT 수신 → Influx/Redis 반영 경로
- 부하 테스트: 시뮬레이터 기반 시나리오 ([`docs/load-test-scenarios.md`](docs/load-test-scenarios.md)), 결과는 run-id 단위로 [`docs/load-test-results.md`](docs/load-test-results.md)에 기록

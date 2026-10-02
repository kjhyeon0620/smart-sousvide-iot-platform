# Agent Guide

이 저장소에서 작업하는 모든 AI 에이전트(Claude, Codex 등)의 진입점이다. `CLAUDE.md`는 이 파일을 불러온다.
사람을 위한 규칙은 [`CONTRIBUTING.md`](CONTRIBUTING.md)에 있다.

## 1. 먼저 읽을 것

| 목적 | 문서 |
|---|---|
| 작업 절차 (스펙 → 구현 → 회고) | [`docs/specs/README.md`](docs/specs/README.md) |
| 제품 범위, 백로그 | [`docs/product.md`](docs/product.md) |
| 구조, MQTT/저장소/에러 계약, 설정 키, **알려진 격차** | [`docs/architecture.md`](docs/architecture.md) |
| 결정 근거 | [`docs/adr/`](docs/adr/README.md) |
| HTTP API 계약 | [`docs/device-api.md`](docs/device-api.md) |
| 완료 보고 형식 | [`docs/ai-collaboration.md`](docs/ai-collaboration.md) |
| 운영, 관측, 부하, 배포 | `docs/observability.md`, `docs/operations-runbook.md`, `docs/load-test-*.md`, `docs/klepaas-oracle-deployment.md` |

작업 대상 이슈의 스펙(`docs/specs/NNNN-*/spec.md`)이 있으면 그 스펙이 이번 작업의 기준이다.

## 2. Spec-Driven 작업 규칙

- 이슈 등급(S/M/L)을 먼저 제안한다. M 이상이면 **`status: ready`인 스펙 없이 구현을 시작하지 않는다.**
- 스펙을 쓸 때는 구현자가 임의로 정해야 하는 부분만 질문한다 (한 번에 최대 5개). 답은 스펙의 Decisions에 기록한다.
- 여러 이슈에 영향을 주는 결정은 ADR이나 `architecture.md`로 옮기고, 스펙에는 링크만 남긴다.
- 구현 중 스펙 변경은 [기준](docs/specs/README.md#구현-중-스펙-변경)을 따른다. 사실 정정과 구현 재량 안의 결정은 스펙을 바로 고치고 계속 진행한다. 범위 변경(새 endpoint, 계약, 스키마, 설정 키, 제어 정책)은 스펙 수정안을 쓰고 사용자에게 묻고 기다린다.
- 코드와 문서가 다른 것을 발견하면 `architecture.md` §9 알려진 격차에 기록하고 사용자에게 알린다.
- 완료 조건: 스펙의 완료 증거 실행, [문서 동기화 표](docs/specs/README.md#6-문서-동기화-완료-조건)에 따른 문서 반영, 회고 작성, `status: done` (모두 구현 PR 안에서)

## 3. Git 워크플로 (필수)

1. **이슈 생성 (에이전트)**: `gh issue list`로 중복을 확인한다. 이미 열린 이슈가 있으면 그 이슈를 쓰고, 없으면 템플릿으로 생성한다 (§3.1).
2. **브랜치 생성 (에이전트)**: 최신 `main`에서 `<type>/#<issue>-<kebab-summary>`로 만들고 전환한다.
   - `feature`: 사용자 기능, `techdebt`: 구조·리팩토링·성능, `bugfix`: 결함 수정
3. 스펙에서 승인된 범위의 파일만 수정하고 검증한다.
4. **커밋 전 보고 (에이전트)**: 커밋하지 않은 상태로 [작업 요약](docs/ai-collaboration.md)만 보고한다. 커밋 메시지나 PR 본문 초안은 제시하지 않는다 (규칙대로 작성하는 것은 에이전트의 몫).
5. **승인 (사용자)**
6. **커밋 → 푸시 → PR 생성 (에이전트)**: 이번 이슈 범위의 파일만 stage하고, staged 목록을 확인한 뒤 진행한다.
7. **머지 (사용자)**. 머지 후 `main` pull과 브랜치 정리는 사용자가 요청할 때 에이전트가 수행한다.

### Hard Rules
- `main`에서 직접 구현하지 않는다.
- PR 범위 밖의 파일을 커밋하지 않는다.
- 작업 트리에 관련 없는 변경이 있으면 멈추고 분리한다. 단, WSL/Windows 파일시스템 차이로 생기는 노이즈는 예외다. 이 경우 이슈 브랜치 고정과 staged 목록 확인으로 범위를 통제한다.
- 최종 커밋 전에 staged 파일 목록을 보여준다.
- 비밀값(토큰, kubeconfig, DB 비밀번호)을 코드, 문서, 스펙, 로그에 남기지 않는다.

- 사용자 승인 전에는 커밋, 푸시, PR 생성을 하지 않는다. 승인 후 코드를 다시 고쳤으면 다시 보고한다.
- **머지는 하지 않는다.** `gh pr merge`, auto-merge 설정, `main` 직접 푸시를 금지한다.

### 3.1 템플릿과 이력 일관성 (필수)

제목, 본문, 브랜치, 커밋 메시지를 쓰기 전에 템플릿과 최근 이력(`git log --no-merges -20`, `gh issue list`, `gh pr list`)을 확인하고 형식을 맞춘다.
템플릿과 최근 이력이 충돌하면 템플릿의 섹션 구조를 유지하고, 문체는 최근 이력을 따른다. 충돌이 있었다면 보고에 적는다.

| 항목 | 규칙 | 예 |
|---|---|---|
| 이슈 | `.github/ISSUE_TEMPLATE/{feature,bug,tech-debt}.yml`의 본문 섹션을 모두 채운다. 제목은 템플릿 prefix + 한국어 요약. 템플릿의 `type:*` 라벨은 저장소에 없으므로 라벨 없이 생성한다 | `[Feature] 대시보드 데모 데이터 콘솔 추가`, `[TechDebt] README 및 기술 문서 정리` |
| 브랜치 | `<type>/#<issue>-<kebab-summary>` (영문 kebab) | `feature/#55-user-device-dashboard-mvp` |
| 커밋 | Conventional Commits `type(scope): summary` (`feat`, `fix`, `refactor`, `docs`, `ci`, `chore`, `techdebt`). 요약은 한국어나 영어 | `feat(downlink): add ack/idempotency/retry-timeout reliability flow`, `fix: 배포 이미지에 사용자 대시보드 포함` |
| PR 제목 | 대표 커밋 제목과 같게 | `fix: 배포 이미지에 사용자 대시보드 포함` |
| PR 본문 | `.github/pull_request_template.md`의 모든 섹션을 채운다 (`Closes: #N`, Spec 링크, Test Evidence 표, Docs Sync) | - |
| base | `main` | - |

- 이슈는 `gh issue create --title ... --body-file ...`로 템플릿 본문을 넣어 생성한다.

## 4. 명령

| 목적 | 명령 |
|---|---|
| 로컬 인프라 | `docker compose up -d` |
| 백엔드 실행 | `./gradlew bootRun` |
| CI와 같은 테스트 게이트 | `./gradlew test --tests "com.iot.IoT.ingestion.*" --tests "com.iot.IoT.control.*" --tests "com.iot.IoT.watchdog.*" --tests "com.iot.IoT.loadtest.*"` |
| 전체 테스트 (인프라 필요) | `./gradlew test` |
| 단일 테스트 | `./gradlew test --tests '<FQCN>'` |
| 프론트 빌드 | `cd frontend && npm ci && npm run build` |
| 부하 테스트 | `docs/load-test-scenarios.md` 참고 |
| 부하 테스트 집계 스크립트 검사 | `bash tests/loadtest/test-summarizers.sh` (`scripts/loadtest/summarize-*.sh`를 바꿨을 때) |

- Gradle은 같은 작업 트리에서 동시에 두 개 이상 실행하지 않는다.
- 검증은 변경 규모에 맞게 한다. 문서만 바꿨으면 빌드나 테스트를 돌리지 않는다. 코드 변경은 가장 가까운 테스트부터 실행한다.

## 5. 코드 규칙

- Java 17, Spring Boot 4. DTO는 record와 Bean Validation으로 명시적이고 엄격하게 만든다.
- 외부 시스템(Influx, Redis, MQTT publish)은 Port/Adapter 뒤에 둔다. 제어 판단은 순수 로직으로 유지한다.
- ingestion의 각 단계 실패는 서로 격리하고, 신뢰성 분류(dead-letter / replay candidate)와 메트릭을 함께 기록한다.
- 새 기능에는 테스트를 먼저 쓰는 것을 기본으로 한다. 파싱, 제어, 명령 상태 전이는 단위 테스트가 필수다.
- 생성물(`build/`, `bin/`, `frontend/dist/`, `docs/loadtest-runs/*`의 원시 산출물)은 직접 수정하지 않는다.
- `.local/`은 git에서 제외된 개인 작업 공간이다. 여기서 나온 결정은 `docs/specs/`나 `docs/adr/`로 옮긴다.

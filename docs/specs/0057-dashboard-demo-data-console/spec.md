---
issue: 57
title: Dashboard demo data console
status: in-progress
size: L
type: feature
branch: feature/#57-dashboard-demo-data-console
---

# Dashboard demo data console

> 로컬 기획 노트(2026-05-06)와 GitHub 이슈 #57 본문을 옮겨 온 스펙이다.
> **구현 현황 (2026-10-03)**: `feature/#57-dashboard-demo-data-console` 브랜치를 최신 `main`으로 rebase했다. 이미 머지된 #58 커밋은 중복이라 빠졌고, 삭제된 `docs/user-device-dashboard.md`의 변경은 이 스펙과 `device-api.md`의 "Dev-Only Dashboard Demo API" 섹션으로 옮겼다. PR 전 상태다.

## 목적 / 성공 조건
- 목적: 개발자가 curl, MQTT publish, Redis/Influx 수동 조작 없이 UI 버튼으로 대시보드의 상태별 화면(online/offline/disabled/heating/holding/명령 실패·만료)을 재현하고 정리한다.
- 기술적 완료: dev 전용 API로 `SV-DEMO-*` 기기와 heartbeat, telemetry, 명령 이력을 생성, 조정, 삭제할 수 있다.
- 사용성: 제품 화면을 방해하지 않도록 패널은 기본적으로 접혀 있다. 운영 환경에서는 API와 패널이 모두 노출되지 않는다.

## 범위
- 포함: dev 전용 backend API (`/dev/dashboard-demo`), frontend demo 패널, prefix 가드
- 제외: 기존 `/devices` 계약 변경, 실제 MQTT downlink publish, 운영 환경 노출, 인증

## 입출력·계약
- `POST /dev/dashboard-demo/scenario` `{count, scenario: mixed|heating|holding|offline|command-failure, baseTargetTemp}` → `{createdDevices, onlineDevices, offlineDevices, disabledDevices, temperaturePoints, commands}`
- `PATCH /dev/dashboard-demo/devices/{deviceId}` `{online?, enabled?, temp?, targetTemp?, state?}`
  - `enabled` → MySQL `devices.enabled`
  - `online=true` → heartbeat 갱신, `online=false` → `device:{id}:lastSeen` 삭제
  - `temp`/`targetTemp`/`state` → Influx `device_status`에 point 추가
- `POST /dev/dashboard-demo/devices/{deviceId}/commands` `{statuses: [SENT|EXPIRED|FAILED|...]}` → `device_commands` row를 직접 생성 (publish하지 않음)
- `DELETE /dev/dashboard-demo` → `SV-DEMO-*` 범위의 MySQL(devices, device_commands)과 Redis(`devices:active` 멤버, `device:*:lastSeen`, `watchdog:*:offline-notified`) 정리

## 제약
- `deviceId`가 `SV-DEMO-`로 시작하지 않으면 거부한다. clear도 이 prefix 범위만 삭제한다.
- 운영 profile에서는 controller bean을 만들지 않는다.
- [architecture §5 저장소](../../architecture.md#5-저장소)의 키 형식을 그대로 쓴다.

## 예외·경계 상황
| 상황 | 개발자가 보는 것 | 시스템 동작 |
|---|---|---|
| prefix 외 deviceId | 400 `INVALID_REQUEST` | 변경 없음 |
| 운영 환경 | 패널 숨김 | API 404 |
| Influx 삭제 실패 | warning 표시 | MySQL/Redis 정리는 완료 |
| 이미 존재하는 demo 기기로 다시 생성 | 새 scenario 결과 | 기존 `SV-DEMO-*`를 MySQL과 Redis에서 먼저 정리한 뒤 생성 |

## 미결 질문 (Open)
- 없음. 아래 결정은 브랜치 구현에서 내려진 것이다. PR을 만들 때 사용자에게 확인받는다.

## 결정 기록 (Decisions)
- 2026-05-06 운영 API 계약은 바꾸지 않는다. `SV-DEMO-` prefix로 범위를 제한한다.
- 2026-06-28 (구현) Q1 → `@Profile({"local", "dev"})`로 켠다. 별도 property는 두지 않는다.
- 2026-06-28 (구현) Q2 → Influx 삭제는 이번 범위에서 제외한다. 매번 새 timestamp로 sample을 써서 차트에 최신 데이터가 보이게 하고, 응답에 warning을 담는다.
- 2026-06-28 (구현) Q3 → 가용성 조회(`GET /dev/dashboard-demo`)가 404면 패널을 숨긴다. 프론트 플래그는 두지 않는다.
- 2026-06-28 (구현) Q4 → command sample은 DB row만 만들고 MQTT publish는 하지 않는다.
- 2026-06-28 (구현) Q5 → scenario를 생성할 때 기존 demo 데이터(MySQL, Redis)를 먼저 정리한다.
- 2026-10-03 (rebase) API 문서는 `device-api.md`의 "Dev-Only Dashboard Demo API" 섹션을 기준으로 한다. prefix 위반과 지원하지 않는 scenario는 400 `INVALID_REQUEST`(`InvalidDeviceQueryException`)를 반환한다.

## 완료 증거
| 증명할 것 | 방법 |
|---|---|
| prefix 가드 (생성·수정·삭제) | controller/service 테스트 |
| scenario 응답 카운트 | service 테스트 |
| clear가 `SV-DEMO-*`만 삭제 | 통합 또는 service 테스트 (비-demo 기기가 남는지 확인) |
| 운영 profile에서 bean 미생성 | context 테스트 |
| 패널로 5가지 상태 재현 | 로컬 수동 QA + 스크린샷 ([panel](screenshot-panel-desktop.png), [after action](screenshot-panel-after-action.png)) |
| frontend 빌드 | `cd frontend && npm run build` |
| 로컬 수동 확인 절차 | `SPRING_PROFILES_ACTIVE=local`로 백엔드 실행 → 접힌 Demo data 패널에서 scenario 생성 → demo 기기, 현재 차트 데이터, 명령 이력 샘플 확인 → clear 후 `SV-DEMO-*` 기기가 목록에서 사라지는지 확인 |

## Tasks
### T1. Backend dev API + prefix 가드
- 먼저 읽을 것: `architecture.md` §5, `DeviceCommandService`, `RedisHeartbeatAdapter`, `InfluxDbTemperatureTimeSeriesAdapter`
- 변경: `DevDashboardDemoController`/`Service`/DTO, profile 조건
- 금지: `/devices` 응답 변경, 실제 MQTT publish
- 검증: 위 표의 backend 테스트

### T2. Frontend demo 패널
- 먼저 읽을 것: `frontend/src/App.tsx`, `api.ts`, T1 계약
- 변경: `demoApi.ts`, `DemoDataPanel.tsx`, 목록 화면에 접힌 상태로 배치
- 금지: 사용자 화면 레이아웃 변경
- 검증: `npm run build`, 수동 QA

## 회고
-

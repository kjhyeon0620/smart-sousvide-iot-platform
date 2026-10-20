---
issue: 75
title: 규칙 기반 기기 이상 탐지
status: done
size: L
type: feature
branch: feature/#75-rule-based-anomaly-detection
---

# 규칙 기반 기기 이상 탐지

## 목적 / 성공 조건
- 목적: 사용자와 운영자가 "기기는 online인데 제대로 가열·유지되지 않는" 상태를 알아챈다. 지금은 offline 외의 이상을 감지하는 수단이 없다.
- 기술적 완료: telemetry를 받을 때 기기별로 세 가지 규칙(목표 이탈, 가열 정체, 센서 고착)을 평가한다. 조건이 성립하면 이상을 열고, 해소되면 닫는다.
- 사용성·운영: 사용자는 대시보드 기기 상세에서 진행 중 이상과 최근 이력을 본다. 운영자는 메트릭과 로그로 유형별 발생·해소 수와 판정 실패를 본다.

## 배경
- Timestral(시계열 파운데이션 모델) 예측 구간 방식과 비교했다. 시뮬레이션 기준으로 규칙이 탐지율과 오탐 모두 앞섰다. 근거: [ADR-0005](../../adr/0005-timestral-anomaly-detection-deferred.md)
- `architecture.md` §9 "fail-safe 이벤트가 로깅만 한다" 격차와 맞닿아 있다. 이번 이슈는 이상 탐지 결과의 노출까지만 다룬다. 외부 알림 채널(webhook/push)은 product §6 #8에서 다룬다.

## 규칙 (기본값은 설정 키로 조정)
| 규칙 | 조건 | 기본값 | PoC 결과(시뮬레이션) |
|---|---|---|---|
| `TARGET_DEVIATION` | `|temp - targetTemp| > threshold`가 `duration` 이상 연속 | 1.0℃, 3분 | 뚜껑 열림 95% 탐지 |
| `HEATING_STALL` | `state = HEATING`이 `duration` 이상 이어졌는데 그 사이 온도 상승이 `minRise` 이하 | 10분, 0.0℃ | 히터 열화 50% 탐지 |
| `SENSOR_STUCK` | `temp`가 `duration` 동안 한 번도 바뀌지 않음 | 10분 | 센서 고착 100% 탐지 |

- `TARGET_DEVIATION`은 기기가 마지막 목표 변경 뒤 한 번이라도 목표에 도달(`|temp - targetTemp| <= control.deadband`)했을 때부터 적용한다. 예열 중에는 목표와 멀리 떨어져 있는 게 정상이기 때문이다. 기본값으로 가정한다.
- 시간은 메시지 개수가 아니라 서버 수신 시각 기준이다. 기기마다 publish 주기가 다를 수 있다.
- PoC 정상 데이터 오탐률은 0/40이다. 임계값은 시뮬레이터를 보고 정한 값이라 실제 기기에서는 다시 조정해야 한다.

## 범위
- 포함:
  - 순수 판정 로직(`anomaly` 패키지). 입력은 기기별 상태와 메시지, 출력은 열기/유지/닫기
  - 기기별 판정 상태를 인스턴스 메모리에 둔다 (Q1)
  - ingestion 파이프라인에 판정 단계 추가. 다른 단계와 실패를 격리한다
  - 메트릭 `iot_anomaly_opened_total{type}`, `iot_anomaly_resolved_total{type}`, `iot_anomaly_evaluation_failures_total`, 로그
  - 이상 이력 MySQL 저장, 조회 API, status 응답 확장, 대시보드 표시 (Q2)
  - 설정 키, 문서 동기화
- 제외:
  - 외부 알림 채널(webhook, push, email)
  - 자동 대응(히터 차단 같은 제어 명령). 제어 정책 변경이라 별도 이슈다
  - ML/예측 기반 탐지
  - 기기별 임계값 (전역 설정만 둔다)
  - 판정 상태의 다중 인스턴스 일관성 (§9 알려진 격차로 기록)
  - 데모 콘솔에서 고장 시나리오 생성 (데모 콘솔은 ingestion을 거치지 않음. 필요하면 별도 이슈)

## 입출력·계약
- HTTP (`docs/device-api.md`에 반영):
  - `GET /devices/{id}/anomalies?status={OPEN|RESOLVED}&limit={n}`: `status` 생략 시 전체, `limit` 기본 20, 범위 1..100. 최신 `detectedAt` 순.
    응답 `{devicePk, deviceId, limit, items: [{id, type, status, detectedAt, resolvedAt, detail}]}`. 에러: `404 DEVICE_NOT_FOUND`, `400`(잘못된 `status`/`limit`)
  - `GET /devices/{id}/status` 응답에 `openAnomalies: [{type, detectedAt}]` 추가 (없으면 빈 배열)
- MQTT: 변경 없음
- 저장소:
  - MySQL `device_anomalies`: `id`, `device_pk`, `device_id`(64), `type`(32), `status`(16), `detected_at`, `resolved_at`(nullable), `detail`(255), `created_at`, `updated_at`. 인덱스 `(device_pk, status)`, `(device_pk, detected_at)`
  - 판정 상태: 인스턴스 메모리 `deviceId → 상태`. 재시작하면 사라진다
- 상태 전이: `OPEN → RESOLVED`. 해소 조건은 해당 규칙이 더 이상 성립하지 않는 것. (기기, 유형)마다 OPEN은 최대 1개
- 설정 키 (`docs/architecture.md` §7):

| 키 | 기본값 |
|---|---|
| `anomaly.enabled` | `true` |
| `anomaly.target-deviation.threshold-celsius` | `1.0` |
| `anomaly.target-deviation.duration-seconds` | `180` |
| `anomaly.heating-stall.duration-seconds` | `600` |
| `anomaly.heating-stall.min-rise-celsius` | `0.0` |
| `anomaly.sensor-stuck.duration-seconds` | `600` |

## 제약
- 참조: [architecture](../../architecture.md), [ADR-0001](../../adr/0001-ingestion-architecture.md)
- 판정 로직은 순수 함수로 두고 단위 테스트한다 (AGENTS §5)
- 판정 실패가 Influx/Redis 저장과 자동 제어를 막지 않는다
- ingestion p95 지연 목표(< 200ms)를 해치지 않는다

## 예외·경계 상황
| 상황 | 사용자·운영자가 보는 것 | 시스템 동작 |
|---|---|---|
| parse 실패 메시지 | 없음 | 판정하지 않음 (기존 dead-letter) |
| 중복 메시지 (2초 억제) | 없음 | 억제된 메시지는 판정하지 않음 |
| `state = OFF` | 진행 중 이상이 닫힘 | 판정 상태 초기화, OPEN 이상은 RESOLVED |
| 목표 온도 변경 | 없음 | 도달 여부 초기화 (`TARGET_DEVIATION` 유예), 다른 규칙 상태 유지 |
| 기기 offline | watchdog offline 이벤트 | 메시지가 없으니 판정 없음. OPEN 이상은 그대로 둔다 (기본값 가정) |
| 기기 disabled | 이상이 표시됨 | 판정하고 저장한다. disabled는 제어만 막는 설정이다 (기본값 가정) |
| 미등록 기기 | 없음 | 판정 상태는 두되 저장하지 않는다. 열림 시점에 기기 조회 결과가 없으면 debug 로그만 남긴다 |
| MySQL 장애 (열기/닫기 저장 실패) | 메트릭 `iot_anomaly_evaluation_failures_total`, error 로그 | 메모리 상태는 바꾸지 않아 다음 메시지에서 다시 시도. 저장·제어 단계는 계속 |
| 애플리케이션 재시작 | 없음 | 판정 창을 처음부터 다시 쌓는다(탐지가 최대 `duration`만큼 늦어짐). 시작할 때 MySQL의 OPEN 이상을 한 번에 읽어 메모리와 맞춘다. 이 조회가 실패하면 실패 메트릭을 남기고, 남은 OPEN은 다음 전이 때까지 그대로 둔다 |
| 공유 구독(`subscriber-count >= 2`) / 다중 인스턴스 | 오탐·누락 가능 | 기기 메시지가 인스턴스에 나뉘어 판정이 부정확하다. §9 격차로 기록 |

## 미결 질문 (Open)
- 없음

## 결정 기록 (Decisions)
- 2026-10-20 외부 알림 채널은 제외 → product §6 #8 별도 이슈. (범위 통제)
- 2026-10-20 `TARGET_DEVIATION`은 목표 도달 이후부터 적용 → 기본값 가정. (예열 중 오탐 방지)
- 2026-10-20 Q1 판정 상태 위치 → 인스턴스 메모리. (ingestion에 I/O를 더하지 않는다. 재시작 지연과 다중 인스턴스 부정확은 감수하고 §9에 기록. 중복 억제와 같은 방식) [승격: architecture §9]
- 2026-10-20 Q2 노출 범위 → MySQL 이력 + 조회 API + status 응답 + 대시보드. webhook은 제외.
- 2026-10-20 Q3 PoC 자료 → 스크립트와 결과 요약을 ADR-0005 자료로 커밋. (재현성) [승격: ADR-0005]
- 2026-10-20 진행 중 gauge는 두지 않음 → 기본값 가정. (DB 집계가 필요해 비용 대비 효용 낮음. 발생·해소 카운터로 추세는 볼 수 있다)
- 2026-10-20 MySQL 쓰기는 열기/닫기 전이 때만 한다 → 기본값 가정. (메시지마다 DB에 쓰지 않는다)
- 2026-10-20 (구현 중) OPEN 동기화 시점: 기기를 처음 볼 때 조회 → 애플리케이션 시작 시 OPEN 전체를 한 번 조회. (부하 테스트의 미등록 기기 수천 대가 첫 메시지마다 DB를 조회하지 않게 한다. OPEN 행은 적다)
- 2026-10-20 (구현 중) `HEATING_STALL`은 이동 창 대신 체크포인트로 판정한다. 가열이 정상이면 duration마다 체크포인트를 앞으로 옮긴다. 탐지가 최대 2×duration 걸린다. (이력 버퍼 없이 기기당 상수 메모리)
- 2026-10-20 (구현 중) 미등록 기기의 이상은 메모리에서만 OPEN으로 두고 저장하지 않는다. 메트릭에는 센다. (매 메시지마다 기기 조회를 반복하지 않는다)
- 2026-10-20 (구현 중) OPEN 저장 전에 같은 (기기, 유형)의 OPEN 행이 있는지 확인하고 있으면 재사용한다. (재시작 전 행과의 중복 방지)
- 2026-10-20 (구현 중) `status` 쿼리 값은 문자열로 받아 서비스에서 검증한다. (잘못된 값도 공통 에러 형식 `400 INVALID_REQUEST`로 응답)
- 2026-10-20 (구현 중) CI 테스트 게이트(`ci.yml`, `deploy-klepaas-oracle.yml`, AGENTS §4)에 `com.iot.IoT.anomaly.*`를 추가한다. (판정 로직 테스트가 CI에서 돌게 한다)

## 완료 증거
| 증명할 것 | 방법 (명령·테스트·수동 시나리오) | 결과 |
|---|---|---|
| 규칙별 열기/유지/닫기, 경계값, 목표 변경 유예, OFF 초기화 | `./gradlew test --tests 'com.iot.IoT.anomaly.*'` | `AnomalyDetectorTest` 11개, `DeviceAnomalyServiceTest` 6개 통과 |
| 판정 실패가 저장·제어를 막지 않음 | `DeviceIngestionServiceImplTest` | 2개 추가(판정 예외 시 저장·제어 유지, 저장·제어 실패 시에도 판정 실행), 8개 통과 |
| API 계약 | `./gradlew test --tests 'com.iot.IoT.controller.*'`, `DeviceServiceImplTest` | controller 19개, service 25개 통과 (status `openAnomalies`, 필터, 잘못된 `status`/`limit` 400, 없는 기기 404) |
| CI 게이트 | AGENTS §4 CI 게이트 명령 | 46개 통과 |
| 고장 시나리오 탐지 | 로컬 인프라 + `mosquitto_pub`, duration을 20/30/30초로 줄여 실행 | `SENSOR_STUCK` 30초 뒤 OPEN → 값 변화로 RESOLVED. `TARGET_DEVIATION` 20초 뒤 OPEN → 복귀로 RESOLVED. `HEATING_STALL` OPEN → `OFF`로 모두 RESOLVED. `iot_anomaly_opened_total` 4, `resolved_total` 4, 실패 0. 잘못된 `status`는 `400 INVALID_REQUEST` |
| 대시보드 표시 | Vite dev server + 브라우저 | 목록 카드 배지("Off target", "Heating stalled"), 상세 배너와 이력 표 확인. `npm run build` 통과 |
| ingestion 지연 영향 | 3,000대 부하 on/off 각 2회 ([load-test-results](../../load-test-results.md#anomaly-evaluation-overhead-75)) | processing p95 off `10.0/11.0ms`, on `8.9/9.4ms`. 차이가 실행 간 편차보다 작다. 수신율은 같은 조건에서 41~85%로 흔들려 A/B 지표로 쓰지 않았다 |

## Tasks (L 등급만)
### T1. 순수 판정 로직
- 먼저 읽을 것: `control/ControlDecisionEngine`, `ingestion/dto/DeviceStatusMessage`
- 변경: `anomaly` 패키지에 `AnomalyType`, 판정 설정 record, 기기별 상태, 판정기(상태 + 메시지 + 시각 → 새 상태 + 활성 유형 집합)
- 금지: Spring·I/O 의존
- 검증: 규칙별 단위 테스트 (경계값, 목표 변경 유예, OFF 초기화, 시간 기준)

### T2. 저장과 ingestion 연결
- 변경: `DeviceAnomaly` 엔티티·리포지토리, 전이를 저장하는 서비스(첫 조회 시 OPEN 동기화), 메트릭, `DeviceIngestionServiceImpl`에 격리된 판정 단계
- 금지: 기존 저장·제어 단계의 순서와 실패 처리 변경
- 검증: 서비스 단위 테스트, ingestion 단위 테스트(판정 실패가 다른 단계를 막지 않음)

### T3. HTTP
- 변경: `GET /devices/{id}/anomalies`, status 응답 `openAnomalies`, Swagger, 에러 매핑
- 검증: controller 테스트

### T4. 대시보드
- 변경: 기기 상세에 진행 중 이상 배지와 최근 이력. 목록에 이상 표시. 상세 polling(5초)에 맞춘다
- 검증: `npm run build`, 수동 확인

### T5. 문서와 완료 증거
- 변경: architecture(패키지, 흐름, 저장소, 설정 키, §9), device-api, observability, operations-runbook, product
- 검증: 완료 증거 표 실행

## 운영 반영
-

## 회고
- 어긋난 점:
  - 스펙은 "기기를 처음 볼 때 OPEN 동기화"였다. 부하 테스트의 미등록 기기 수천 대가 첫 메시지마다 DB를 조회하게 되는 문제를 구현 중에 발견해 시작 시 일괄 조회로 바꿨다.
  - 완료 증거의 "3k 1회 p95 비교"는 이 호스트에서 결론을 낼 수 없었다. 수신율이 실행마다 41~85%로 흔들렸다. 각 2회로 늘리고 비교 지표를 처리 지연으로 좁혔다.
  - 목표 이탈 규칙이 "목표 도달 뒤 큰 하락"(가열 정체 시나리오의 50℃)도 잡았다. 설계대로지만 스펙 예시에는 없던 겹침이다.
- 원인 분류: 외부 제약 미인지 (부하 테스트 기기가 미등록이라는 점, 포화 호스트의 측정 편차), 완료 기준 느슨 (반복 횟수를 정하지 않음)
- 고친 위치: 스펙 Decisions(구현 중 항목), `load-test-results.md` 해석과 한계에 수신율 편차 기록. 반복 측정 기준을 테스트 절차 문서로 올릴지는 다음 부하 측정 이슈에서 정한다.

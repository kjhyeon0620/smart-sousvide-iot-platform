# Product: Smart Sous-vide IoT Platform

이 문서는 제품의 목적, 사용자, 범위, 성공 기준, 로드맵을 담는다. 구조는 [`architecture.md`](architecture.md), 결정 근거는 [`adr/`](adr/README.md)에 있다.
(이전 `project-plan.md`와 `refactoring-roadmap.md`를 통합한 문서다.)

## 1. 목적

- 기존 수비드 기기를 **비침습 방식**(ESP32 + DS18B20 + SG90 서보)으로 스마트 IoT 기기로 바꾼다.
- 기기 telemetry의 수집, 저장, 제어, 감시를 담당하는 백엔드가 **고동시 환경(단계적으로 10k 연결 목표)**에서도 안정적으로 동작하는지 검증한다.
- 기능 구현에 그치지 않고 **성능, 신뢰성, 운영성, 보안성을 정량적으로 입증**하는 것이 포트폴리오 목표다.

## 2. 사용자

| 사용자 | 목적 | 주 화면 |
|---|---|---|
| 기기 사용자 | 내 기기가 정상인지, 지금 몇 도인지, 원하는 온도로 제어되고 있는지 즉시 확인하고 조작한다 | 사용자 대시보드 (`frontend/`, `/`) |
| 운영자 | ingestion과 downlink의 이상 징후를 분류하고 대응한다 | Grafana, [`operations-runbook.md`](operations-runbook.md) |
| 기기(펌웨어) | 상태를 publish하고 명령을 수신한다 | MQTT |

사용자 화면과 운영자 화면은 분리한다. 사용자 대시보드에는 운영 메트릭을 넣지 않는다.

## 3. 성공 기준

### 3.1 시스템 SLO (로컬 벤치마크 기준)

| 지표 | 목표 |
|---|---|
| Ingestion success rate | `>= 99.9%` |
| Parse failure rate | `< 0.1%` |
| Influx write failure rate | `< 0.5%` |
| Redis heartbeat failure rate | `< 0.5%` |
| p95 ingest latency | `< 200ms` |
| 부하 | HiveMQ 모델 기준 3k 이상 안정 처리, 이후 5k/10k 단계 검증 |

connection success와 business pipeline success는 **분리해서** 평가한다. 로컬 단일 호스트 수치는 절대 성능이 아니라 병목의 위치와 개선 방향을 확인하는 자료로 쓴다.

### 3.2 제품 경험

- 사용자는 기기 목록에서 online/offline, 현재 온도, 목표 온도, 상태를 한눈에 파악한다.
- offline이거나 disabled인 기기에 수동 명령을 보낼 수 없고, 그 이유가 화면에 표시된다.
- 명령의 결과(SENT / ACKED / EXPIRED / FAILED)를 명령 이력에서 확인할 수 있다.

## 4. 범위

### 포함
- MQTT 기반 telemetry 수집 파이프라인 (strict 파싱과 검증, Influx 저장, Redis heartbeat)
- 제어 판단과 자동 downlink 명령, 수동 명령, ACK/retry/expire
- Watchdog 오프라인 감지와 fail-safe 이벤트
- Device 관리 API와 사용자 대시보드 MVP
- 관측성(Actuator/Prometheus/Grafana), 운영 runbook
- 분산 부하 테스트 시뮬레이터와 결과 문서화
- GHCR + K-Le-PaaS를 통한 Oracle k3s 배포

### 제외 (현재 단계)
- 물리 기기의 대량 실장
- 멀티 리전 등 대규모 클라우드 운영 자동화
- 프로덕션급 인증과 보안 체계 전체 (최소 보안 설계는 백로그에 포함)
- 과도한 마이크로서비스 분해
- ML 기반 제어 최적화, 심부온도 예측 정확도 마케팅
- 관리자 콘솔, 멀티 테넌트, 커스텀 위젯 빌더, 네이티브 앱, 펌웨어 OTA, 기기 지도

## 5. 현재 상태 (2026-10 기준)

| 영역 | 상태 | 관련 이슈 |
|---|---|---|
| Ingestion (parse → Influx/Redis) | 완료 | #3, #45 |
| 제어 판단 (deadband) + 자동 downlink | 완료 | #5, #43 |
| Watchdog / fail-safe | 완료 | #7 |
| 부하 테스트 프레임워크 (Paho/HiveMQ, 분산) | 완료 | #9 ~ #25 |
| Device API (CRUD, status, temps, policy, commands) | 완료 | #27 ~ #35 |
| 관측성 + Grafana + runbook | 완료 | #37, #39 |
| Swagger | 완료 | #41 |
| 신뢰성 분류 (dead-letter / replay candidate) | 완료 (메트릭과 로그 수준) | #47 |
| DeviceService 유스케이스 분리 | 완료 | #49 |
| Ingestion 지연 계측, executor 모드 비교 | 완료 | #53 |
| 사용자 대시보드 MVP | 완료 | #55, #60 |
| K-Le-PaaS Oracle 배포 워크플로 | 완료 | #58 |

알려진 격차는 [`architecture.md` §9](architecture.md#9-알려진-격차)에 정리했다.

## 6. 로드맵과 백로그

우선순위를 정하는 기준은 두 가지다. 단기에는 포트폴리오 임팩트(제품 경험, 운영성)를, 중기에는 규모 확장(비동기 분리, 재처리)을 본다.
각 항목은 착수할 때 `docs/specs/`에 스펙으로 옮긴다.

| 순위 | 항목 | 요약 | 스펙 |
|---|---|---|---|
| 1 | 서버 제어 정책을 자동 제어에 연결 | 저장된 `targetTemp`/`hysteresis`가 자동 제어에 쓰이지 않는 격차 해소 | [draft](specs/draft-control-policy-auto-control/spec.md) |
| 2 | Dashboard demo data console | dev 전용 API와 패널로 대시보드의 상태별 데모 데이터를 생성하고 삭제 | [#57 구현됨(rebase 완료), PR 전](specs/0057-dashboard-demo-data-console/spec.md) |
| 3 | DeviceOwnership + JWT | 내 기기만 조회하고 제어. 401/403, protected route | 미작성 |
| 4 | Influx fallback + readiness 강화 | Influx 401/timeout 대응, readiness probe | 미작성 |
| 5 | Command scheduler scan 최적화 | `nextRetryAt` 인덱스 중심 스캔 | 미작성 |
| 6 | 기기 등록 UX | 대시보드에서 등록, 중복 `deviceId` 처리 | 미작성 |
| 7 | CookSession 도메인 | start/pause/resume/stop/complete 상태머신과 화면 | 미작성 |
| 8 | 알림 | offline/recovered/목표 도달/명령 실패. Webhook 먼저, Push는 나중에 | 미작성 |
| 9 | Persistent DLQ + replay | replay candidate를 실제로 재처리하는 경로 | 미작성 |
| 10 | Queue 기반 분리 (Kafka/RabbitMQ) | 백프레셔. 도입 전과 후의 처리율, 실패율, 복구시간 비교가 필수 | 미작성 |
| 11 | 실시간 갱신 (SSE) | polling으로 UX가 부족할 때만 도입 | 미작성 |
| 12 | 최소 보안 통제 | MQTT TLS/ACL, secret 관리, audit log | 미작성 |
| 13 | Chaos 시나리오 | Redis down, Influx 지연, broker 재시작 | 미작성 |
| - | DeviceService facade 제거 (techdebt) | 유스케이스 서비스 직접 의존, 테스트 분리와 신뢰성 스케줄러 테스트 보강 | #63 |

## 7. 포트폴리오 스토리라인

- 문제: 고동시 IoT telemetry 환경에서 파이프라인 안정성, 관측성, 제어 신뢰성이 부족하다.
- 행동: 수집 → 부하 검증 → API 제품화 → 다운링크 신뢰성 → 관측성 표준화 → 사용자 대시보드 → 배포 자동화 → (다음) 소유권, 큐 분리
- 근거: 변경 전후의 처리율, 실패율, 지연시간을 run-id 단위 결과로 비교 ([`load-test-results.md`](load-test-results.md))
- 결론: 기능을 구현한 것이 아니라 운영 가능한 백엔드 시스템을 설계하고 검증한 역량을 보여준다.

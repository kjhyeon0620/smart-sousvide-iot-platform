# Smart Sous-vide IoT Platform - Master Project Plan

## 1. Project Objective
- Legacy sous-vide 기기를 비침습 방식으로 스마트 IoT 디바이스화한다.
- 고트래픽(단계적으로 10k 목표) 환경에서 수집/저장/제어/감시 백엔드의 안정성을 검증한다.
- 포트폴리오 관점에서 기능 구현뿐 아니라 성능, 신뢰성, 운영성, 보안성을 정량적으로 입증한다.

## 2. Scope
### In Scope
- MQTT 기반 디바이스 상태 수집 파이프라인
- JSON 파싱/검증, InfluxDB 저장, Redis heartbeat 업데이트
- 제어 판단(ControlDecisionEngine), Watchdog/Fail-safe
- 부하 테스트 시뮬레이터 및 분산 실행 체계
- 관측성(지표/로그), 장애 대응 시나리오, 결과 문서화

### Out of Scope
- 하드웨어 대량 실장(수천 대 물리 장치 구성)
- 클라우드 상용 배포 최적화(비용/운영 고도화)
- 프로덕션급 완전 보안 인증 체계(단, 최소 보안 설계는 포함)

## 3. System Architecture Summary
- Edge: ESP32 + DS18B20 + SG90 Servo
- Protocol: MQTT over Wi-Fi
- Backend: Spring Boot 3.2.x, Layered + Event-Driven(Spring Integration)
- Data:
  - InfluxDB v2: temperature/time-series
  - Redis: device state/heartbeat/watchdog state
  - MySQL: metadata
- Infra: Docker Compose (local)

## 4. Success Criteria (SLO)
- Ingestion success rate: `>= 99.9%`
- Parse failure rate: `< 0.1%`
- Influx write failure rate: `< 0.5%`
- Redis heartbeat failure rate: `< 0.5%`
- p95 ingest latency(local benchmark): `< 200ms`
- 부하 검증: HiveMQ 모델 기준 3k+ 안정 처리, 이후 5k/10k 단계적 검증

## 5. Phase Plan
## Phase A - Foundation (Done)
- Local infra 구성(Mosquitto/MySQL/Redis/InfluxDB)
- MQTT inbound 채널 수신 파이프라인 구성
- 기본 DTO/파싱/서비스 구조 확립

## Phase B - Core Ingestion (Done)
- strict DTO parsing + validation
- Influx/Redis 저장 연동
- 제어 판단 로직(deadband) 적용

## Phase C - Safety and Fail-safe (Done)
- Watchdog 스캔/오프라인 감지
- Fail-safe 이벤트/알림 경로 구현

## Phase D - Load Test Framework (Done)
- 분산 부하 실행 스크립트 및 집계
- Paho/HiveMQ 모델 비교 체계 구축
- Root-cause 분리: Paho 고연결 불안정, HiveMQ 3k 안정 확인

## Phase E - Ingestion Observability (In Progress)
- 단계별 지표:
  - MQTT recv
  - parse success/failure
  - Influx success/failure
  - Redis success/failure
- 1초 주기 델타/누적 로그 기반 검증

## Phase F - High Scale Validation (Planned)
- HiveMQ 기준 5k/10k 시나리오 수행
- connection-level vs business-level 성공률 분리 평가
- run-id 기반 결과표와 실패 원인 분류 문서화
- Completion definition (PR1):
  - HiveMQ 5k run 완료 시 `docs/loadtest-runs/<run-id>/attempt-1/connection-summary.json` 생성
  - 동일 attempt에 `docs/loadtest-runs/<run-id>/attempt-1/business-summary.json` 생성
  - `docs/load-test-results.md`에 run_id + attempt 기준 split 집계 행 반영

## Phase G - Reliability and Security Hardening (Planned)
- DLQ/replay 전략 문서화 및 적용
- idempotency(중복 수신 방지) 정책 수립
- Chaos 시나리오(Redis down/Influx 지연/Broker restart) 수행
- 최소 보안 통제(TLS/ACL/secret/audit-log) 설계 반영

## 6. Verification Strategy
- Open-loop + Closed-loop 부하 검증 병행
- 모델 교차검증:
  - 동일 조건 Paho vs HiveMQ 비교 후 병목 분리
- 결과 필수 항목:
  - command, run-id, published/failed/throughput
  - 파이프라인 단계별 실패율
  - 원인 분류(클라이언트/앱/브로커/호스트)

## 7. Risks and Mitigations
- Host resource ceiling (thread/file/socket)
  - 분산 파티션, parallelism 튜닝, timeout 가드
- 관측 지표 부족으로 원인 불명확
  - 단계별 계측 + 1초 로그 스냅샷
- 특정 MQTT client model 편향
  - Paho/HiveMQ 교차검증을 기본 절차로 유지
- 장애 상황 재현 부족
  - Chaos 테스트를 정기 시나리오로 편입

## 8. Deliverables
- Code:
  - ingestion/control/watchdog/loadtest 구현 코드
- Documents:
  - `docs/load-test-scenarios.md`
  - `docs/load-test-results.md`
  - `docs/adr/*`
  - `docs/ai-collaboration.md`
  - `docs/project-plan.md` (this file)
- Portfolio outputs:
  - 단계별 성능 리포트(정량 수치 포함)
  - 병목 원인 분석 및 개선 근거
  - 블로그 팩트 리포트(Context/Problem/Solution/Key Code/Lesson)

## 9. Working Rules
- 이슈 단위로 개발/검증/문서화/PR을 완료한다.
- 작업 종료 시 아래를 항상 제공한다:
  - 변경 파일/구현 방식/설계 이유/리스크/테스트 결과
  - 커밋 메시지
  - PR 본문
  - 블로그 팩트 리포트

## 10. Source of Truth
- 전체 계획: `docs/project-plan.md`
- 부하 테스트 실행 계획: `docs/load-test-scenarios.md`
- 부하 테스트 결과 기록: `docs/load-test-results.md`
- 협업 프로토콜: `docs/ai-collaboration.md`

# Refactoring and Productization Roadmap

## 1. Purpose
- 이 문서는 현재 IoT ingestion 중심 백엔드를 "운영 가능한 제품형 서비스"로 확장하기 위한 우선순위와 구현 범위를 정의한다.
- 대상 독자: 포트폴리오 리뷰어, 면접관, 협업 개발자.

## 2. Current Baseline (As-Is)
- 강점:
  - MQTT ingestion + strict parsing/validation
  - InfluxDB/Redis 분리 저장
  - Watchdog 오프라인 감지
  - 분산 부하 테스트 + 결과 집계 자동화
- 보완 필요:
  - 사용자/API 계층 미구현(`controller/entity/repository/service` 활용 여지)
  - 다운링크(서버 -> 디바이스) 제어 경로 부재
  - 표준 운영 관측 스택(Actuator/Prometheus/Grafana) 미도입

## 3. Recommendations Assessment
## R1. Device API/Domain expansion
- 판단: `Strongly Recommended`
- 이유:
  - 현 구조에 가장 작은 변경으로 "서비스 형태"를 만들 수 있다.
  - 면접/포트폴리오에서 도메인 모델링과 트랜잭션 경계를 설명하기 좋다.

## R2. Downlink control path
- 판단: `Strongly Recommended`
- 이유:
  - uplink-only 시스템에서 양방향 IoT 플랫폼으로 완성도가 올라간다.
  - ACK/재시도/idempotency 설계는 신뢰성 역량을 직접 보여준다.

## R3. Observability stack upgrade
- 판단: `Recommended`
- 이유:
  - 부하 테스트 수치와 시스템 상태를 하나의 시각화로 연결할 수 있다.
  - 성능 개선의 원인 분석 근거를 제공한다.

## R4. Kafka/RabbitMQ immediate adoption
- 판단: `Recommended as Phase-2`
- 이유:
  - 장기 확장성에는 유효하지만 현재 단계에서 복잡도 상승이 크다.
  - API/다운링크/관측성 선행 후 도입해야 ROI가 높다.

## 4. Execution Priority (To-Be)
1. Device Management API
2. Downlink Reliability
3. Observability Standardization
4. Queue-based Decoupling (Kafka/RabbitMQ + DLQ)

## 5. Phase Details
## Phase 1: Device Management API
- 목표:
  - 외부 클라이언트/운영자가 사용할 수 있는 제품형 인터페이스 제공
- 제안 엔드포인트:
  - `POST /devices`
  - `GET /devices/{id}`
  - `GET /devices/{id}/status`
  - `GET /devices/{id}/temps?from&to`
  - `PATCH /devices/{id}/control-policy`
- 핵심 산출물:
  - `Device`, `DeviceConfig`, `ControlPolicy`, `DeviceStatusSnapshot` 모델
  - API 계약 문서(요청/응답/오류코드)

## Phase 2: Downlink Reliability
- 목표:
  - 서버 -> 디바이스 명령 경로를 신뢰성 있게 제공
- 범위:
  - MQTT command topic 정의(`devices/{id}/cmd`)
  - command idempotency key
  - ACK timeout/retry policy
  - 실패 상태 모델(`PENDING`, `SENT`, `ACKED`, `EXPIRED`, `FAILED`)
- 핵심 산출물:
  - 명령 이력 저장(최소 MySQL)
  - 재시도/만료 스케줄러

## Phase 3: Observability Standardization
- 목표:
  - 수집 파이프라인/제어 경로/인프라 상태를 통합 관측
- 범위:
  - Spring Boot Actuator + Micrometer + Prometheus endpoint
  - Grafana dashboard:
    - ingestion TPS
    - parse/influx/redis fail rate
    - watchdog offline event count
    - JVM/thread/memory
  - 구조화 로그 상관키(`deviceId`, `messageId`, `commandId`)

## Phase 4: Queue-based Decoupling (Optional but strategic)
- 목표:
  - 급격한 입력 폭주 시 백프레셔와 재처리 체계 확보
- 범위:
  - MQTT consumer -> queue producer
  - worker consumer 분리
  - DLQ 및 replay 경로 설계
- 주의:
  - 도입 전/후 지표 비교(처리율, 실패율, 복구시간)를 반드시 문서화

## 6. Portfolio Storyline Template
- 문제 정의:
  - "고동시 IoT telemetry 환경에서 파이프라인 안정성/관측성/제어 신뢰성 부족"
- 개선 행동:
  - API 제품화 -> 다운링크 신뢰성 -> 관측성 표준화 -> 큐 분리
- 정량 근거:
  - 변경 전/후 TPS, 실패율, 지연시간, MTTR 비교표
- 결론:
  - "기능 구현"이 아닌 "운영 가능한 백엔드 시스템 설계/검증" 역량 강조

## 7. Out of Scope for now
- 과도한 마이크로서비스 분해
- 대규모 클라우드 운영 자동화(멀티 리전 등)
- 고급 ML 모델 기반 제어 최적화(규칙/통계 기반 선행 후 확장)

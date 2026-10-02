# Smart Sousvide IoT Backend

MQTT 기반 수비드 디바이스 상태 메시지를 수집하고, Redis/InfluxDB에 상태를 반영하며, 제어 정책에 따라 자동 downlink command까지 연결하는 Spring Boot 백엔드입니다. 단순 수집 서버가 아니라 ingestion, control, reliability, observability를 함께 다루는 IoT 운영형 백엔드를 목표로 구현했습니다.

## Overview
- 디바이스 telemetry를 MQTT로 수신합니다.
- payload를 strict parse/validation 후 시계열/heartbeat 경로로 분기합니다.
- watchdog으로 디바이스 오프라인 상태를 감지합니다.
- 제어 정책에 따라 auto control command를 발행합니다.
- downlink command는 ACK / retry / expire 흐름을 가집니다.
- Actuator/Prometheus/Grafana 기반 운영 지표를 노출합니다.

## Tech Stack
- Language: Java 17
- Framework: Spring Boot 4, Spring Web, Spring Data JPA, Spring Integration MQTT
- Database: MySQL
- State/Cache: Redis
- Time-series: InfluxDB
- Messaging: MQTT (Mosquitto broker, Paho/HiveMQ simulator path)
- Observability: Spring Actuator, Micrometer, Prometheus, Grafana
- Frontend: React + Vite + TypeScript user dashboard (`frontend/`, bundled into the Spring image)
- Test/Load: JUnit, Mockito, distributed load test scripts
- Deploy: Docker multi-stage → GHCR → K-Le-PaaS → Oracle k3s

## Architecture
```text
Device
  -> MQTT status publish
  -> MqttConsumer
  -> MqttPayloadParser
  -> DeviceIngestionService
     -> InfluxDB temperature series
     -> Redis heartbeat(lastSeen)
     -> ControlDecisionEngine
     -> DeviceCommandService(auto downlink)

Operator/API
  -> DeviceController
  -> DeviceService facade
     -> DeviceQueryService
     -> DeviceControlPolicyService
     -> DeviceCommandService
     -> DeviceCommandReliabilityService
```

상세 구조와 계약: [Architecture](docs/architecture.md) · 결정 기록: [ADR](docs/adr/README.md)

## Core Features
- Device management API
  - 디바이스 등록, 조회, 목록, enabled 변경
- Device status/temperature API
  - 현재 상태 스냅샷 조회
  - 온도 시계열 구간 조회
- Control policy API
  - target temperature / hysteresis 조회 및 변경
- Downlink command API
  - 수동 command 발행
  - command history 조회
  - ACK 반영
- Auto control loop
  - telemetry -> control decision -> auto command dispatch
- Reliability policy
  - duplicate suppression
  - parse dead-letter classification
  - storage/control replay candidate classification
- Observability
  - ingestion/downlink 메트릭 노출
  - Prometheus/Grafana 연동 기반 제공
- User dashboard
  - 기기 목록/상세, 온도 차트, 제어 정책, 명령 발행/이력 (polling 기반)

## API Summary
주요 endpoint:
- `POST /devices`
- `GET /devices`
- `GET /devices/{id}`
- `PATCH /devices/{id}/enabled`
- `GET /devices/{id}/status`
- `GET /devices/{id}/temps`
- `GET /devices/{id}/control-policy`
- `PATCH /devices/{id}/control-policy`
- `POST /devices/{id}/commands`
- `GET /devices/{id}/commands`
- `POST /devices/{id}/commands/{commandId}/ack`

상세 계약:
- [Device API](docs/device-api.md) (Swagger 사용법 포함)

## Reliability And Observability
현재 반영된 신뢰성 정책:
- invalid JSON / validation failure는 non-replayable parse failure로 분류
- Influx/Redis failure는 storage replay candidate로 분류
- control dispatch failure는 control replay candidate로 분류
- short-window duplicate suppression으로 즉시 중복 telemetry를 완화

현재 노출되는 대표 메트릭:
- `iot_ingestion_pipeline_overall_success_total`
- `iot_ingestion_pipeline_core_success_total`
- `iot_ingestion_processing_failure_total`
- `iot_ingestion_inflight`
- `iot_downlink_command_sent_total`
- `iot_downlink_command_acked_total`
- `iot_downlink_command_expired_total`

관련 문서:
- [Observability](docs/observability.md)
- [Operations Runbook](docs/operations-runbook.md)

## Load Test Notes
이 프로젝트는 MQTT simulator 기반 부하 테스트와 결과 집계 스크립트를 포함합니다.

현재까지 확인한 내용:
- 초기 baseline 기준 로컬 환경에서 2000 동시 연결까지 안정적으로 검증
- distributed simulator + HiveMQ path를 통해 더 높은 연결 수까지 비교 검증
- strict 고부하 재검증에서는 local single-host WSL 환경의 CPU / memory / disk saturation이 병목으로 드러남

중요한 해석:
- connection success와 business pipeline success는 별개로 봐야 합니다.
- local 환경 수치는 절대 성능 수치라기보다 병목과 개선 방향을 확인하기 위한 자료로 사용합니다.

관련 문서:
- [Load Test Results](docs/load-test-results.md)
- [Load Test Scenarios](docs/load-test-scenarios.md)

## Run Locally
인프라 실행:
```bash
docker compose up -d
```

애플리케이션 실행:
```bash
./gradlew bootRun
```

테스트 실행:
```bash
./gradlew test
```

대시보드 개발 서버 (백엔드 `localhost:8080` 프록시):
```bash
cd frontend && npm ci && npm run dev
```

주요 확인 경로:
- API: `http://localhost:8080/swagger-ui.html`
- Actuator Health: `http://localhost:8080/actuator/health`
- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000`

## Document Map
- 개발 방식 (Spec-Driven): [docs/specs/README.md](docs/specs/README.md)
- 제품 범위·로드맵: [docs/product.md](docs/product.md)
- 구조·계약·알려진 격차: [docs/architecture.md](docs/architecture.md)
- 결정 기록: [docs/adr/](docs/adr/README.md)
- API: [Device API](docs/device-api.md) (Swagger 포함)
- 운영: [Observability](docs/observability.md), [Operations Runbook](docs/operations-runbook.md), [K-Le-PaaS Deployment](docs/klepaas-oracle-deployment.md)
- 부하: [Load Test Scenarios](docs/load-test-scenarios.md), [Load Test Results](docs/load-test-results.md)
- 기여: [CONTRIBUTING.md](CONTRIBUTING.md), AI 에이전트: [AGENTS.md](AGENTS.md)

## Current Limits
주요 한계는 [architecture.md §9 알려진 격차](docs/architecture.md#9-알려진-격차)에서 관리합니다.
- 서버 제어 정책(`targetTemp`/`hysteresis`)이 아직 자동 제어에 반영되지 않습니다.
- duplicate suppression은 best-effort in-memory 정책이며, persistent DLQ / replay worker는 아직 없습니다.
- 인증/기기 소유권이 없습니다.
- 고부하 strict 검증은 local single-host 환경 한계의 영향을 크게 받습니다.

## Next Steps
우선순위와 백로그는 [docs/product.md §6](docs/product.md#6-로드맵과-백로그)을 기준으로 합니다.

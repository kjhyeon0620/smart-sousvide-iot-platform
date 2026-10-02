# Architecture

이 문서는 컴포넌트 책임, 데이터 흐름, 공통 계약(MQTT, 저장소, 에러, 명령 상태), 설정 키, 알려진 격차를 정리한 기준 문서다.
HTTP API의 세부 계약은 [`device-api.md`](device-api.md), 결정 근거는 [`adr/`](adr/README.md)에 있다.
코드와 이 문서가 다르면 코드가 사실이다. 이 문서를 고치거나 [§9 알려진 격차](#9-알려진-격차)에 추가한다.

## 1. 기술 스택

| 영역 | 선택 |
|---|---|
| Backend | Java 17, Spring Boot 4.0.1, Spring Web, Spring Data JPA, Spring Integration MQTT |
| Frontend | React 18 + Vite 6 + TypeScript, Recharts (`frontend/`) |
| Metadata | MySQL 8 (`devices`, `device_commands`) |
| Heartbeat/State | Redis |
| Time-series | InfluxDB 2.7 |
| Broker | Mosquitto 2.0 (로컬), 부하 테스트는 Paho / HiveMQ 클라이언트 모델 |
| Observability | Actuator, Micrometer, Prometheus, Grafana |
| Deploy | Docker multi-stage (frontend → Spring static) → GHCR → K-Le-PaaS → Oracle k3s |

## 2. 패키지 구조 (`com.iot.IoT`)

| 패키지 | 책임 |
|---|---|
| `ingestion.consumer` | `MqttConsumer`: 수신, 파싱 위임, 짧은 구간 중복 억제, 메트릭 기록 |
| `ingestion.parser` | `MqttPayloadParser`: strict JSON(모르는 필드 거부) + Bean Validation |
| `ingestion.service` | `DeviceIngestionServiceImpl`: Influx 저장 → Redis heartbeat → 제어 판단과 자동 명령. 각 단계의 실패를 격리 |
| `ingestion.port` / `ingestion.adapter` | 시계열과 heartbeat 저장소의 Port/Adapter (Influx, Redis) |
| `ingestion.config` | MQTT inbound(`direct`/`executor` 채널), Influx, Jackson 설정 |
| `ingestion.metrics` | `IngestionMetricsCollector` (`iot_ingestion_*`) |
| `control` | `ControlDecisionEngine`: deadband 기반 순수 판단 → `HEAT_ON`/`HEAT_OFF`/`HOLD` |
| `controller` | `DeviceController` (`/devices`), `DevDashboardDemoController` (`/dev/dashboard-demo`, `local`/`dev` profile 전용), `GlobalApiExceptionHandler` |
| `service` | `DeviceService` facade → Query / ControlPolicy / Command / CommandReliability 유스케이스 서비스 |
| `mqtt` | downlink publish Port/Adapter (QoS 1) |
| `watchdog` | heartbeat 만료 감지 → `DeviceOfflineEvent` → fail-safe publisher (현재는 로깅만 함) |
| `entity` / `repository` / `dto` | JPA 엔티티, 리포지토리, API DTO |
| `loadtest` | MQTT 부하 시뮬레이터 (Paho, HiveMQ) |
| `frontend/` | 사용자 대시보드 SPA |

## 3. 데이터 흐름

### 3.1 Uplink (telemetry → 저장 → 자동 제어)

```text
Device ──MQTT publish (sousvide/{deviceId}/status, QoS 1)──▶ Broker
  ▶ mqttInputChannel (ingestion.channel.mode = executor | direct)
  ▶ MqttConsumer
       ├─ parse 실패 → parse dead-letter (재처리하지 않음)
       ├─ 중복 (deviceId + rawPayload, 2초 이내) → drop
       ▼
    DeviceIngestionServiceImpl.ingest
       ├─ Influx write (write-mode=bypass이면 생략)   실패 → storage replay candidate
       ├─ Redis heartbeat 갱신                         실패 → storage replay candidate
       └─ ControlDecisionEngine.decide(message)
            └─ DeviceCommandService.sendAutoControlCommand
                  (HOLD, 미등록 기기, disabled 기기는 발행하지 않음)  실패 → control replay candidate
```

- 각 단계는 서로 독립적으로 실패한다. Influx가 실패해도 Redis 갱신과 제어 판단은 계속 진행한다.
- **core pipeline success**는 Redis 갱신 성공, **overall pipeline success**는 Influx와 Redis 둘 다 성공한 경우다.

### 3.2 Downlink (명령 → ACK)

```text
수동: POST /devices/{id}/commands ─┐
자동: ingestion 제어 판단 ─────────┴▶ DeviceCommandService
    idempotency 조회 → (있으면 기존 명령 반환)
    PENDING 저장 → MQTT publish (devices/{deviceId}/cmd, QoS 1) → SENT | FAILED

DeviceCommandReliabilityScheduler (downlink.reliability.scan-interval-ms, 기본 5s)
    PENDING/SENT 전체 조회
    expireAt 경과 → EXPIRED
    PENDING → 재발행
    SENT + nextRetryAt 경과 + retryCount < maxRetries → 재발행 (한도 초과 시 FAILED)

ACK: POST /devices/{id}/commands/{commandId}/ack → ACKED
```

### 3.3 Watchdog

```text
WatchdogScheduler (watchdog.scan-interval-ms)
  devices:active의 각 기기에 대해 device:{id}:lastSeen TTL이 만료되었는지 확인
  → watchdog:{id}:offline-notified가 없을 때만 한 번 알림 (cooldown)
  → FailSafeEventPublisher.publishDeviceOffline (현재 구현: 로깅)
```

### 3.4 사용자 대시보드

- 운영 배포에서는 Docker 빌드 시 `frontend/dist`를 Spring `static/`에 넣어 같은 origin에서 `/`(SPA)와 `/devices`(JSON)를 함께 제공한다. 로컬 `bootJar`는 frontend를 빌드하지 않는다.
- 로컬 개발은 Vite dev server + proxy, 또는 `VITE_API_BASE_URL`을 쓴다.
- 갱신은 polling이다. 목록 30초, 상세(status, commands) 5초, 온도 차트 30초.
- 수동 명령의 idempotency key는 `dashboard:{id}:{commandType}:{epochMillis}`다. 클릭마다 새 명령이 생성된다.
- 근거: [ADR-0004](adr/0004-user-dashboard-delivery.md)

## 4. MQTT 계약

| 방향 | Topic | QoS | Payload |
|---|---|---|---|
| Uplink | `sousvide/{deviceId}/status` (구독: `sousvide/+/status`) | 1 | `{"deviceId": string, "temp": number, "targetTemp": number, "state": "HEATING" \| "HOLDING" \| "OFF"}` |
| Downlink | `devices/{deviceId}/cmd` | 1 | `{"commandId": number, "commandType": "HEAT_ON" \| "HEAT_OFF" \| "HOLD", "requestedAt": ISO-8601}` |
| ACK | MQTT 없음. HTTP `POST /devices/{id}/commands/{commandId}/ack` | - | - |

- Uplink의 모든 필드는 필수다. **정의되지 않은 필드가 있으면 parse 실패**로 처리한다 (`FAIL_ON_UNKNOWN_PROPERTIES`).
- payload를 바꾸면 펌웨어, 시뮬레이터(`loadtest/DevicePayloadFactory`), 이 표를 **함께** 수정한다.

## 5. 저장소

| 저장소 | 키 / 테이블 | 내용 | 수명 |
|---|---|---|---|
| MySQL | `devices` | `device_id`(unique, 64), `name`, `enabled`, `control_target_temp`, `control_hysteresis`, 생성·수정 시각 | 영구 |
| MySQL | `device_commands` | 명령, 상태, idempotency key, retry/expire 시각, topic, payload, 에러 | 영구 |
| Redis | `device:{deviceId}:lastSeen` | 마지막 수신 시각 | TTL `ingestion.heartbeat-ttl-seconds` (120s) |
| Redis | `devices:active` (set) | watchdog 추적 대상 | 영구 |
| Redis | `watchdog:{deviceId}:offline-notified` | 오프라인 중복 알림 방지 | cooldown |
| Influx | measurement `device_status`, tag `deviceId`, fields `temp`, `targetTemp`, `state` | 온도 시계열 | bucket retention |

- 스키마는 JPA `ddl-auto: update`로 관리한다. 마이그레이션 도구는 쓰지 않는다 (§9 참고).

## 6. 공통 계약

### 6.1 HTTP 에러 응답

모든 HTTP 에러는 `{code, message, timestamp}` 형식이며 `GlobalApiExceptionHandler`에서 변환한다. 코드 목록과 HTTP 상태 매핑은 [`device-api.md` Error Codes](device-api.md#error-codes)에 있다.

### 6.2 명령 상태 모델

`PENDING → SENT → ACKED`, `SENT → EXPIRED` (ACK timeout), `PENDING/SENT → FAILED` (publish 실패 또는 retry 한도 초과).
idempotency key는 `(devicePk, idempotencyKey)` 단위로 유일하다. 같은 key로 다시 요청하면 기존 명령을 반환한다. 근거: [ADR-0002](adr/0002-downlink-command-reliability.md)

### 6.3 신뢰성 분류

| 분류 | 대상 | 자동 재처리 |
|---|---|---|
| parse dead-letter | invalid JSON, validation 실패 | 안 함 |
| storage replay candidate | Influx/Redis 실패, consumer 런타임 예외 | 후보로 집계만 함 (DLQ는 백로그) |
| control replay candidate | 자동 제어 dispatch 실패 | 후보로 집계만 함 |

근거: [ADR-0001 Reliability Addendum](adr/0001-ingestion-architecture.md)

## 7. 설정 키

| 키 | 기본값 | 의미 |
|---|---|---|
| `spring.mqtt.default-topic` | `sousvide/+/status` | uplink 구독 topic |
| `ingestion.channel.mode` | `executor` | `direct` \| `executor` ([ADR-0003](adr/0003-executor-inbound-channel.md)) |
| `ingestion.executor.core-pool-size` / `max-pool-size` / `queue-capacity` | 4 / 16 / 5000 | executor 채널 풀 |
| `ingestion.influx.write-mode` | `strict` | `strict` \| `bypass` (부하 테스트에서 Influx 제외용) |
| `ingestion.heartbeat-ttl-seconds` | 120 | online 판정 TTL |
| `ingestion.duplicate-suppress-window-seconds` | 2 | uplink 중복 억제 창 |
| `control.deadband` | 0.3 | 자동 제어 deadband (전역) |
| `control.auto-command-dedup-window-seconds` | 30 | 자동 명령 idempotency 시간 버킷 |
| `downlink.ack-timeout-seconds` | 30 | ACK timeout → EXPIRED |
| `downlink.retry-interval-seconds` | 10 | 재발행 간격 |
| `downlink.max-retries` | 3 | 재발행 한도 |
| `downlink.reliability.scan-interval-ms` | 5000 | 신뢰성 스케줄러 주기 |
| `watchdog.scan-interval-ms` | 30000 (yml) | watchdog 주기 |
| `watchdog.offline-notify-cooldown-seconds` | 60 | 오프라인 알림 cooldown |
| `APP_ENV` | `local` | 메트릭 `env` 태그 |

새 설정 키를 추가하면 이 표에 함께 추가한다.

## 8. 배포

- 로컬: `docker compose up -d` (Mosquitto, MySQL :3307, Redis, Influx, Prometheus, Grafana) + `./gradlew bootRun`
- 운영: `main` push → `.github/workflows/deploy-klepaas-oracle.yml` (테스트 게이트 → arm64 이미지 → GHCR → K-Le-PaaS 배포 API). 자세한 내용은 [`klepaas-oracle-deployment.md`](klepaas-oracle-deployment.md)
- 비밀값(kubeconfig, 토큰, DB 비밀번호)은 저장소에 커밋하지 않는다. `application.yml`에 있는 값은 로컬 개발용 기본값이다.

## 9. 알려진 격차

스펙을 쓸 때 반드시 확인한다. 해소하면 이 목록에서 지운다.

| 격차 | 영향 | 백로그 |
|---|---|---|
| 서버에 저장된 제어 정책(`control_target_temp`, `control_hysteresis`)이 자동 제어에 **쓰이지 않는다.** `ControlDecisionEngine`은 기기가 보고한 `targetTemp`와 전역 `control.deadband`로 판단한다 | 대시보드에서 정책을 바꿔도 자동 제어 동작이 바뀌지 않는다 | product §6 #1 |
| ACK는 HTTP로만 받는다. MQTT ACK topic과 payload 표준이 없다 | 실제 기기가 ACK를 보낼 경로가 정해져 있지 않다 | 미정 |
| 자동 제어와 수동 제어 사이에 우선순위 정책이 없다 | 수동 명령 직후 자동 명령이 덮어쓸 수 있다 | 미정 |
| uplink 중복 억제가 인스턴스 메모리 기반의 best-effort다 | 다중 인스턴스에서는 효과가 없다 | product §6 #9 |
| replay candidate는 집계만 하고 실제로 재처리하지 않는다 | 저장소 장애 동안의 데이터가 유실된다 | product §6 #9 |
| 신뢰성 스케줄러가 PENDING/SENT 전체를 매 주기 조회한다 | 명령이 쌓이면 스캔 비용이 늘어난다 | product §6 #5 |
| 인증과 소유권이 없다. Prometheus endpoint가 `unrestricted`다 | 누구나 모든 기기를 조회하고 제어할 수 있다 | product §6 #3, #12 |
| 스키마를 `ddl-auto: update`로 관리한다 | 스키마 변경 이력이 남지 않고 롤백이 어렵다 | 미정 |
| fail-safe 이벤트가 로깅만 한다 | 사용자에게 알림이 가지 않는다 | product §6 #8 |
| 테스트가 적다 (파서, 제어, ingestion, watchdog, controller, 서비스 facade) | 신뢰성 스케줄러와 명령 서비스에 전용 테스트가 없다 | #63 |

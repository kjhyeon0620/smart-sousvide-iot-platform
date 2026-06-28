# Device API Contract

HTTP API 계약의 기준 문서다. 계약을 바꾸는 이슈는 스펙에서 먼저 합의한 뒤 이 문서와 코드를 함께 수정한다 ([절차](specs/README.md)). MQTT와 저장소 계약은 [`architecture.md`](architecture.md)에 있다.

## Swagger
- UI: `GET /swagger-ui.html` → `device-api` 그룹을 선택하고 `Try it out`으로 호출한다
- OpenAPI JSON: `GET /v3/api-docs`, 그룹별: `GET /v3/api-docs/device-api`
- Actuator endpoint는 `device-api` 그룹에 포함되지 않는다.
- 요청·응답의 규칙과 에러 코드는 이 문서가 기준이다. Swagger는 수동 호출 도구로 쓴다.

## Endpoints

### 1) POST /devices
- 설명: 디바이스를 등록한다.
- Request body:
```json
{
  "deviceId": "SV-001",
  "name": "bath-1",
  "enabled": true
}
```
- Rules:
  - `deviceId`: required, max 64
  - `name`: optional, max 100
  - `enabled`: optional (미입력 시 `true`)
- Responses:
  - `201 Created`: 생성 성공
  - `409 Conflict`: 중복 `deviceId`
  - `400 Bad Request`: 유효성 실패

### 2) GET /devices/{id}
- 설명: 단일 디바이스를 조회한다.
- Responses:
  - `200 OK`
  - `404 Not Found`

### 3) GET /devices?page={page}&size={size}
- 설명: 디바이스 목록을 페이징 조회한다.
- Default:
  - `page=0`
  - `size=20`
- Response example:
```json
{
  "items": [
    { "id": 1, "deviceId": "SV-001", "name": "bath-1", "enabled": true,
      "createdAt": "2026-03-02T00:00:00Z", "updatedAt": "2026-03-02T00:00:00Z" }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "page": 0,
  "size": 20
}
```
- Responses:
  - `200 OK`

### 4) PATCH /devices/{id}/enabled
- 설명: 디바이스 활성 상태를 변경한다.
- Request body:
```json
{
  "enabled": false
}
```
- Responses:
  - `200 OK`
  - `404 Not Found`
  - `400 Bad Request`

### 5) GET /devices/{id}/status
- 설명: 디바이스 현재 상태 스냅샷을 조회한다.
- Response example:
```json
{
  "id": 1,
  "deviceId": "SV-001",
  "name": "bath-1",
  "enabled": true,
  "lastSeenAt": "2026-03-02T00:00:00Z",
  "online": true,
  "latestTemp": 60.1,
  "latestTargetTemp": 65.0,
  "latestState": "HEATING",
  "latestOccurredAt": "2026-03-02T00:00:00Z"
}
```
- Responses:
  - `200 OK`
  - `404 Not Found`

### 6) GET /devices/{id}/temps?from={ISO8601}&to={ISO8601}&limit={n}
- 설명: 디바이스 온도 시계열 구간을 조회한다.
- Query params:
  - `from` optional (기본: `to - 1h`)
  - `to` optional (기본: `now`)
  - `limit` optional (기본: `200`, 허용 범위 `1..500`, `0`은 기본값 사용)
- Response example:
```json
{
  "devicePk": 1,
  "deviceId": "SV-001",
  "from": "2026-03-02T00:00:00Z",
  "to": "2026-03-02T00:10:00Z",
  "limit": 100,
  "items": [
    {
      "occurredAt": "2026-03-02T00:00:30Z",
      "temp": 60.2,
      "targetTemp": 65.0,
      "state": "HEATING"
    }
  ]
}
```
- Responses:
  - `200 OK`
  - `404 Not Found`
  - `400 Bad Request` (`from > to`, invalid `limit`)

### 7) GET /devices/{id}/control-policy
- 설명: 디바이스 제어 정책을 조회한다.
- Response example:
```json
{
  "devicePk": 1,
  "deviceId": "SV-001",
  "targetTemp": 65.0,
  "hysteresis": 0.3,
  "updatedAt": "2026-03-02T00:00:00Z"
}
```
- Responses:
  - `200 OK`
  - `404 Not Found`

### 8) PATCH /devices/{id}/control-policy
- 설명: 디바이스 제어 정책을 변경한다.
- Request body:
```json
{
  "targetTemp": 64.5,
  "hysteresis": 0.5
}
```
- Rules:
  - `targetTemp`: required, `> 0`, 소수 둘째 자리까지
  - `hysteresis`: required, `> 0`, 소수 둘째 자리까지
- Responses:
  - `200 OK`
  - `404 Not Found`
  - `400 Bad Request` (유효성 실패)

### 9) POST /devices/{id}/commands
- 설명: 디바이스로 다운링크 명령을 발행하고 이력을 저장한다.
- Request body:
```json
{
  "commandType": "HEAT_ON",
  "idempotencyKey": "cmd-20260302-0001"
}
```
- Rules:
  - `commandType`: required (`HEAT_ON`, `HEAT_OFF`, `HOLD`)
  - `idempotencyKey`: required, max 100
- Response example:
```json
{
  "commandId": 10,
  "devicePk": 1,
  "deviceId": "SV-001",
  "commandType": "HEAT_ON",
  "status": "SENT",
  "topic": "devices/SV-001/cmd",
  "payload": "{\"commandId\":10,\"commandType\":\"HEAT_ON\",\"requestedAt\":\"2026-03-02T00:00:00Z\"}",
  "requestedAt": "2026-03-02T00:00:00Z",
  "sentAt": "2026-03-02T00:00:01Z",
  "errorMessage": null
}
```
- Responses:
  - `200 OK` (`SENT`, `FAILED`, 재요청 시 기존 command 반환)
  - `404 Not Found`
  - `400 Bad Request`

### 10) GET /devices/{id}/commands?limit={n}
- 설명: 디바이스 다운링크 명령 이력을 조회한다.
- Query params:
  - `limit` optional (기본: `20`, 허용 범위 `1..100`)
- Responses:
  - `200 OK`
  - `404 Not Found`
  - `400 Bad Request` (invalid `limit`)

### 11) POST /devices/{id}/commands/{commandId}/ack
- 설명: 다운링크 명령 ACK를 반영한다.
- Responses:
  - `200 OK` (`ACKED`)
  - `404 Not Found` (`DEVICE_NOT_FOUND`, `COMMAND_NOT_FOUND`)

## Dev-Only Dashboard Demo API

스펙: [0057](specs/0057-dashboard-demo-data-console/spec.md)

The dashboard demo API is available only when Spring runs with the `local` or `dev` profile. Production profiles do not create the controller bean.

Base path:

```http
/dev/dashboard-demo
```

Safety rules:

- All generated and mutable demo devices use the `SV-DEMO-` prefix.
- `PATCH /dev/dashboard-demo/devices/{deviceId}` rejects non-demo device IDs.
- `POST /dev/dashboard-demo/devices/{deviceId}/commands` rejects non-demo device IDs.
- `DELETE /dev/dashboard-demo` deletes MySQL device and command rows only for `SV-DEMO-*`.
- Redis demo heartbeat, active-device, and offline-notified keys are cleaned.
- InfluxDB demo telemetry deletion is not implemented; fresh timestamped samples are written when scenarios are created.

### GET /dev/dashboard-demo

Returns availability metadata for the frontend panel.

### POST /dev/dashboard-demo/scenario

Request:

```json
{
  "count": 5,
  "scenario": "mixed",
  "baseTargetTemp": 64.5
}
```

Supported scenarios: `mixed`, `heating`, `holding`, `offline`, `command-failure`.

### PATCH /dev/dashboard-demo/devices/{deviceId}

Request:

```json
{
  "online": true,
  "enabled": true,
  "temp": 61.2,
  "targetTemp": 64.5,
  "state": "HEATING"
}
```

### POST /dev/dashboard-demo/devices/{deviceId}/commands

Request:

```json
{
  "statuses": ["PENDING", "SENT", "FAILED", "EXPIRED"]
}
```

### DELETE /dev/dashboard-demo

Clears `SV-DEMO-*` MySQL and Redis demo data. InfluxDB cleanup is documented as a current limit.

## Downlink Reliability Notes
- 근거: [ADR-0002](adr/0002-downlink-command-reliability.md)
- 상태 모델:
  - `PENDING`: 생성됨, 아직 발행 전
  - `SENT`: 발행됨, ACK 대기
  - `ACKED`: ACK 수신 완료
  - `EXPIRED`: ACK timeout 초과
  - `FAILED`: publish 실패 또는 retry 한도 초과
- 기본 정책:
  - `ack-timeout`: `30s` (기본값)
  - `retry-interval`: `10s` (기본값)
  - `max-retries`: `3` (기본값)

## Auto Control Notes
- 주의: `PATCH /devices/{id}/control-policy`로 저장한 정책은 현재 자동 제어 판단에 쓰이지 않는다 ([알려진 격차](architecture.md#9-알려진-격차)).
- telemetry ingestion 경로에서 제어 판단 결과가 자동 downlink command로 연결된다.
- 자동 발행 규칙:
  - `HOLD`는 발행하지 않는다.
  - 등록되고 `enabled=true`인 디바이스만 자동 발행 대상이다.
  - 중복 발행 방지를 위해 시간 버킷 기반 idempotency key를 사용한다.
- 자동 발행 key 예시:
  - `auto:SV-001:HEAT_ON:<bucket>`

## Error Response Contract
```json
{
  "code": "DEVICE_NOT_FOUND",
  "message": "Device not found. id=99",
  "timestamp": "2026-03-02T00:00:00Z"
}
```

## Error Codes
| code | HTTP | 발생 조건 |
|---|---|---|
| `DEVICE_NOT_FOUND` | 404 | 기기 없음 |
| `COMMAND_NOT_FOUND` | 404 | 명령 없음 |
| `DEVICE_DUPLICATE` | 409 | `deviceId` 중복 |
| `INVALID_REQUEST` | 400 | Bean Validation 실패, 조회 파라미터 오류 |

새 에러 코드는 `GlobalApiExceptionHandler`와 이 표에 함께 추가한다.

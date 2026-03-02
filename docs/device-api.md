# Device Management API (Foundation)

## Purpose
- 디바이스 관리 기능의 기본 골격을 제공한다.
- 이후 상태 조회 확장, 다운링크 제어, 운영 정책 API의 기반으로 사용한다.

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

## Downlink Reliability Notes (Phase 5)
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

## Error Response Contract
```json
{
  "code": "DEVICE_NOT_FOUND",
  "message": "Device not found. id=99",
  "timestamp": "2026-03-02T00:00:00Z"
}
```

## Error Codes
- `DEVICE_NOT_FOUND`
- `DEVICE_DUPLICATE`
- `COMMAND_NOT_FOUND`
- `INVALID_REQUEST`

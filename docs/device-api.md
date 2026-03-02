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
- `INVALID_REQUEST`

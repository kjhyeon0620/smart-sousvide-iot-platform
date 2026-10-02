# ADR-0002: Downlink Command Reliability Model

## Status
Accepted (#33, #35, #43에서 구현)

## Context
- 서버에서 기기로 가는 명령(`HEAT_ON`/`HEAT_OFF`/`HOLD`)은 네트워크 단절, broker 장애, 기기 무응답으로 유실되거나 중복될 수 있다.
- 자동 제어는 telemetry가 들어올 때마다 판단하므로 같은 명령이 짧은 간격으로 반복해서 생성될 수 있다.
- 명령 이력은 사용자 대시보드와 운영 분석에서 조회할 수 있어야 한다.

## Decision
- 명령을 MySQL `device_commands`에 먼저 저장한 뒤(`PENDING`) MQTT QoS 1로 발행한다.
- 상태 모델: `PENDING`, `SENT`, `ACKED`, `EXPIRED`, `FAILED`
- `(devicePk, idempotencyKey)`를 유일하게 유지한다. 같은 key로 다시 요청하면 새로 발행하지 않고 기존 명령을 반환한다.
  - 수동 명령: 클라이언트가 key를 제공한다.
  - 자동 명령: `auto:{deviceId}:{commandType}:{epochMillis / dedupWindow}` 형식의 시간 버킷 key를 쓴다 (기본 30초).
- 자동 제어는 `HOLD`를 발행하지 않는다. 미등록 기기와 `enabled=false` 기기는 자동 발행 대상에서 제외한다.
- 스케줄러가 ACK timeout(30s), 재발행 간격(10s), 재발행 한도(3)를 적용한다. ACK는 HTTP endpoint로 반영한다.

## Alternatives
- **저장 없이 fire-and-forget 발행**: 구현은 가장 단순하지만 추적, 재시도, 이력 화면을 만들 수 없어 채택하지 않았다.
- **메시지 큐(Kafka/RabbitMQ)로 명령 전달**: 신뢰성은 높지만 현재 규모에 비해 운영 복잡도가 커서 백로그로 미뤘다.

## Consequences
- 장점: 명령 추적, 재시도, 만료가 가능하고 중복 발행을 억제한다. 대시보드에 명령 이력을 보여줄 수 있다.
- 감수하는 점:
  - 스케줄러가 PENDING/SENT 전체를 스캔한다. 명령이 쌓이면 비용이 커진다.
  - ACK를 HTTP로만 받는다. 기기 쪽 ACK 경로가 표준화되어 있지 않다.
  - 시간 버킷 경계에서는 같은 자동 명령이 한 번 더 생성될 수 있다.

## Revisit When
- 명령 테이블 스캔이 스케줄러 지연의 원인으로 관측될 때 (`nextRetryAt` 인덱스 기반 스캔)
- 실제 펌웨어가 MQTT ACK를 보내게 될 때
- 자동 제어와 수동 제어의 우선순위 정책이 필요해질 때

## References
- [`architecture.md` §3.2, §6.2](../architecture.md)
- [`device-api.md`](../device-api.md) Downlink Reliability Notes

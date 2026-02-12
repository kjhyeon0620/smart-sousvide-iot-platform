# ADR-0001: Ingestion Architecture for MQTT Device Status

## Status
Accepted

## Context
- 목표: 10k+ 디바이스가 송신하는 MQTT 상태 메시지를 안정적으로 수집한다.
- 제약: 로컬 개발 환경(Docker Compose), Spring Integration 기반 MQTT 인바운드 사용.
- 요구: 타입 안정성, 파싱 실패 격리, 시계열 저장/Heartbeat 갱신/제어 분기 분리.

## Decision
- `MqttConsumer`는 수신/위임만 담당한다.
- 메시지 파싱은 `Parser` 컴포넌트로 분리한다.
- 비즈니스 유스케이스는 `DeviceIngestionService`로 통합한다.
- 저장소는 Port/Adapter로 분리한다.
  - InfluxDB: temperature series
  - Redis: device heartbeat(lastSeen)
- 제어 판단은 `ControlDecisionEngine`에서 순수 로직으로 처리한다.

## Consequences
- 장점: 책임 분리로 테스트 용이성/확장성 향상.
- 장점: 고트래픽 상황에서 병목 지점을 계층별로 관측 가능.
- 단점: 초기 구현 클래스 수 증가.

## Follow-up
- 실패 메시지 전용 채널/재처리 정책 도입
- QoS/재전송 정책 및 idempotency 키 설계
- 10k 시뮬레이터 기반 부하 테스트 시나리오 정교화

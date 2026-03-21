# Operations Runbook (Phase 7)

## Purpose
- Grafana 대시보드를 기준으로 ingestion/downlink 이상 징후를 빠르게 분류하고 대응한다.

## Prerequisites
1. 앱 실행 상태
2. Prometheus가 `/actuator/prometheus` 스크랩 중
3. Grafana datasource(`Prometheus`) 연결 완료

## Dashboard Import
1. Grafana > Dashboards > Import
2. `docs/grafana-observability-dashboard.json` 업로드
3. `DS_PROMETHEUS`에 Prometheus datasource 매핑

## Triage Flow
1. Ingestion Throughput 확인
- `mqtt recv/s` 급감 여부 확인

2. Ingestion Failure Ratios 확인
- `parse failure ratio` 상승 시 payload/스키마 변경 의심
- `influx failure ratio` 상승 시 Influx 연결/토큰/지연 점검
- `redis failure ratio` 상승 시 Redis 연결/메모리 점검
- `processing failure ratio` 상승 시 애플리케이션 내부 예외 또는 executor backlog 의심
- `parse dead-letter` 증가 시 non-replayable payload 오류로 분류
- `storage replay candidate` 증가 시 Influx/Redis 복구 후 재처리 후보 증가로 해석
- `control replay candidate` 증가 시 downlink publish/ACK 경로 점검 우선

3. Ingestion Pipeline Success 확인
- `overall pipeline success`가 하락하면 Influx 또는 Redis 경로를 우선 의심
- `core pipeline success`는 유지되고 `overall`만 하락하면 Influx write path 병목 가능성이 높음
- `inflight`가 계속 상승하면 downstream 처리 속도가 입력 속도를 따라가지 못하는 상태로 본다

4. Downlink Command Events 확인
- `failed/s`, `expired/s`, `retried/s` 급증 여부 확인

5. Downlink Reliability Ratios 확인
- `acked ratio` 하락 + `timeout ratio` 상승이면 디바이스 ACK 경로 또는 네트워크 지연 우선 점검

6. System Runtime Overview 확인
- CPU 급등, thread 증가, HTTP req/s 급증 여부 확인

## Incident Scenarios
### 1) Parse Failure 급증
- 증상: `parse failure ratio` > 1%
- 점검:
  - 최근 payload 포맷 변경 여부
  - enum/string 필드 값 변경 여부
  - 배포 직후 발생인지 확인
- 조치:
  - 파서 유효성 규칙과 송신 포맷 동기화
  - `parse dead-letter`를 non-replayable failure로 분류하고 자동 재처리는 수행하지 않음

### 2) Downlink Timeout 급증
- 증상: `timeout ratio` 상승, `acked ratio` 하락
- 점검:
  - ACK endpoint 트래픽 유입 여부
  - 명령 topic 발행 성공 여부(`sent/s` 대비 `acked/s`)
  - retry 증가 동반 여부
- 조치:
  - ACK 경로 우선 복구
  - 필요 시 `downlink.ack-timeout-seconds`, `downlink.retry-interval-seconds` 조정

### 3) Downlink Failed 급증
- 증상: `failed/s` 급증
- 점검:
  - MQTT broker 연결 상태
  - 인증/토픽 권한 설정
  - 앱 로그의 publish exception 확인
- 조치:
  - broker 연결/인증 복구 후 재시도

### 4) Overall Pipeline Success 급락
- 증상: `overall pipeline success` 급락, `core pipeline success`는 유지 또는 상대적으로 높음
- 점검:
  - InfluxDB 상태 및 write latency
  - strict / bypass 모드 설정 확인
  - backend 로그의 Influx write failure 메시지 확인
- 조치:
  - 우선 bypass 모드로 core path를 분리 검증
  - Influx 연결/토큰/스토리지 상태 복구 후 strict 재검증

### 5) Replay Candidate 증가
- 증상: `storage replay candidate` 또는 `control replay candidate` 증가
- 점검:
  - Influx/Redis/broker 상태
  - 최근 deploy 이후 예외 패턴 변화
  - duplicate suppression 증가 여부와 동반되는지 확인
- 조치:
  - parse invalid는 재처리하지 않음
  - storage/control failure는 복구 후 재처리 대상 후보로 별도 취급

## Suggested Thresholds (Initial)
- parse failure ratio: `> 0.01` (1%)
- timeout ratio: `> 0.05` (5%)
- downlink failed/s: 평시 대비 3배 이상
- inflight: 지속 증가 추세면 backlog 의심
- duplicate dropped/s: 평시 대비 급증 시 broker/network duplicate 여부 확인

## Related Docs
- `docs/observability.md`
- `docs/device-api.md`

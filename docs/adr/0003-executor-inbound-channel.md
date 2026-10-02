# ADR-0003: Executor-backed MQTT Inbound Channel

## Status
Accepted (#45에서 도입, #53에서 비교 측정)

## Context
- 기존 direct 채널에서는 broker 수신 스레드가 파싱, 저장, 제어까지 동기로 처리했다. 그래서 downstream이 느려지면 수신 자체가 지연되었다.
- 고동시(1k~3k 연결) 조건에서 core pipeline 성공률이 90% 전후에 머물렀다.

## Decision
- MQTT inbound 채널을 `ExecutorChannel` 기반으로 바꾸어 수신 스레드와 처리 스레드를 분리한다.
- `ingestion.channel.mode=direct|executor`로 전환할 수 있게 유지해 비교 측정이 가능하게 한다. 기본값은 `executor`다.
- 풀 기본값: core 4 / max 16 / queue 5000
- queue wait, rejected, queue depth, active, e2e latency 메트릭을 노출한다.

## Alternatives
- **외부 큐(Kafka 등)로 분리**: 백프레셔와 재처리에는 더 유리하지만 인프라와 운영 부담이 크다. 로컬 executor로 효과를 먼저 확인했다.

## Consequences
- 장점 (bypass 모드, HiveMQ, 1k/2k/3k 연결): core pipeline 성공률이 direct 89~93%에서 executor 97.6~99.6%로 올랐다. strict 모드(Influx 포함)에서도 98.1~99.9%를 유지했다.
- 감수하는 점:
  - 메모리 큐이므로 프로세스가 죽으면 대기 중인 메시지가 유실된다.
  - 큐가 가득 차면 rejected가 발생한다 (측정 중 관측됨). 백프레셔 정책이 아직 없다.
  - 처리 순서가 보장되지 않는다. 같은 기기의 메시지가 순서가 바뀐 채 처리될 수 있다.

## Revisit When
- `iot_ingestion_executor_rejected_total`이 평시에 증가할 때
- 인스턴스를 여러 개로 늘리거나, 유실을 허용할 수 없는 요구가 생길 때 (→ 외부 큐와 DLQ)

## References
- [`load-test-results.md`](../load-test-results.md) Phase 2 Direct vs Executor, Executor Strict Validation
- `docs/loadtest-runs/{direct,executor}-*`

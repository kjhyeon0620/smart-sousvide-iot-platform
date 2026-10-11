---
issue: 70
title: Ingestion executor 포화 시 MQTT 구독 연결 유실 방지
status: done
size: M
type: bugfix
branch: bugfix/#70-ingestion-backpressure-reconnect
---

# Ingestion executor 포화 시 MQTT 구독 연결 유실 방지

## 배경 (2026-10-09 원인 조사)
기존 Phase 2 결과(3,000대, bypass)는 published 183,000건 중 backend recv가 5,371~8,249건이었다. 원인을 확인하려고 수정 전 코드로 이 Mac에서 다시 측정했다. 조건은 3,000대, 1 msg/s, 60초, QoS 1, bypass이고, 실행마다 broker와 backend를 재시작했다.

| 모드 | broker publish 수신 | backend로 전송 | broker 큐 초과 드롭 | backend recv | 산출물 |
|---|---:|---:|---:|---:|---|
| executor (기본값) | 181,500 | 12,021 | 2,943 | 11,981 | `docs/loadtest-runs/baseline-mac-executor-bypass-3000-20261009-153047` |
| direct | 181,500 | 44,787 | 136,713 | 44,787 | `docs/loadtest-runs/baseline-mac-direct-bypass-3000-20261009-153352` |

- broker 수치는 `$SYS/broker/publish/messages/{received,sent,dropped}`에서 읽었다.
- **executor 모드에서 일어난 일**
  1. executor 큐(5,000)가 포화됐다.
  2. `RejectedExecutionHandler`가 던진 `RejectedExecutionException`이 `MessageDeliveryException`으로 감싸져 Paho 콜백 스레드까지 전파됐다.
  3. Paho가 `Lost connection`으로 연결을 닫았다.
  4. 이후 재연결이 없었다. 구독자가 없는 동안 들어온 약 16.6만 건은 어디에도 전달되지 않았다(broker drop 집계에도 잡히지 않음).
  5. 거부는 1건(`executorRejected=1`)이었지만, 이 한 건으로 구독 연결 전체를 잃었다.
- **direct 모드에서 일어난 일**: 연결은 유지됐다. 전송 44,787건과 드롭 136,713건의 합이 publish 수와 정확히 같다. 즉 손실은 전부 Mosquitto의 기본 큐 한도(`max_queued_messages` 1000) 초과다.
- **집계 구간의 영향**: 시뮬레이터 종료 직후와 20초 drain 후의 recv 차이는 executor 0건, direct 568건(1.3%)이다. 주원인이 아니다.
- **3월 기록과의 대조**: 2026-03-23 broker 로그에도 3,000대 실행마다 `Outgoing messages are being dropped for client spring-boot-backend`가 있다. executor 실행은 드롭 2~4초 뒤 backend 연결이 닫혔다. 당시 backend.log가 없어서 거부 예외가 원인인지는 확인할 수 없다.

## 목적 / 성공 조건
- 목적: 부하가 처리 능력을 넘어도 backend가 uplink 구독을 잃지 않게 하고, 발행 대비 수신율을 측정해 근거로 남긴다.
- 기술적 완료:
  - executor가 포화돼도 예외가 Paho 콜백으로 전파되지 않는다. 수신 스레드가 그 메시지를 직접 처리해 수신 속도를 늦춘다(백프레셔).
  - 연결이 끊기면 자동으로 재연결하고 `sousvide/+/status`를 다시 구독한다.
- 사용성·운영:
  - 포화는 `iot_ingestion_executor_rejected_total`로 알 수 있다. 이 카운터의 의미는 "거부되어 호출자가 처리한 횟수"로 바뀐다.
  - 연결 끊김과 재연결은 로그로 알 수 있다.

## 범위
- 포함:
  - `MqttConfig`의 executor 포화 처리 정책 변경
  - MQTT inbound 자동 재연결과 재구독
  - `summarize-ingestion-metrics.sh`에 발행 대비 수신율 추가
  - 3,000대 bypass 재측정 1회, strict 재측정 1회
  - `load-test-results.md`에 새 Phase 추가, 관련 문서 동기화
- 조건부 포함 (Q2): 재측정에서 broker 큐 드롭이 여전히 주된 손실이면 공유 구독(`$share/<group>/sousvide/+/status`)으로 구독 클라이언트 N개를 둔다. 필요하면 Mosquitto 큐 설정도 조정한다.
- 제외:
  - 외부 큐(Kafka 등) 도입
  - persistent session(`cleanSession=false`) 전환
  - direct 모드 동작 변경
  - 시뮬레이터 payload 변경 (범위 밖, 아래 참조)
  - 기존 Phase 기록 삭제

## 입출력·계약
- MQTT: topic과 payload는 바꾸지 않는다. 공유 구독을 도입하면 구독 filter만 `$share/...` 형태가 된다(Q2).
- 저장소: 변경 없음
- 설정 키: 기본안에서는 추가하지 않는다. 공유 구독을 도입하면 구독자 수 키 1개를 추가한다(Q2). 키를 추가하면 `architecture.md` §7에 반영한다.
- 부하 테스트 요약 (`business-summary.json`)에 아래 필드를 추가한다. 기존 필드는 그대로 둔다.
  - `published_total`: 같은 attempt의 `connection-summary.json`에서 읽는다. 파일이 없으면 필드를 생략한다.
  - `backend_receive_rate`, `backend_receive_rate_pct`: `recv_total / published_total`
  - `backend_receive_rate_formula`
  - 기존 `core_pipeline_success_rate`(수신 대비 핵심 처리율)는 그대로 유지한다.

## 제약
- 참조: [architecture](../../architecture.md), [ADR-0003](../../adr/0003-executor-inbound-channel.md)
- ADR-0003의 "백프레셔 정책이 아직 없다"를 해소하는 변경이다. ADR-0003의 Consequences와 Status를 갱신한다.
- 측정 환경이 3월(Windows, i5-1155G7)과 다르다. 수정 전후 비교는 같은 Mac의 위 기준 측정과 한다.
- 이 Mac에서는 6379 포트를 다른 프로젝트가 쓰고 있다. 측정용 Redis는 별도 포트(6380)로 띄우고 `--spring.data.redis.port`로 지정한다.

## 예외·경계 상황
| 상황 | 사용자·운영자가 보는 것 | 시스템 동작 |
|---|---|---|
| executor 큐 포화 | `executor_rejected_total` 증가 | 수신 스레드가 직접 처리한다. 수신이 느려지고 broker 큐에 쌓인다. 큐 한도를 넘으면 broker가 드롭한다 |
| broker 연결 끊김 | 끊김과 재연결 로그 | Paho가 자동 재연결하고 재구독한다. 끊긴 동안 들어온 메시지는 유실된다(`cleanSession=true` 유지) |
| broker 장시간 장애 | 재연결 시도 로그 반복 | 최대 간격까지 늘려 가며 계속 재시도한다 |
| 처리 중 예외 (파싱, 저장) | 기존과 같음 | 기존 신뢰성 분류를 유지한다. 예외를 Paho로 전파하지 않는다 |
| application 종료 중 | - | executor shutdown 이후 들어온 메시지는 처리하지 않아도 된다(호출자 실행 정책은 shutdown 상태에서 버린다) |

## 미결 질문 (Open)
- 없음

## 결정 기록 (Decisions)
- 2026-10-09 Q1 포화 정책 → 호출자 실행(`CallerRunsPolicy` 방식). 메시지를 버리지 않고 수신 속도를 늦춘다. 넘친 부하는 broker 큐로 옮겨 가는 것을 감수한다.
- 2026-10-09 Q2 재측정 후 broker 큐 드롭이 주된 손실이면 → 이번 이슈 안에서 공유 구독(구독자 수 설정 키, 기본값 1)을 추가하고 다시 측정한다. Mosquitto 큐 설정은 그래도 부족할 때만 바꾼다.
- 2026-10-09 Q3 기존 Phase 2 기록과 ADR-0003 수치 → 지우지 않고, "당시 발행 대비 수신율은 약 3~4.5%였음, 원인은 새 Phase 참고"라는 주석을 덧붙인다.
- 2026-10-09 재연결 방식 → Paho `automaticReconnect=true`를 기본값으로 가정한다. 재구독이 adapter에서 자동으로 되는지는 구현 중 확인하고 테스트로 증명한다. (기존 adapter는 실측에서 재연결하지 않았음)
- 2026-10-09 `cleanSession=true`를 유지한다고 가정한다. (persistent session은 broker 저장 부담과 의미가 바뀌어 제외)
- 2026-10-09 측정 범위 → executor(기본 모드)의 bypass 3,000대 1회와 strict 3,000대 1회로 가정한다. direct는 이번 변경과 무관해 다시 측정하지 않는다.
- 2026-10-09 broker `$SYS` 드롭 수 수집은 자동화하지 않고, 측정 시 수동으로 기록해 results에 남긴다고 가정한다. (반복 수요가 생기면 스크립트화)
- 2026-10-09 범위 밖, 별도 이슈 후보: 시뮬레이터가 같은 payload를 2초 안에 반복 발행해 중복 억제(`dupDrop`)에 걸린다. direct 실행에서 4,765건이 걸렸고, 이 때문에 핵심 처리율이 낮게 나온다.
- 2026-10-09 (구현 중) 재연결 동작 → Spring Integration 7.0.1 adapter는 자체 재연결 루프가 없다. `connectionLost()`는 이벤트만 발행하고, 재연결은 Paho `automaticReconnect`에만 의존한다(기본 false). 그래서 Paho `automaticReconnect=true`로 켰다. 재구독은 adapter의 `connectComplete()`가 한다. (소스 확인으로 위 가정을 사실로 정정)
- 2026-10-09 (구현 중) Q2 적용 → 수정 후 첫 측정(구독자 1개)에서 broker 큐 드롭이 39.4%로 주된 손실이었다. 그래서 공유 구독을 추가했다.
  - 설정 키는 기존 MQTT 키와 같은 위치의 `spring.mqtt.subscriber-count`(기본 1)로 했다.
  - 1이면 기존 client-id와 topic을 그대로 쓴다. 2 이상이면 `<client-id>-<i>`와 `$share/ingestion/<topic>`을 쓴다.
  - adapter는 `BeanDefinitionRegistryPostProcessor`로 N개를 등록한다.
- 2026-10-09 (구현 중) 측정용 구독자 수 → 4로 했다(executor core pool과 같게). 다른 값은 측정하지 않았다.
- 2026-10-09 (구현 중) Mosquitto 큐 설정 → 바꾸지 않았다. 남은 드롭(18.2%)은 처리량이 발행량보다 지속적으로 낮아서 생긴다. 고정 60초 테스트에서 큐를 키우면 이 부족이 가려진다.
- 2026-10-09 (구현 중) 수신 대비 핵심 처리율 하락 → 수신량이 늘면서 중복 억제(`dupDrop`)가 늘었다. 실패분은 거의 전부 이것이고 parse/Redis/Influx 실패는 0이다. 수치를 그대로 기록하고, 원인은 범위 밖 이슈 후보로 남긴다.

## 완료 증거
| 증명할 것 | 방법 | 결과 |
|---|---|---|
| 포화 시 예외가 전파되지 않고 호출자가 처리한다 | `MqttConfigTest.saturatedExecutorRunsOnCaller` | 통과 |
| 자동 재연결 설정 | `MqttConfigTest.clientFactoryEnablesAutomaticReconnect`, 재측정 3회 backend 로그 `Lost connection` | 통과, 0회 |
| 공유 구독 등록 | `MqttConfigTest` 3건, 실제 broker 스모크(구독자 1/4개, 8건 발행 → 각 recv 8) | 통과 |
| CI 테스트 게이트 | `./gradlew test --tests "com.iot.IoT.ingestion.*" --tests "com.iot.IoT.control.*" --tests "com.iot.IoT.watchdog.*" --tests "com.iot.IoT.loadtest.*"` | 27건 통과 |
| 요약에 두 지표가 분리되어 나온다 | `bash tests/loadtest/test-summarizers.sh` | 통과 |
| 수신율 변화 (executor + bypass) | 3,000대 재측정 | 6.60% → 60.57%(구독자 1) → 81.77%(구독자 4) |
| strict 경로 | 3,000대 strict 1회(구독자 4) | 수신율 56.41%, Influx 실패 0 |
| 결과 기록 | [`load-test-results.md`](../../load-test-results.md) Phase 3 | 반영 |

## 운영 반영
- (머지 후 배포 workflow 결과. 다음 이슈 PR에서 기록)

## 회고
- 어긋난 점:
  - Phase 2는 수신 대비 처리율만 기록했다. 발행 대비 수신율이 3% 안팎이라는 사실이 결과와 ADR에 드러나지 않았다.
  - executor 포화 시 동작(예외 → 연결 유실)과 재연결 부재가 테스트되지 않았다.
- 원인 분류: 완료 기준 느슨(측정 지표가 수신 이후만 봄), 외부 제약 미인지(Spring Integration 7 adapter의 재연결 방식), 테스트 부재
- 고친 위치:
  - `summarize-ingestion-metrics.sh`가 `backend_receive_rate`를 함께 기록하고, `test-summarizers.sh`로 검증한다.
  - `MqttConfigTest`가 포화 정책과 재연결 설정을 검증한다.
  - `load-test-scenarios.md`에 비교 측정 전 broker와 backend 재시작 규칙을 적었다.
- 범위 밖, 별도 이슈 후보: 시뮬레이터 payload에 timestamp/sequence가 없어 정상 측정이 중복으로 억제된다. 남은 broker 드롭을 줄이기 위한 처리량 개선(메시지당 INFO 로그, Redis 동기 호출)도 후보다.

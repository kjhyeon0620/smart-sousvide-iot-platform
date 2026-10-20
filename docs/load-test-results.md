# MQTT Load Test Results (Baseline)

## Test Environment
- Host: Windows 11, Intel i5-1155G7, RAM 16GB
- Runtime: Java 17, Gradle 9.2.1
- Broker: Docker Compose `mosquitto` only (`tcp://localhost:1883`)
- Simulator: `MqttLoadSimulator` (MemoryPersistence, parallel connect)

## Test Method
- Common params:
  - `messages-per-second=1`
  - `duration-seconds=60`
  - `qos=1`
- Stop criteria:
  - `OutOfMemoryError: unable to create native thread`
  - `pthread_create failed (EAGAIN)`
  - severe host slowdown

## Commands Used
```bash
./gradlew mqttLoadTest --args="--connections=300 --connect-parallelism=100 --messages-per-second=1 --duration-seconds=60 --qos=1"
./gradlew mqttLoadTest --args="--connections=500 --connect-parallelism=100 --messages-per-second=1 --duration-seconds=60 --qos=1"
./gradlew mqttLoadTest --args="--connections=1000 --connect-parallelism=120 --messages-per-second=1 --duration-seconds=60 --qos=1"
./gradlew mqttLoadTest --args="--connections=1500 --connect-parallelism=150 --messages-per-second=1 --duration-seconds=60 --qos=1"
./gradlew mqttLoadTest --args="--connections=2000 --connect-parallelism=180 --messages-per-second=1 --duration-seconds=60 --qos=1"
./gradlew mqttLoadTest --args="--connections=2500 --connect-parallelism=200 --messages-per-second=1 --duration-seconds=60 --qos=1"
```

## Results
| Stage | Connections | Connect Parallelism | Duration(s) | Published | Failed | Throughput (msg/s) | Result |
|---|---:|---:|---:|---:|---:|---:|---|
| 1 | 300 | 100 | 66.959 | 18,300 | 0 | 273.30 | Success |
| 2 | 500 | 100 | 67.124 | 30,500 | 0 | 454.38 | Success |
| 3 | 1000 | 120 | 67.552 | 61,000 | 0 | 903.01 | Success |
| 4 | 1500 | 150 | 68.178 | 91,500 | 0 | 1,342.08 | Success |
| 5 | 2000 | 180 | 69.051 | 122,000 | 0 | 1,766.81 | Success |
| 6 | 2500 | 200 | - | - | - | - | Failed (native thread limit) |

## Failure Evidence (2500)
- `java.lang.OutOfMemoryError: unable to create native thread`
- `pthread_create failed (EAGAIN)`

## Baseline Conclusion
- Stable up to `2000` concurrent clients in current environment.
- `2500` exceeded thread/resource limits before steady publish phase.

## Next Optimization Targets
1. Lower per-client thread overhead in simulator.
2. Split load generation into multi-process runners (e.g., 1250 + 1250).
3. Tune connect parallelism vs host thread ceiling.
4. Re-run 2500 and 3000 with the same metrics schema.

## Round 2 Plan (Distributed Simulator)
- Use `scripts/loadtest/run-distributed.sh` to run multiple simulator processes.
- Partition example for 2500:
  - process A: 1250 with `start-index=0`
  - process B: 1250 with `start-index=1250`
- Keep same `messages-per-second`, `duration`, and `qos` for A/B comparison with baseline.

## Round 2 Execution (Distributed)

### Smoke
- command: `./scripts/loadtest/run-distributed.sh 100 2 30 1 10 1`
- result:
  - parts: 2
  - published_total: 1085
  - failed_total: 15
  - throughput_total: 94.23 msg/s

### 2500 Distributed (1250 x 2)
- command: `./scripts/loadtest/run-distributed.sh 2500 2 120 1 60 1`
- logs: `docs/loadtest-runs/20260213-163238`
- result:
  - process A (`part-0`): success, published=76250, failed=0, throughput=1123.59 msg/s
  - process B (`part-1`): failure with thread ceiling
- failure evidence:
  - `OutOfMemoryError: unable to create native thread`
  - `pthread_create failed (EAGAIN)`

### Round 2 Conclusion
- Distributed mode improved reproducibility and removed local persistence directory side-effects.
- However, on current host and runtime, 2500 total concurrent clients still exceeds native thread limits.

## Round 3 Execution (Adaptive Fallback)

### Script Enhancements
- `run-distributed.sh` now retries automatically on thread-limit failures.
- Fallback strategy:
  1. increase partition count first
  2. then reduce connect parallelism

### Run A
- command: `MAX_ATTEMPTS=4 MIN_PARALLELISM=30 MAX_PARTITIONS=8 ./scripts/loadtest/run-distributed.sh 2500 2 120 1 60 1`
- logs: `docs/loadtest-runs/20260213-165623`
- attempt-1 outcome:
  - part-0: success (`published=76250`, `failed=0`, `throughput=1123.16`)
  - part-1: failed with thread ceiling (`EAGAIN`, `unable to create native thread`)
- fallback moved to attempt-2 (`parallelism 120 -> 60`), but run remained unstable.

### Run B
- command: `MAX_ATTEMPTS=4 MIN_PARALLELISM=30 MAX_PARTITIONS=8 ./scripts/loadtest/run-distributed.sh 2500 3 80 1 60 1`
- logs: `docs/loadtest-runs/20260213-170440`
- attempt-1 outcome:
  - part-1: success (`published=50813`, `failed=0`, `throughput=753.50`)
  - part-0 / part-2: failed with thread ceiling (`EAGAIN`, `unable to create native thread`)
- run was terminated due prolonged unstable state.

### Round 3 Conclusion
- Adaptive fallback improved orchestration, observability, and reproducibility of failure handling.
- Root bottleneck remains unresolved: per-client native thread footprint of current MQTT client model.
- On current host/runtime, `2500` is still not stably repeatable even with distributed and adaptive retry strategy.

## Round 4 Root-Cause Verification (Paho vs HiveMQ)

### Objective
- Verify whether the scaling limit is broker/infrastructure or MQTT client model.

### 1500 Connections Cross-Check
| Model | Command | Result | Evidence |
|---|---|---|---|
| Paho | `SIM_TASK=mqttLoadTest MAX_ATTEMPTS=1 PART_TIMEOUT_SECONDS=300 ./scripts/loadtest/run-distributed.sh 1500 2 120 1 30 1` | Failed | parts exited with `code=143`, Paho logs include `Timed out as no activity` |
| HiveMQ | `SIM_TASK=mqttLoadTestHive MAX_ATTEMPTS=1 PART_TIMEOUT_SECONDS=300 ./scripts/loadtest/run-distributed.sh 1500 2 120 1 30 1` | Success | `published_total=46500`, `failed_total=0`, `throughput_total=1550.00` |

### HiveMQ Scale-Up Validation
| Connections | Partitions | Parallelism | Published | Failed | Throughput (msg/s) | Result | Run ID |
|---:|---:|---:|---:|---:|---:|---|---|
| 2000 | 3 | 120 | 61,334 | 0 | 2,044.46 | Success | `20260213-185957` |
| 2500 | 3 | 120 | 77,500 | 0 | 2,583.34 | Success | `20260213-190212` |
| 3000 | 4 | 120 | 93,000 | 0 | 3,100.00 | Success | `20260213-190513` |

### Round 4 Conclusion
- The immediate bottleneck is the Paho client model behavior under high concurrency on this host/runtime.
- Broker/infrastructure is not the primary blocker because HiveMQ path scaled to 3000 with zero failures.
- Default high-traffic baseline should use HiveMQ simulator path; Paho path remains as comparison and technical debt item.

## Phase F PR1 Split Table (HiveMQ 5k)

Source artifacts:
- `docs/loadtest-runs/<run-id>/attempt-<n>/connection-summary.json`
- `docs/loadtest-runs/<run-id>/attempt-<n>/business-summary.json`

| Run ID | Attempt | Command | Connection Published | Connection Failed | Connection Success(%) | Connection Throughput(msg/s) | Business recv | Business overall pipeline success | Business overall Success(%) | Business core pipeline success | Business core Success(%) | Parse Fail | Influx Fail | Influx Bypass | Redis Fail | Notes |
|---|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| `pr1-5k-20260219-113748` | `1` | `SIM_TASK=mqttLoadTestHive RUN_ID=pr1-5k-20260219-113748 MAX_ATTEMPTS=1 PART_TIMEOUT_SECONDS=540 ./scripts/loadtest/run-distributed.sh 5000 5 120 1 60 1` | `305000` | `0` | `100.00` | `5083.35` | `680` | `0` | `0.00` | `0` | `0.00` | `0` | `680` | `0` | `0` | `PR1 measured (strict mode, Influx write path failed)` |

Formulas:
- overall pipeline success = `min(parse_ok_total, influx_ok_total, redis_ok_total) / recv_total`
- core pipeline success = `min(parse_ok_total, redis_ok_total) / recv_total`

## Phase F PR2 Runbook (HiveMQ 10k, bypass mode)
```bash
# Backend must run with ingestion.influx.write-mode=bypass
SIM_TASK=mqttLoadTestHive RUN_ID="pr2-hive-10k-$(date +%Y%m%d-%H%M%S)" MAX_ATTEMPTS=1 PART_TIMEOUT_SECONDS=780 REQUIRE_BUSINESS_SUMMARY=1 ./scripts/loadtest/run-distributed.sh 10000 8 120 1 60 1
```

Use the same split-table schema above for PR2 rows, with `Influx Bypass` and `Business core pipeline success` as required columns.

## Visual Dashboard (MVP)
- Open `docs/loadtest-dashboard.html` in a browser.
- After each load run, dashboard manifest is refreshed automatically.
- Click `Refresh List`, then select `run` + `attempt` and click `Load Selected`.
- Upload two files from the same run attempt:
  - `docs/loadtest-runs/<run-id>/attempt-<n>/connection-summary.json`
  - `docs/loadtest-runs/<run-id>/attempt-<n>/business-summary.json`
- Dashboard shows:
  - connection success and throughput
  - business overall/core success split
  - parse/influx/redis failure counters and bypass count

## Phase 2 Baseline Change
- Backend ingestion path now uses executor-backed dispatch instead of direct synchronous channel handoff.
- Additional metrics were added to classify backend saturation:
  - `processing_failure_total`
  - `overall_pipeline_success_total`
  - `core_pipeline_success_total`
  - `inflight`
  - ingestion processing latency timer
- Re-run the 5k HiveMQ validation after this change and append strict/bypass comparison rows with the updated metric set.

## Phase 2 Direct vs Executor (Bypass Mode)

### Objective
- Compare `direct` and `executor` channel modes under the same bypass-mode load.
- Isolate parse + Redis + control path from Influx write pressure.

### Test Conditions
- Backend config:
  - `ingestion.influx.write-mode=bypass`
  - `ingestion.channel.mode=direct|executor`
- Simulator:
  - `SIM_TASK=mqttLoadTestHive`
  - `messages-per-second=1`
  - `duration-seconds=60`
  - `qos=1`
- Measurement focus:
  - connection throughput
  - business `core_pipeline_success_rate`
  - executor queue wait presence

### Commands
```bash
# direct + bypass
SIM_TASK=mqttLoadTestHive RUN_ID="direct-bypass-1000-$(date +%Y%m%d-%H%M%S)" MAX_ATTEMPTS=1 PART_TIMEOUT_SECONDS=300 REQUIRE_BUSINESS_SUMMARY=1 ./scripts/loadtest/run-distributed.sh 1000 2 120 1 60 1
SIM_TASK=mqttLoadTestHive RUN_ID="direct-bypass-2000-$(date +%Y%m%d-%H%M%S)" MAX_ATTEMPTS=1 PART_TIMEOUT_SECONDS=360 REQUIRE_BUSINESS_SUMMARY=1 ./scripts/loadtest/run-distributed.sh 2000 3 120 1 60 1
SIM_TASK=mqttLoadTestHive RUN_ID="direct-bypass-3000-$(date +%Y%m%d-%H%M%S)" MAX_ATTEMPTS=1 PART_TIMEOUT_SECONDS=420 REQUIRE_BUSINESS_SUMMARY=1 ./scripts/loadtest/run-distributed.sh 3000 4 120 1 60 1

# executor + bypass
SIM_TASK=mqttLoadTestHive RUN_ID="executor-bypass-1000-$(date +%Y%m%d-%H%M%S)" MAX_ATTEMPTS=1 PART_TIMEOUT_SECONDS=300 REQUIRE_BUSINESS_SUMMARY=1 ./scripts/loadtest/run-distributed.sh 1000 2 120 1 60 1
SIM_TASK=mqttLoadTestHive RUN_ID="executor-bypass-2000-$(date +%Y%m%d-%H%M%S)" MAX_ATTEMPTS=1 PART_TIMEOUT_SECONDS=360 REQUIRE_BUSINESS_SUMMARY=1 ./scripts/loadtest/run-distributed.sh 2000 3 120 1 60 1
SIM_TASK=mqttLoadTestHive RUN_ID="executor-bypass-3000-$(date +%Y%m%d-%H%M%S)" MAX_ATTEMPTS=1 PART_TIMEOUT_SECONDS=420 REQUIRE_BUSINESS_SUMMARY=1 ./scripts/loadtest/run-distributed.sh 3000 4 120 1 60 1
```

### Results
| Mode | Connections | Run ID | Published | Failed | Throughput (msg/s) | Business recv | Core pipeline success | Core success(%) | Notes |
|---|---:|---|---:|---:|---:|---:|---:|---:|---|
| direct | 1000 | `direct-bypass-1000-20260323-013750` | `61000` | `0` | `1016.66` | `14532` | `12983` | `89.34` | bypass mode |
| direct | 2000 | `direct-bypass-2000-20260323-014108` | `122000` | `0` | `2033.34` | `7915` | `7084` | `89.50` | bypass mode |
| direct | 3000 | `direct-bypass-3000-retry-20260323-014826` | `183000` | `0` | `3050.00` | `8249` | `7633` | `92.53` | bypass mode |
| executor | 1000 | `executor-bypass-1000-20260323-020032` | `60500` | `0` | `1008.33` | `10831` | `10572` | `97.61` | queue wait measured |
| executor | 2000 | `executor-bypass-2000-20260323-020305` | `122000` | `0` | `2033.34` | `5849` | `5777` | `98.77` | queue wait measured |
| executor | 3000 | `executor-bypass-3000-20260323-020536` | `183000` | `0` | `3050.00` | `5371` | `5349` | `99.59` | queue wait measured |

> **2026-10-09 주석 (#70)**: 이 표의 core success는 `recv_total`을 분모로 한 값이다. 당시 3000대의 발행 대비 backend 수신율은 약 2.9~4.5%(executor 5,371, direct 8,249 / 183,000)였다. 1000대도 약 18~24%였다. 원인(executor 포화 → 구독 연결 유실, broker 큐 초과 드롭)과 재측정은 [Phase 3](#phase-3-receive-rate-recovery-70)을 참고한다.

### Comparison Summary
| Connections | Direct core success(%) | Executor core success(%) | Improvement (%p) |
|---:|---:|---:|---:|
| 1000 | `89.34` | `97.61` | `+8.27` |
| 2000 | `89.50` | `98.77` | `+9.27` |
| 3000 | `92.53` | `99.59` | `+7.06` |

### Interpretation
- Connection-level throughput stayed stable across both modes up to `3050 msg/s`.
- In bypass mode, `executor` consistently improved `core_pipeline_success_rate` over `direct`.
- The measured gain range was `+7.06%p` to `+9.27%p`.
- `executor` mode produced observable queue wait metrics, which confirms that the broker receive thread and downstream processing were decoupled.
- `executor_rejected_total` was observed during the executor test session, so saturation guard behavior should still be tracked in follow-up runs.

### Measurement Notes
- In bypass mode, `overall pipeline success` remains `0` by formula because Influx actual write success is intentionally excluded.
- For bypass-mode comparisons, use `core_pipeline_success_rate` as the primary business metric.
- Source artifacts:
  - `docs/loadtest-runs/direct-bypass-1000-20260323-013750/attempt-1/*`
  - `docs/loadtest-runs/direct-bypass-2000-20260323-014108/attempt-1/*`
  - `docs/loadtest-runs/direct-bypass-3000-retry-20260323-014826/attempt-1/*`
  - `docs/loadtest-runs/executor-bypass-1000-20260323-020032/attempt-1/*`
  - `docs/loadtest-runs/executor-bypass-2000-20260323-020305/attempt-1/*`
  - `docs/loadtest-runs/executor-bypass-3000-20260323-020536/attempt-1/*`

## Phase 2 Executor Strict Validation

### Objective
- Validate the full ingestion path with Influx write enabled after bypass-mode comparison.
- Confirm whether the executor-based path remains stable when actual Influx persistence is included.

### Preconditions
- `ingestion.channel.mode=executor`
- `ingestion.influx.write-mode=strict`
- Influx direct write probe succeeded with `HTTP 204`

Direct write probe:
```bash
curl -i -XPOST "http://localhost:8086/api/v2/write?org=myorg&bucket=sousvide_bucket&precision=s" \
  -H "Authorization: Token my-super-secret-auth-token" \
  --data-raw "strict_probe,deviceId=SV-TEST temp=60.5"
```

### Commands
```bash
SIM_TASK=mqttLoadTestHive RUN_ID="executor-strict-1000-clean-$(date +%Y%m%d-%H%M%S)" MAX_ATTEMPTS=1 PART_TIMEOUT_SECONDS=300 ./scripts/loadtest/run-distributed.sh 1000 2 120 1 60 1
SIM_TASK=mqttLoadTestHive RUN_ID="executor-strict-2000-clean-retry-$(date +%Y%m%d-%H%M%S)" MAX_ATTEMPTS=1 PART_TIMEOUT_SECONDS=360 ./scripts/loadtest/run-distributed.sh 2000 3 120 1 60 1
SIM_TASK=mqttLoadTestHive RUN_ID="executor-strict-3000-clean-retry-$(date +%Y%m%d-%H%M%S)" MAX_ATTEMPTS=1 PART_TIMEOUT_SECONDS=420 ./scripts/loadtest/run-distributed.sh 3000 4 120 1 60 1
```

### Results
| Mode | Connections | Run ID | Published | Failed | Throughput (msg/s) | Business recv | Influx success | Influx failure | Overall pipeline success | Overall success(%) | Notes |
|---|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|---|
| executor + strict | 1000 | `executor-strict-1000-clean-20260323-022447` | `61000` | `0` | `1016.66` | `5888` | `5776` | `0` | `5776` | `98.10` | manual business summary generation |
| executor + strict | 2000 | `executor-strict-2000-clean-retry-20260323-023138` | `122000` | `0` | `2033.34` | `5208` | `5204` | `0` | `5204` | `99.92` | manual business summary generation |
| executor + strict | 3000 | `executor-strict-3000-clean-retry-20260323-023509` | `183000` | `0` | `3050.00` | `5346` | `5323` | `0` | `5323` | `99.57` | manual business summary generation |

### Interpretation
- After restoring valid Influx authorization, the strict path completed with `0` Influx write failures in all measured runs.
- Executor-based ingestion remained stable up to `3050 msg/s` connection throughput with `99.57%` to `99.92%` overall pipeline success in the measured strict runs.
- This confirms that the executor-based channel not only improves the bypass-mode core path, but also sustains high success rates when actual Influx persistence is included.

> **2026-10-09 주석 (#70)**: overall success는 `recv_total` 기준이다. 3000대 strict의 발행 대비 수신율은 약 2.9%(5,346 / 183,000)였다. [Phase 3](#phase-3-receive-rate-recovery-70) 참고.

### Strict Validation Notes
- For the strict reruns, `business-summary.json` for the later runs was generated by copying the completed `backend.log` into the attempt directory and running `scripts/loadtest/summarize-ingestion-metrics.sh` manually.
- Source artifacts:
  - `docs/loadtest-runs/executor-strict-1000-clean-20260323-022447/attempt-1/*`
  - `docs/loadtest-runs/executor-strict-2000-clean-retry-20260323-023138/attempt-1/*`
  - `docs/loadtest-runs/executor-strict-3000-clean-retry-20260323-023509/attempt-1/*`

## Phase 3 Receive Rate Recovery (#70)

### Objective
- 발행 대비 backend 수신율(`recv / published`)이 낮은 원인을 확인하고, 최소 수정 후 같은 부하로 다시 측정한다.
- 스펙: [`docs/specs/0070-ingestion-backpressure-reconnect/spec.md`](specs/0070-ingestion-backpressure-reconnect/spec.md)

### Test Conditions
- Host: macOS, 10 CPU cores, RAM 32GB, Java 17.0.20, Mosquitto 2.0.22 (Docker). **Phase 2(Windows, i5-1155G7)와 호스트가 다르다.** 수정 전후 비교는 아래 baseline 행(같은 Mac, 수정 전 코드)과 한다.
- Simulator: `SIM_TASK=mqttLoadTestHive ./scripts/loadtest/run-distributed.sh 3000 4 120 1 60 1` (3,000대, 1 msg/s, 60초, QoS 1)
- Backend: `java -jar` 실행. 매 실행마다 broker와 backend를 재시작해 카운터를 0에서 시작했다.
- Mosquitto 설정은 바꾸지 않았다 (기본 `max_queued_messages=1000`, `max_inflight_messages=20`).
- broker 수치는 실행 직후 `$SYS/broker/publish/messages/{received,sent,dropped}`에서 수동으로 읽었다.
- recv는 `run-distributed.sh`가 시뮬레이터 종료 직후 만든 `business-summary.json` 값이다. 20초 drain 후 값과의 차이는 direct baseline 568건, strict 286건, 나머지 0건이었다.

### Root Cause (before fix)
- executor 모드: executor 큐(5,000)가 포화되자 `RejectedExecutionException`이 Paho 콜백까지 전파됐다. 이어서 `Lost connection`이 났다. Spring Integration 7 adapter는 자체 재연결 루프가 없고 Paho `automaticReconnect`는 꺼져 있어서, 연결이 다시 붙지 않았다. 이후 약 16.6만 건은 구독자가 없어 전달되지 않았다.
- direct 모드: 연결은 유지됐다. 손실 136,713건은 모두 broker 큐 초과 드롭이었다 (전송 + 드롭 = publish).

### Changes
1. executor 포화 시 예외를 던지지 않고 호출자(MQTT 수신 스레드)가 처리한다. 수신이 느려지고, 넘친 부하는 broker 큐로 옮겨 간다.
2. Paho `automaticReconnect=true`. 재연결 후 adapter의 `connectComplete()`가 재구독한다.
3. `spring.mqtt.subscriber-count` (기본 1). 2 이상이면 `$share/ingestion/<topic>` 공유 구독 클라이언트 N개로 나눈다. 측정에서는 4를 썼다 (executor core pool 크기와 같게 잡았고, 다른 값은 측정하지 않았다).

### Results
| Run | Code | Mode | Subscribers | Published | broker sent | broker dropped | Backend recv | Receive rate (recv/published) | Core success (core/recv) | Executor caller-run | dupDrop | Backend disconnect | Run ID |
|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|---|
| baseline | before | executor + bypass | 1 | `181500` | `12021` | `2943` | `11981` | `6.60%` | `98.09%` | `1` (rejected, thrown) | `229` | yes (no reconnect) | `baseline-mac-executor-bypass-3000-20261009-153047` |
| baseline | before | direct + bypass | 1 | `181500` | `44787` | `136713` | `44219` | `24.36%` | `89.28%` | - | `4765` | no | `baseline-mac-direct-bypass-3000-20261009-153352` |
| fix | after | executor + bypass | 1 | `181500` | `109926` | `71574` | `109926` | `60.57%` | `86.79%` | `125` | `14518` | no | `fix-executor-bypass-3000-20261009-163700` |
| fix | after | executor + bypass | 4 (shared) | `181500` | `148406` | `33094` | `148406` | `81.77%` | `80.66%` | `18109` | `28699` | no | `fix-shared4-executor-bypass-3000-20261009-164102` |
| fix | after | executor + strict | 4 (shared) | `182250` | `103099` | `79151` | `102813` | `56.41%` | `85.24%` (overall `85.24%`) | `19899` | `15236` | no | `fix-shared4-executor-strict-3000-20261009-164344` |

- strict 실행의 Influx 쓰기 실패는 `0`건이다 (`influx_ok_total=87643`).
- strict는 수정 전 baseline을 이 Mac에서 측정하지 않았다. Phase 2 strict(Windows)와 직접 비교하지 않는다.

### Interpretation
- 수신율 손실의 주원인은 구독 연결 유실이었다. 백프레셔와 재연결만으로 executor + bypass 수신율이 `6.60%`에서 `60.57%`로 올랐다.
- 공유 구독 4개로 나누자 `81.77%`까지 올랐다. 남은 손실 `18.2%`는 broker 큐 초과 드롭이다. 이 호스트에서 backend 처리량(약 2,400 msg/s)이 발행량(약 3,025 msg/s)보다 낮아 생긴다.
- strict 모드는 Influx 쓰기가 더해져 처리량이 낮아지고, 수신율이 `56.41%`다.
- **수신 대비 핵심 처리율은 오히려 내려갔다** (`98.09%` → `80.66%`). 실패분은 거의 전부 중복 억제(`dupDrop`)였고, parse/Redis/Influx 실패는 `0`이다. 시뮬레이터 payload에 timestamp나 sequence가 없고 온도가 0.1 단위로 반올림되어, 같은 기기의 서로 다른 측정이 2초 창 안에서 동일 payload로 판정된다. 수신량이 늘수록 이 판정이 늘어난다. 이 지표를 개선 근거로 쓰지 않는다.

### Limitations
- 1회씩 측정했다. 반복 측정 편차는 확인하지 않았다.
- broker 큐 설정을 조정하지 않았다. 고정 60초 테스트에서 큐를 키우면 지속적인 처리량 부족이 가려지기 때문이다.
- 공유 구독을 쓰면 같은 기기의 메시지 처리 순서가 보장되지 않는다 (executor 모드에서도 이미 보장되지 않았다).
- 남은 drop을 줄이려면 처리량 자체(메시지당 INFO 로그, Redis 동기 호출 등)를 개선하거나 브로커 측 흐름 제어를 검토해야 한다. 이번 범위 밖이다.


## Anomaly Evaluation Overhead (#75)

### Objective
- ingestion에 추가한 이상 판정 단계(`DeviceAnomalyService.evaluate`)가 메시지 처리 지연을 늘리는지 확인한다.
- 스펙: [`docs/specs/0075-rule-based-anomaly-detection/spec.md`](specs/0075-rule-based-anomaly-detection/spec.md)

### Test Conditions
- Host: #70과 같은 Mac (10 CPU cores, RAM 32GB, Java 17), Mosquitto 2.0 (Docker)
- Simulator: `SIM_TASK=mqttLoadTestHive ./scripts/loadtest/run-distributed.sh 3000 4 120 1 60 1`
- Backend: `java -jar`, `executor` + `bypass`, 구독자 1개. 차이는 `--anomaly.enabled=true|false` 하나다. 매 실행마다 broker와 backend를 재시작했다.
- 지연 percentile은 측정할 때만 `--management.metrics.distribution.percentiles.iot.ingestion.{processing,e2e}.latency=0.5,0.95,0.99`로 켜고, 시뮬레이터 종료 직후 `/actuator/prometheus`에서 읽었다 (`prometheus-latency.txt`).
- 시뮬레이터 기기는 미등록이고 60초 안에 이상 조건이 성립하지 않는다. 측정 대상은 판정 계산 자체의 비용이다 (opened/resolved 0건).

### Results
| Run | anomaly | Published | Backend recv | Receive rate | processing p50 | processing p95 | processing p99 | e2e p95 | Run ID |
|---|---|---:|---:|---:|---:|---:|---:|---:|---|
| 1 | off | `181500` | `110298` | `60.77%` | `3.9ms` | `10.0ms` | `37.7ms` | `503ms` | `anomaly-off-executor-bypass-3000-20261020-213506` |
| 2 | on | `180750` | `153517` | `84.93%` | `3.7ms` | `8.9ms` | `27.3ms` | `839ms` | `anomaly-on-executor-bypass-3000-20261020-213636` |
| 3 | off | `181500` | `107639` | `59.31%` | `3.8ms` | `11.0ms` | `50.3ms` | `386ms` | `anomaly-off2-executor-bypass-3000-20261020-213839` |
| 4 | on | `181500` | `75013` | `41.33%` | `3.9ms` | `9.4ms` | `65.0ms` | `285ms` | `anomaly-on2-executor-bypass-3000-20261020-214022` |

### Interpretation
- 메시지 처리 지연(processing)은 on/off 차이가 실행 간 편차보다 작다 (p50 `3.7~3.9ms`, p95 `8.9~11.0ms`). 판정 단계의 오버헤드는 이 측정으로 구분되지 않는다.
- **수신율은 A/B 지표로 쓸 수 없다.** 같은 조건에서 `41.33%`~`84.93%`로 흔들렸다. 이 호스트에서 backend 처리량이 발행량보다 낮아 broker 큐 드롭이 생기는 포화 상태라, 드롭 양이 실행마다 크게 달라진다. #70의 단일 측정 수신율(`60.57%`)도 이 편차 범위 안에서 해석해야 한다.
- e2e 지연은 대기열 길이에 따라 달라져(수신량이 많을수록 길다) 판정 비용 비교에 쓰지 않는다.

### Limitations
- 각 조건 2회씩이다. 지연 percentile은 Micrometer 클라이언트 측 추정값(감쇠 창)이다.
- 이상 전이가 일어나는 부하(MySQL 쓰기 동반)는 측정하지 않았다. 전이는 기기당 드물게 일어난다는 전제다.

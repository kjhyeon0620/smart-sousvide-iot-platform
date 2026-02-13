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

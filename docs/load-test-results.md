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

### Strict Validation Notes
- For the strict reruns, `business-summary.json` for the later runs was generated by copying the completed `backend.log` into the attempt directory and running `scripts/loadtest/summarize-ingestion-metrics.sh` manually.
- Source artifacts:
  - `docs/loadtest-runs/executor-strict-1000-clean-20260323-022447/attempt-1/*`
  - `docs/loadtest-runs/executor-strict-2000-clean-retry-20260323-023138/attempt-1/*`
  - `docs/loadtest-runs/executor-strict-3000-clean-retry-20260323-023509/attempt-1/*`

# MQTT Load Test Scenarios (Local)

## Pre-check
```bash
docker compose ps
```

Run with at least Mosquitto up:
- `smart-sousvide-mqtt` on `localhost:1883`

## Current Execution Model
- Use `scripts/loadtest/run-distributed.sh`.
- Script does one-time runtime preparation, then runs each partition via direct `java -cp ...` execution.
- This avoids per-partition Gradle startup overhead.

## Common Arguments
```bash
./scripts/loadtest/run-distributed.sh <totalConnections> <partitions> <connectParallelism> <mps> <durationSec> <qos>
```

## Key Environment Variables
- `SIM_TASK`
  - `mqttLoadTest`: Paho simulator
  - `mqttLoadTestHive`: HiveMQ simulator
- `RUN_ID` (optional; default: timestamp `YYYYMMDD-HHMMSS`)
- `MAX_ATTEMPTS` (default: 4)
- `MIN_PARALLELISM` (default: 40)
- `MAX_PARTITIONS` (default: 6)
- `PART_TIMEOUT_SECONDS` (default: `duration + 120`)
- `REQUIRE_BUSINESS_SUMMARY` (default: 0; set `1` to fail attempt when split aggregation artifacts are missing)

## Recommended Validation Sequence
1. Paho smoke
```bash
SIM_TASK=mqttLoadTest MAX_ATTEMPTS=1 PART_TIMEOUT_SECONDS=180 ./scripts/loadtest/run-distributed.sh 200 1 40 1 10 1
```

2. Model cross-check at same load
```bash
SIM_TASK=mqttLoadTest MAX_ATTEMPTS=1 PART_TIMEOUT_SECONDS=300 ./scripts/loadtest/run-distributed.sh 1500 2 120 1 30 1
SIM_TASK=mqttLoadTestHive MAX_ATTEMPTS=1 PART_TIMEOUT_SECONDS=300 ./scripts/loadtest/run-distributed.sh 1500 2 120 1 30 1
```

3. HiveMQ scale-up baseline
```bash
SIM_TASK=mqttLoadTestHive MAX_ATTEMPTS=1 PART_TIMEOUT_SECONDS=360 ./scripts/loadtest/run-distributed.sh 2000 3 120 1 30 1
SIM_TASK=mqttLoadTestHive MAX_ATTEMPTS=1 PART_TIMEOUT_SECONDS=420 ./scripts/loadtest/run-distributed.sh 2500 3 120 1 30 1
SIM_TASK=mqttLoadTestHive MAX_ATTEMPTS=1 PART_TIMEOUT_SECONDS=480 ./scripts/loadtest/run-distributed.sh 3000 4 120 1 30 1
```

## Phase F PR1 (HiveMQ 5k)
```bash
RUN_ID="pr1-hive-5k-$(date +%Y%m%d-%H%M%S)"
ATTEMPT_DIR="docs/loadtest-runs/${RUN_ID}/attempt-1"
BACKEND_SERVICE="<backend-service>"

# Start backend log collection before run; collector writes once attempt dir exists.
(
  until [[ -d "$ATTEMPT_DIR" ]]; do sleep 1; done
  docker compose logs -f "$BACKEND_SERVICE" 2>&1 | tee "$ATTEMPT_DIR/backend.log"
) &
BACKEND_LOG_PID=$!

SIM_TASK=mqttLoadTestHive RUN_ID="$RUN_ID" MAX_ATTEMPTS=1 PART_TIMEOUT_SECONDS=540 REQUIRE_BUSINESS_SUMMARY=1 ./scripts/loadtest/run-distributed.sh 5000 5 120 1 60 1

kill "$BACKEND_LOG_PID"
wait "$BACKEND_LOG_PID" 2>/dev/null || true
```

## Phase F PR2 (HiveMQ 10k, Influx bypass mode)
Backend config requirement (before run):
- set `ingestion.influx.write-mode=bypass`
- default is `strict`; PR2 load target uses `bypass` to isolate parse+redis/control path from Influx write pressure

```bash
RUN_ID="pr2-hive-10k-$(date +%Y%m%d-%H%M%S)"
ATTEMPT_DIR="docs/loadtest-runs/${RUN_ID}/attempt-1"
BACKEND_SERVICE="<backend-service>"

# Start backend log collection before run; collector writes once attempt dir exists.
(
  until [[ -d "$ATTEMPT_DIR" ]]; do sleep 1; done
  docker compose logs -f "$BACKEND_SERVICE" 2>&1 | tee "$ATTEMPT_DIR/backend.log"
) &
BACKEND_LOG_PID=$!

SIM_TASK=mqttLoadTestHive RUN_ID="$RUN_ID" MAX_ATTEMPTS=1 PART_TIMEOUT_SECONDS=780 REQUIRE_BUSINESS_SUMMARY=1 ./scripts/loadtest/run-distributed.sh 10000 8 120 1 60 1

kill "$BACKEND_LOG_PID"
wait "$BACKEND_LOG_PID" 2>/dev/null || true
```

If backend runs outside Docker Compose, replace the `docker compose logs ...` command with your backend log source (for example `tail -F /path/to/backend.log | tee "$ATTEMPT_DIR/backend.log"`).

Expected artifacts:
- per-part logs: `docs/loadtest-runs/<run-id>/attempt-<n>/part-*.log`
- connection summary: `docs/loadtest-runs/<run-id>/attempt-<n>/connection-summary.json`
- business summary: `docs/loadtest-runs/<run-id>/attempt-<n>/business-summary.json`
- backend ingest log source: `docs/loadtest-runs/<run-id>/attempt-<n>/backend.log`

## Metrics To Record
- `published_total`
- `failed_total`
- `throughput_total(msg/sec)`
- `recv_total`
- `parse_ok_total`, `parse_fail_total`
- `influx_ok_total`, `influx_fail_total`, `influx_bypass_total`
- `redis_ok_total`, `redis_fail_total`
- `pipeline_success_total` (overall)
- `core_pipeline_success_total` (parse+redis)
- per-part logs: `docs/loadtest-runs/<run-id>/attempt-<n>/part-*.log`
- failure signatures:
  - `unable to create native thread`
  - `pthread_create failed (EAGAIN)`
  - `Timed out as no activity` (Paho keepalive path)

## Interpretation Rule
- If Paho fails and HiveMQ succeeds under same parameters, classify as client-model bottleneck.
- Use HiveMQ path as default baseline for further backend capacity validation.

Split aggregation formulas:
- connection success rate = `published_total / (published_total + failed_total)`
- business pipeline success rate = `min(parse_ok_total, influx_ok_total, redis_ok_total) / recv_total`
- business core pipeline success rate = `min(parse_ok_total, redis_ok_total) / recv_total`

## Service Objectives (SLO)
- Ingestion success rate: `>= 99.9%`
- Parse failure rate: `< 0.1%`
- Influx write failure rate: `< 0.5%`
- Redis heartbeat failure rate: `< 0.5%`
- p95 end-to-end ingest latency: `< 200ms` (local benchmark target)

## Reliability Plan (No Schedule Change)
- Keep current phase order, but add reliability gates to each phase:
1. DLQ-ready failure classification
2. replay/retry strategy evidence
3. idempotency policy for duplicate telemetry

Definition notes:
- DLQ-ready: parse/storage failures can be separated and counted independently.
- Idempotency policy: `deviceId + timestamp(or sequence)` key strategy documented and testable.

## Performance Methodology
- Separate metrics into:
1. connection-level success (client connect/publish)
2. business-level success (parse/influx/redis pipeline)
- Run both open-loop and closed-loop style checks:
  - open-loop: fixed input rate, observe drop/failure behavior
  - closed-loop: feedback-limited publish, observe saturation point
- Always include model cross-check (`Paho vs HiveMQ`) before claiming infra bottleneck.

## Chaos and Failure Injection
- Mandatory scenarios:
1. Redis unavailable during ingestion
2. Influx write latency/failure spike
3. Broker restart during sustained load
- Required outputs per scenario:
  - failure signature
  - service behavior (degrade/recover)
  - MTTR measurement

## Security and Financial-Grade Readiness
- Add and track minimum controls:
1. MQTT TLS enablement plan
2. topic ACL policy
3. secret management path
4. audit log policy (who/when/what for control and fail-safe events)

## Portfolio Deliverables
- Keep technical artifacts updated each round:
1. benchmark table with command + run-id + result
2. bottleneck classification rationale
3. ADR updates for major design decisions
4. blog fact report (`Context/Problem/Solution/Key Code/Lesson`)

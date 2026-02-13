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
- `MAX_ATTEMPTS` (default: 4)
- `MIN_PARALLELISM` (default: 40)
- `MAX_PARTITIONS` (default: 6)
- `PART_TIMEOUT_SECONDS` (default: `duration + 120`)

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

## Metrics To Record
- `published_total`
- `failed_total`
- `throughput_total(msg/sec)`
- per-part logs: `docs/loadtest-runs/<run-id>/attempt-<n>/part-*.log`
- failure signatures:
  - `unable to create native thread`
  - `pthread_create failed (EAGAIN)`
  - `Timed out as no activity` (Paho keepalive path)

## Interpretation Rule
- If Paho fails and HiveMQ succeeds under same parameters, classify as client-model bottleneck.
- Use HiveMQ path as default baseline for further backend capacity validation.

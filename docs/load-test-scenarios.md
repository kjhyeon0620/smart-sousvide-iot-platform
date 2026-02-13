# MQTT Load Test Scenarios (Local)

## Run Command
Use Gradle JavaExec task:

```bash
./gradlew mqttLoadTest --args="--connections=1000 --messages-per-second=1 --duration-seconds=120"
```

## Parameters
- `--broker-url=tcp://localhost:1883`
- `--topic-template=sousvide/%s/status`
- `--connections=100`
- `--start-index=0`
- `--connect-parallelism=100`
- `--messages-per-second=1`
- `--duration-seconds=60`
- `--qos=1`
- `--client-prefix=sim`
- `--base-temp=60.0`
- `--target-temp=65.0`

## Step Load Plan
1. `1k x 1 msg/s x 120s`
2. `3k x 1 msg/s x 120s`
3. `5k x 1 msg/s x 120s`
4. `10k x 1 msg/s x 120s`

## Metrics To Record
- simulator throughput (msg/s)
- broker CPU / memory
- backend ingest log rate and error count
- Redis command latency (heartbeat)
- Influx write errors

## Distributed Run (Recommended for 2500+)
```bash
./scripts/loadtest/run-distributed.sh 2500 2 120 1 60 1
```

Arguments:
- `2500`: total connections
- `2`: number of simulator processes
- `120`: connect parallelism per process
- `1`: messages per second per device
- `60`: duration seconds
- `1`: qos

## Adaptive Fallback (Round3)
`run-distributed.sh` retries automatically when thread-limit errors are detected.

Environment variables:
- `MAX_ATTEMPTS` (default: 4)
- `MIN_PARALLELISM` (default: 40)
- `MAX_PARTITIONS` (default: 6)

Fallback order:
1. Increase partition count by +1 (up to `MAX_PARTITIONS`)
2. Reduce connect parallelism by half (down to `MIN_PARALLELISM`)

Example:
```bash
MAX_ATTEMPTS=5 MIN_PARALLELISM=30 MAX_PARTITIONS=8 ./scripts/loadtest/run-distributed.sh 2500 2 120 1 60 1
```

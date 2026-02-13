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

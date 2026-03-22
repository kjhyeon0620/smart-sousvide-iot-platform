# Observability Guide

## Purpose
- 운영 지표를 표준 endpoint로 노출한다.
- Prometheus/Grafana 연동의 1차 기반을 제공한다.

## Enabled Endpoints
- `GET /actuator/health`
- `GET /actuator/info`
- `GET /actuator/metrics`
- `GET /actuator/prometheus`

## How To Verify
1. 애플리케이션 실행
2. health 확인
```bash
curl -s http://localhost:8080/actuator/health
```
3. prometheus 메트릭 확인
```bash
curl -s http://localhost:8080/actuator/prometheus | rg "^iot\\."
```

## Metric Names
- Ingestion:
  - `iot_ingestion_mqtt_received_total`
  - `iot_ingestion_parse_success_total`
  - `iot_ingestion_parse_failure_total`
  - `iot_ingestion_influx_success_total`
  - `iot_ingestion_influx_failure_total`
  - `iot_ingestion_influx_bypass_total`
  - `iot_ingestion_influx_write_latency_seconds`
  - `iot_ingestion_redis_success_total`
  - `iot_ingestion_redis_failure_total`
  - `iot_ingestion_redis_heartbeat_latency_seconds`
  - `iot_ingestion_processing_failure_total`
  - `iot_ingestion_pipeline_overall_success_total`
  - `iot_ingestion_pipeline_core_success_total`
  - `iot_ingestion_inflight`
  - `iot_ingestion_e2e_latency_seconds`
  - `iot_ingestion_executor_queue_wait_seconds`
  - `iot_ingestion_executor_rejected_total`
  - `iot_ingestion_executor_queue_depth`
  - `iot_ingestion_executor_active`
  - `iot_ingestion_control_dispatch_latency_seconds`
  - `iot_ingestion_processing_latency_seconds`
- Downlink:
  - `iot_downlink_command_sent_total`
  - `iot_downlink_command_failed_total`
  - `iot_downlink_command_acked_total`
  - `iot_downlink_command_expired_total`
  - `iot_downlink_command_retried_total`
  - `iot_downlink_command_idempotency_hit_total`

## Common Tags
- `application`: `${spring.application.name}`
- `env`: `${APP_ENV:local}`

## Ingestion Notes
- MQTT inbound channel은 `ingestion.channel.mode=direct|executor`로 전환 가능하다.
- `executor` 모드에서는 broker 수신 스레드와 downstream 처리 스레드를 느슨하게 분리한다.
- business success 지표는 아래 두 축으로 본다.
  - overall pipeline success: parse 이후 Influx + Redis가 모두 성공한 건수
  - core pipeline success: parse 이후 Redis heartbeat까지 성공한 건수
- `strict` 모드는 Influx write path를 포함한 전체 경로 검증용이다.
- `bypass` 모드는 Influx 압력을 제외하고 parse + Redis + control path를 검증하기 위한 모드다.
- 주요 성능 비교 지표는 아래 순서로 본다.
  - `iot_ingestion_e2e_latency_seconds`: channel 진입부터 downstream 처리 종료까지의 전체 지연
  - `iot_ingestion_processing_latency_seconds`: consumer 실행 이후 처리 지연
  - `iot_ingestion_executor_queue_wait_seconds`: executor queue 대기 지연
  - `iot_ingestion_influx_write_latency_seconds`: Influx 저장 지연
  - `iot_ingestion_redis_heartbeat_latency_seconds`: Redis heartbeat 갱신 지연
  - `iot_ingestion_control_dispatch_latency_seconds`: control decision + auto command dispatch 지연

## Grafana
- Dashboard JSON:
  - `docs/grafana-observability-dashboard.json`
- Runbook:
  - `docs/operations-runbook.md`

## Quick Import
1. Grafana > Dashboards > Import
2. `docs/grafana-observability-dashboard.json` 업로드
3. datasource 변수(`DS_PROMETHEUS`)를 Prometheus에 매핑

## Docker Compose Quick Start
1. 인프라 기동
```bash
docker compose up -d
```
2. 애플리케이션 실행 (`localhost:8080`)
3. Prometheus 확인:
   - `http://localhost:9090/targets`
   - `iot-backend` target이 `UP` 상태인지 확인
4. Grafana 확인:
   - `http://localhost:3000` (`admin` / `admin`)
   - `IoT` 폴더에 대시보드가 자동 로드됨

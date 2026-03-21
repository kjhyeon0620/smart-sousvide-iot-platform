# Observability Baseline (Phase 6)

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
  - `iot_ingestion_redis_success_total`
  - `iot_ingestion_redis_failure_total`
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

## Grafana (Phase 7)
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

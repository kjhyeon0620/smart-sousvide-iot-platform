# K-Le-PaaS Oracle Deployment

This project can publish the backend image to GHCR and ask K-Le-PaaS to deploy
that image to the Oracle k3s target.

## Workflow

The workflow is defined in:

```text
.github/workflows/deploy-klepaas-oracle.yml
```

It runs on `main` branch pushes and supports manual `workflow_dispatch` runs. The
job runs the focused Gradle test gate first, builds the backend image for
`linux/arm64`, pushes it to GHCR, then calls the K-Le-PaaS deployment API with
the exact image URI.

Image format:

```text
ghcr.io/${{ github.repository }}/backend:sha-${{ github.sha }}
```

The deployment API payload must stay snake_case:

```json
{
  "repository_id": "...",
  "branch_name": "...",
  "commit_hash": "...",
  "image_uri": "..."
}
```

## Required GitHub Secrets

Configure these secrets in the Smart Sousvide repository:

```text
KLEPAAS_DEPLOY_URL
KLEPAAS_REPOSITORY_ID
```

`KLEPAAS_DEPLOY_URL` should point to K-Le-PaaS `POST /api/v1/deployments`.
`KLEPAAS_REPOSITORY_ID` is the repository id registered in K-Le-PaaS for this
ON_PREMISE target.

## Deployment Authentication

The workflow does not store a K-Le-PaaS token. It has `permissions: id-token: write`
and requests a short-lived GitHub Actions OIDC token with audience `k-le-paas`
for each run, then sends it as `Authorization: Bearer <token>`.

K-Le-PaaS accepts the request only when the token comes from this repository's
`main` branch and the request's `repository_id`, `branch_name`, and `commit_hash`
match the token's repository, ref, and commit. The token can only create
deployments. See the K-Le-PaaS deployment guide, "CI Deployment Authentication".

Do not commit kubeconfig, GHCR tokens, K-Le-PaaS tokens, database passwords, or
production runtime secrets to this repository.

## K-Le-PaaS Config Expectations

The K-Le-PaaS repository config should use:

```text
cloud_vendor=ON_PREMISE
build_strategy=GITHUB_ACTIONS_GHCR
image_pull_secret_name=ghcr-pull-secret
service_type=NODE_PORT
node_port=30080
container_port=8080
```

The workflow sends `image_uri` directly, so `image_uri_template` is optional for
this path.

Oracle k3s assumptions:

```text
namespace=klepaas
image_pull_secret_name=ghcr-pull-secret
```

## Runtime Configuration

The image contains the Spring Boot application and the existing React dashboard.
The Dockerfile builds `frontend/` with `npm ci` and `npm run build`, then copies
`frontend/dist/` into `src/main/resources/static/` before building the JAR.
Spring Boot serves the dashboard at `/` and the existing API at `/devices`.
Both use the same origin, so no production `VITE_API_BASE_URL` override is needed.
A new image must be built and deployed; redeploying an older image does not add
the dashboard. The application still needs runtime services and environment
variables in the Oracle k3s target.
Do not commit real secret values.

Required service dependencies:

```text
MySQL
Redis
MQTT broker
InfluxDB
```

Minimum runtime environment variables to provide through K-Le-PaaS or Kubernetes
Secret/ConfigMap:

```text
APP_ENV
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
SPRING_DATA_REDIS_HOST
SPRING_DATA_REDIS_PORT
SPRING_MQTT_BROKER_URL
SPRING_MQTT_CLIENT_ID
SPRING_MQTT_USERNAME
SPRING_MQTT_PASSWORD
SPRING_MQTT_DEFAULT_TOPIC
INFLUXDB_URL
INFLUXDB_TOKEN
INFLUXDB_ORG
INFLUXDB_BUCKET
```

Optional tuning values:

```text
INGESTION_CHANNEL_MODE
INGESTION_HEARTBEAT_TTL_SECONDS
INGESTION_EXECUTOR_CORE_POOL_SIZE
INGESTION_EXECUTOR_MAX_POOL_SIZE
INGESTION_EXECUTOR_QUEUE_CAPACITY
INGESTION_INFLUX_WRITE_MODE
WATCHDOG_SCAN_INTERVAL_MS
WATCHDOG_OFFLINE_NOTIFY_COOLDOWN_SECONDS
CONTROL_DEADBAND
```

The current `application.yml` defaults are local/docker-compose oriented
(`localhost` for MySQL, Redis, MQTT, and InfluxDB). They will not work unchanged
inside k3s because `localhost` would point at the backend Pod itself.

Before the first live deployment, decide whether MySQL, Redis, MQTT, and InfluxDB
will run inside the Oracle k3s cluster or be external managed services. If they
run in k3s, set the URLs/hosts to Kubernetes Service DNS names in the `klepaas`
namespace. If they run externally, set them to the external endpoints and provide
the credentials through secrets.

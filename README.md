# MiniDog

MiniDog is a single-node metrics ingestion and aggregation service built to
exercise the failure modes that matter in observability infrastructure:
backpressure, concurrent processing, tag-cardinality growth, durable time
buckets, and measurable behavior under load.

On the local benchmark described below, MiniDog accepted **138,680 metric
events/second** in 500-event batches. When the bounded queue saturated, it shed
load with HTTP 429 responses while the process remained healthy and eventually
drained the queue to zero.

## What it does

- Accepts individual or batched gauge, counter, and histogram events.
- Validates request shape, values, timestamps, types, and tag limits at the API.
- Uses a bounded, non-blocking queue so ingestion threads never wait for workers.
- Processes events with a configurable fixed worker pool.
- Limits unique tag combinations independently for each metric name.
- Computes count, sum, average, latest, and histogram p50/p95/p99 statistics.
- Flushes aggregate windows to H2 or PostgreSQL on a configurable interval.
- Queries stored time buckets by metric, time range, tags, and statistic.
- Exposes health, Prometheus metrics, and a pipeline status snapshot.

## Request flow

```text
POST /ingest
    |
    v
validation --> bounded ArrayBlockingQueue --full--> HTTP 429
                         |
                         v
                  8 worker threads
                         |
                         v
                cardinality limiter --over cap--> drop + metric
                         |
                         v
               in-memory window aggregator
                         |
                    every 10s
                         v
           indexed metric_aggregates table
                         |
                         v
                    GET /query
```

`202 Accepted` means an event entered the queue; it does not claim that the
event has already been persisted. A batch is accepted or rejected atomically.

## Run locally

Requirements: Java 21. The Maven wrapper downloads the required Maven version.

```powershell
.\mvnw.cmd spring-boot:run
```

By default MiniDog listens on `http://localhost:8080` and stores data in
`./data/minidog` using embedded H2 in PostgreSQL compatibility mode.

Run the tests:

```powershell
.\mvnw.cmd test
```

Build and run the container:

```powershell
docker build -t minidog .
docker run --rm -p 8080:8080 -v minidog-data:/app/data minidog
```

## PostgreSQL / Supabase

The migration at `supabase/migrations/20260901000000_create_metric_aggregates.sql`
creates the production table and its `(metric_name, bucket_time)` index. Apply
it with the Supabase dashboard, CLI, or MCP migration tool, then provide the
transaction-pooler JDBC connection through environment variables:

```powershell
$env:DATABASE_URL = "jdbc:postgresql://YOUR_POOLER_HOST:6543/postgres?sslmode=require&prepareThreshold=0"
$env:DATABASE_USERNAME = "YOUR_POOLER_USER"
$env:DATABASE_PASSWORD = "YOUR_DATABASE_PASSWORD"
.\mvnw.cmd spring-boot:run
```

No credentials are stored in the repository. The application defaults to
`spring.jpa.hibernate.ddl-auto=update`, so an empty development database is also
initialized automatically. For a controlled deployment, apply the migration
first and override that setting with `SPRING_JPA_HIBERNATE_DDL_AUTO=validate`.

## API

### Ingest one metric

```http
POST /ingest
Content-Type: application/json

{
  "name": "api.request.duration",
  "value": 45.3,
  "type": "histogram",
  "tags": {"service": "checkout", "env": "prod"},
  "timestamp": 1723999200
}
```

The timestamp is optional and defaults to the server's UTC receive time.

### Ingest a batch

```http
POST /ingest
Content-Type: application/json

{
  "events": [
    {"name": "requests", "value": 1, "type": "counter"},
    {"name": "temperature", "value": 21.4, "type": "gauge"}
  ]
}
```

Successful ingestion returns HTTP 202:

```json
{"status":"accepted","accepted":2}
```

If the queue cannot fit the entire request, the API immediately returns HTTP
429 with `{"status":"queue_full","accepted":0}`.

### Query aggregates

```http
GET /query?name=api.request.duration&from=1723990000&to=1724000000&stat=p95&tag=service:checkout&tag=env:prod
```

Supported statistics are `auto`, `sum`, `average`, `latest`, `p50`, `p95`, and
`p99`. `auto` selects sum for counters, latest for gauges, and p95 for
histograms. Every point includes the selected value plus its complete aggregate
statistics and tags.

## Configuration

| Property | Default | Purpose |
| --- | ---: | --- |
| `minidog.pipeline.queue-capacity` | 10,000 | Maximum events waiting for workers |
| `minidog.pipeline.worker-count` | 8 | Concurrent event-processing workers |
| `minidog.pipeline.cardinality-limit` | 1,000 | Unique tag combinations per metric name |
| `minidog.pipeline.flush-interval-seconds` | 10 | Aggregate window and persistence interval |
| `DATABASE_URL` | local H2 file | JDBC URL for durable storage |
| `DATABASE_USERNAME` | `sa` | Database user |
| `DATABASE_PASSWORD` | empty | Database password |

Spring Boot properties can also be supplied with their standard uppercase
environment-variable form.

## Operations

- `GET /status` — queue, worker, drop, aggregation, and flush counters.
- `GET /actuator/health` — aggregate service health without sensitive details.
- `GET /actuator/health/liveness` and `/readiness` — probe groups.
- `GET /actuator/metrics` — Micrometer metric discovery.
- `GET /actuator/prometheus` — Prometheus exposition format.

Custom Prometheus metrics use the `minidog_*` prefix and cover queue depth,
capacity, accepted/rejected events, aggregation, cardinality drops, worker
failures, active series, persisted series, and flush failures.

## Load test

The dependency-free Java 21 generator is documented under `load-test/`. Two
profiles were run on September 1, 2026 against the full local HTTP pipeline,
with H2 persistence flushing every five seconds and 8 logical processors
available to the process.

| Profile | Concurrency | Batch | Accepted events/s | p50 | p95 | p99 | HTTP 429 | Failures |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Moderate batch | 32 | 50 | 14,176 | 67.14 ms | 249.63 ms | 831.05 ms | 0 | 0 |
| Saturation | 16 | 500 | 138,680 | 41.79 ms | 113.86 ms | 245.67 ms | 144 | 0 |

After both runs, `/status` reported 915,700 accepted and aggregated events,
72,000 events rejected at the queue boundary, zero worker failures, zero flush
failures, and queue depth zero. These are local development measurements, not a
claim about production hardware or a distributed deployment.

## Design tradeoffs

- The in-process queue makes backpressure behavior explicit and keeps the demo
  operationally small. It also means queued data is lost on process failure and
  cannot be shared across instances; Kafka or another durable broker is the
  next step for horizontal scale.
- The cardinality limiter favors established series and drops only unseen tag
  combinations after a per-metric cap. Its tracking set is process-local and
  lifetime-scoped.
- Exact nearest-rank histogram percentiles are easy to verify but retain values
  for the current flush window. A production service would use DDSketch,
  t-digest, or fixed buckets to bound memory.
- Queries use the metric/time index and apply tag subsets in the service. At
  much larger cardinality, tags should use PostgreSQL JSONB with appropriate
  expression or GIN indexes.
- Failed database flushes retain one drained aggregate batch for retry. This
  avoids silent loss, but a persistent database outage can delay later windows.

## Scope

MiniDog is intentionally a backend-only, single-process implementation. A UI is
kept separate. Authentication, multi-tenancy, an external broker, distributed
cardinality coordination, and Kubernetes deployment are natural future work.

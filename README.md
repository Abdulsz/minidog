# MiniDog

MiniDog is a compact metrics ingestion and aggregation service built to explore
the hard parts of observability infrastructure: concurrent ingestion,
backpressure, high-cardinality tags, windowed aggregation, durable persistence,
and measurable behavior under load.

In an end-to-end benchmark backed by Supabase PostgreSQL, MiniDog accepted up to
**149,493 metric events per second**. When the bounded queue filled, the service
rejected complete batches with HTTP `429` instead of blocking request threads or
growing memory without limit. It drained the queue after the test with no worker
failures and no database flush failures.

## Highlights

- Accepts individual metrics or atomic batches over HTTP.
- Supports gauges, counters, and histograms.
- Computes count, sum, average, latest value, and p50/p95/p99 percentiles.
- Uses a bounded, non-blocking queue as an explicit load-shedding boundary.
- Processes events concurrently with a configurable worker pool.
- Caps unique tag combinations per metric to control cardinality growth.
- Flushes time-windowed aggregates to H2 or PostgreSQL/Supabase.
- Queries persisted metrics by name, time range, tags, and statistic.
- Exposes health checks, Prometheus metrics, and a pipeline status snapshot.
- Retries a detached aggregate batch after a failed database flush.

## Architecture

```text
Client -> POST /ingest -> validation -> bounded ArrayBlockingQueue
                                             |              |
                                          queue full        v
                                             |       8 worker threads
                                             v              |
                                          HTTP 429   cardinality limiter
                                                            |
                                                            v
                                                  in-memory aggregation
                                                            |
                                                     periodic flush
                                                            |
                                                            v
                                                  H2 or PostgreSQL/Supabase
                                                            |
                                                            v
                                                       GET /query
```

An HTTP `202 Accepted` response means the complete request entered the queue; it
does not mean the data has already reached the database. Batch admission is
atomic: if the queue cannot fit every event, MiniDog inserts none of them and
returns `429 Too Many Requests`.

## Technology

- Java 21
- Spring Boot 4
- Spring MVC and Bean Validation
- Spring Data JPA / Hibernate
- H2 for zero-configuration local development
- PostgreSQL and Supabase for deployed persistence
- Micrometer and Prometheus for telemetry
- JUnit 5, AssertJ, and Spring test support

## Quick start

### Requirements

- Java 21
- Docker, only if you want to build or run the container

The Maven wrapper is included, so a separate Maven installation is unnecessary.

On macOS or Linux:

```bash
./mvnw spring-boot:run
```

On Windows PowerShell:

```powershell
.\mvnw.cmd spring-boot:run
```

MiniDog starts at `http://localhost:8080`. By default, it stores data in
`./data/minidog` using embedded H2 in PostgreSQL compatibility mode.

Check that it is healthy:

```bash
curl http://localhost:8080/actuator/health
```

## API

### Ingest one event

```bash
curl -X POST http://localhost:8080/ingest \
  -H "Content-Type: application/json" \
  -d '{
    "name": "api.request.duration",
    "value": 45.3,
    "type": "histogram",
    "tags": {"service": "checkout", "env": "prod"},
    "timestamp": 1788253200
  }'
```

The timestamp is an optional Unix timestamp in seconds. If omitted, MiniDog
uses the server's current UTC time.

Successful response:

```json
{"status":"accepted","accepted":1}
```

### Ingest a batch

```bash
curl -X POST http://localhost:8080/ingest \
  -H "Content-Type: application/json" \
  -d '{
    "events": [
      {"name": "requests", "value": 1, "type": "counter"},
      {"name": "temperature", "value": 21.4, "type": "gauge"}
    ]
  }'
```

If the queue has insufficient capacity for the entire batch, the response is:

```json
{"status":"queue_full","accepted":0}
```

### Query aggregates

```bash
curl "http://localhost:8080/query?name=api.request.duration&from=1788250000&to=1788260000&stat=p95&tag=service:checkout&tag=env:prod"
```

Supported statistics are `auto`, `sum`, `average`, `latest`, `p50`, `p95`, and
`p99`. The `auto` option selects sum for counters, latest for gauges, and p95 for
histograms. Each returned point also contains the complete aggregate statistics
and decoded tags.

## PostgreSQL and Supabase

Production DDL lives in [`supabase/migrations`](supabase/migrations). The
migration creates `metric_aggregates`, validates metric types and numeric
invariants, and adds a B-tree index on `(metric_name, bucket_time)` for the main
range query.

For Supabase, use the transaction pooler on port `6543` and inject credentials
through the environment. Do not commit them.

macOS or Linux:

```bash
export DATABASE_URL='jdbc:postgresql://YOUR_POOLER_HOST:6543/postgres?sslmode=require&prepareThreshold=0'
export DATABASE_USERNAME='YOUR_POOLER_USER'
export DATABASE_PASSWORD='YOUR_DATABASE_PASSWORD'
export SPRING_JPA_HIBERNATE_DDL_AUTO='validate'
./mvnw spring-boot:run
```

Windows PowerShell:

```powershell
$env:DATABASE_URL = "jdbc:postgresql://YOUR_POOLER_HOST:6543/postgres?sslmode=require&prepareThreshold=0"
$env:DATABASE_USERNAME = "YOUR_POOLER_USER"
$env:DATABASE_PASSWORD = "YOUR_DATABASE_PASSWORD"
$env:SPRING_JPA_HIBERNATE_DDL_AUTO = "validate"
.\mvnw.cmd spring-boot:run
```

The default `ddl-auto=update` keeps local setup simple. A controlled deployment
should apply the migration first and use `ddl-auto=validate` so startup detects
schema drift without changing the database.

MiniDog connects directly over JDBC; it does not require the Supabase Data API.
If `public.metric_aggregates` is exposed through that API, enable Row Level
Security and define policies appropriate to your clients.

## Configuration

| Setting | Default | Purpose |
| --- | ---: | --- |
| `minidog.pipeline.queue-capacity` | `10000` | Maximum events waiting for workers |
| `minidog.pipeline.worker-count` | `8` | Concurrent aggregation workers |
| `minidog.pipeline.cardinality-limit` | `1000` | Unique tag combinations allowed per metric name |
| `minidog.pipeline.flush-interval-seconds` | `10` | Aggregate persistence interval |
| `DATABASE_URL` | Local H2 file | JDBC connection URL |
| `DATABASE_USERNAME` | `sa` | Database username |
| `DATABASE_PASSWORD` | empty | Database password |

Spring Boot's standard environment-variable mapping can also be used for every
property, for example `MINIDOG_PIPELINE_QUEUE_CAPACITY=20000`.

## Observability

| Endpoint | Purpose |
| --- | --- |
| `GET /status` | Queue depth, throughput reconciliation, active series, drops, and flush state |
| `GET /actuator/health` | Aggregate service health without sensitive component details |
| `GET /actuator/metrics` | Micrometer metric discovery |
| `GET /actuator/prometheus` | Prometheus exposition output |

Custom metrics use the `minidog_*` prefix and cover queue depth and capacity,
accepted and rejected events, aggregation outcomes, cardinality drops, active
series, persisted series, and flush failures.

## Testing

### Automated test suite

Run a clean compile, all tests, and packaging:

```bash
./mvnw clean verify
```

On Windows:

```powershell
.\mvnw.cmd clean verify
```

The suite exercises the system at several boundaries:

| Test area | Tests | What is verified |
| --- | ---: | --- |
| Aggregation | 5 | Counts, sums, latest values, percentiles, concurrent updates, and window draining |
| Cardinality limiting | 4 | Existing-series admission, per-metric limits, and race safety |
| Ingest controller | 5 | Single and batch requests, validation, atomic admission, and HTTP 429 behavior |
| Queue | 1 | Capacity and non-blocking admission behavior |
| Application context | 1 | Complete Spring application startup |
| Persistence/API integration | 4 | Scheduled flushing, stored queries, tag filters, errors, status, and health |
| Pipeline integration | 2 | Real queue, worker, limiter, and aggregator interaction |
| **Total** | **22** | **Full suite** |

The suite was rerun on **September 12, 2026** with Java 21.0.4:

```text
Tests run: 22, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Tests use an isolated in-memory H2 database in PostgreSQL compatibility mode,
so they do not require or mutate a developer's Supabase project.

### End-to-end load test

The standalone [`LoadGenerator.java`](load-test/LoadGenerator.java) uses only
Java 21. It creates a fixed-size JSON batch, starts a configurable number of
client threads, and repeatedly sends synchronous HTTP requests for 15 seconds.
This exercises JSON parsing, validation, queue admission, worker processing,
aggregation, and periodic database persistence through the real application.

The generator records:

- accepted requests (`202`);
- shed requests (`429`);
- unexpected responses, timeouts, and network failures;
- accepted events per second; and
- nearest-rank p50, p95, and p99 request latency across completed responses.

Latency includes both `202` and `429` responses because both are completed
requests observed by a client.

Compile it and run the two recorded profiles:

```bash
javac load-test/LoadGenerator.java
java -cp load-test LoadGenerator http://localhost:8080/ingest 32 15 50
java -cp load-test LoadGenerator http://localhost:8080/ingest 16 15 500
```

#### Recorded results

These profiles were run on **September 1, 2026** against the complete MiniDog
service with 8 aggregation workers, a five-second flush interval, and Supabase
PostgreSQL 17.6 through the transaction pooler. They are development-machine
measurements, not production capacity guarantees.

| Profile | Concurrency | Batch size | Accepted events/s | p50 | p95 | p99 | HTTP 429 | Failures |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Moderate batches | 32 | 50 | 105,868 | 11.43 ms | 32.94 ms | 56.08 ms | 395 | 0 |
| Saturation | 16 | 500 | 149,493 | 35.54 ms | 106.64 ms | 162.28 ms | 758 | 0 |

After both runs, `/status` reported:

- **3,847,700** events accepted and aggregated;
- **398,750** events rejected at the queue boundary;
- **0** worker failures;
- **0** Supabase flush failures; and
- queue depth returned to **0**.

Eight aggregate rows were persisted. Every generated event deliberately used
the same metric name and tags, so millions of raw events collapsed into one
series per flush window. The row count therefore measures aggregate windows,
not accepted events.

## Design decisions and limits

- **Bounded in-process queue:** keeps overload behavior visible and predictable,
  but queued data can be lost if the process crashes and cannot be shared across
  instances.
- **Per-metric cardinality cap:** protects memory while allowing established
  series to continue, but the tracking state is local to one process.
- **Exact percentiles:** nearest-rank results are simple to understand and test,
  but histogram samples are retained for the active flush window. A larger
  system should use DDSketch, t-digest, or fixed buckets.
- **Batch persistence:** database writes happen outside the request path and use
  `saveAll()`. One failed detached batch is retained for retry, so a prolonged
  outage can delay later windows.
- **Application-side tag filtering:** the metric/time index narrows the query,
  then tag subsets are filtered in Java. At higher cardinality, use PostgreSQL
  `jsonb` with suitable expression or GIN indexes.

## Container

```bash
docker build -t minidog .
docker run --rm -p 8080:8080 -v minidog-data:/app/data minidog
```

The multi-stage image builds with Java 21, runs on the smaller JRE image, and
executes as a non-root user. Inject the database environment variables when
running the container against PostgreSQL.

## Project layout

```text
src/main/java/com/project/minidog/
  aggregation/      concurrent statistics and cardinality control
  api/              ingestion, query, status, and error endpoints
  config/           queue, workers, properties, and Micrometer metrics
  ingestion/        bounded queue and worker lifecycle
  persistence/      aggregate entity, repository, and scheduled flusher
src/test/            unit, controller, pipeline, and persistence tests
load-test/           dependency-free Java load generator
supabase/migrations/ PostgreSQL schema migrations
Dockerfile           multi-stage production image
```

For a deeper discussion of concurrency, failure behavior, and scaling choices,
see [`ARCHITECTURE.md`](ARCHITECTURE.md).

## Future work

The next scaling step is to place a durable partitioned log such as Kafka or
Redpanda before the aggregation workers. Partitioning by tenant and metric would
give stable ownership and ordering, while mergeable sketches would bound
histogram memory. From there, ingest and query workloads can be deployed
independently and scaled horizontally under Kubernetes.

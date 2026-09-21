# Software Operations API

[![CI](https://github.com/mrav7/software-operations-api/actions/workflows/ci.yml/badge.svg)](https://github.com/mrav7/software-operations-api/actions/workflows/ci.yml)

Software Operations API is a Java/Spring Boot backend for managing software
components, operational work orders, controlled lifecycle transitions, and
persistent operational history.

The project uses a fictional software-operations domain and focuses on explicit
business rules, transactional consistency, concurrency handling, automated
testing, and reproducible application operation.

## Tech stack

**Java 25 · Spring Boot 4 · Maven · PostgreSQL 18 · Spring Data JPA /
Hibernate · Flyway · JUnit · Docker Compose · GitHub Actions**

## Engineering highlights

- Explicit WorkOrder lifecycle with domain-level invariants.
- Progressive field immutability as operational work advances.
- Transactional WorkLog history for lifecycle state changes.
- PostgreSQL persistence with Flyway-owned schema migrations.
- Optimistic locking for stale concurrent writes.
- Targeted row locking for cross-entity consistency.
- PostgreSQL-backed persistence, rollback, HTTP, safety, and concurrency tests.
- `application/problem+json` error responses for API failures.
- Health checks, external runtime configuration, and operational logging.
- Multi-stage Docker build with a non-root runtime user.
- GitHub Actions build and test verification against PostgreSQL.

## WorkOrder lifecycle

```text
CREATED
  ├──> PLANNED ──> IN_PROGRESS ──> COMPLETED
  │                    │
  │                    └──> BLOCKED ──> IN_PROGRESS
  └──> CANCELLED
```

PLANNED, IN_PROGRESS, and BLOCKED may also transition to CANCELLED.
COMPLETED and CANCELLED are terminal.

Lifecycle changes are explicit domain operations rather than direct status
updates. Transitions enforce state-specific rules, timestamps, required
operational context, and persisted history within the application transaction.

## Project status

**v1 feature scope is complete and technically verified.** The repository is
currently maintained as a personal technical project; future changes are
expected to be limited to fixes or explicitly scoped improvements.

## Prerequisites

- Java 25 LTS. Tested with Eclipse Temurin 25.0.4.1.  
- PostgreSQL 18.6 with separate databases for application use and integration tests.
- On Linux, a POSIX shell, `curl` or `wget`, and `unzip` or `tar`.
- Network access for the initial Maven and build-plugin downloads.

Set `JAVA_HOME` to your Temurin JDK directory and put its `bin` directory first
on `PATH` in the current shell. Before building, check:

```bash
java -version
javac -version
./mvnw --version
```

The Maven version output should identify Maven 3.9.16 and Temurin Java 25.0.4.1.
The included Apache Maven Wrapper 3.3.4 downloads the configured Maven version;
a global Maven installation is unnecessary.

### Native PostgreSQL preparation

Native execution requires a PostgreSQL 18 server, an application role, a
development database, and a separate integration-test database. For example,
run the following with a PostgreSQL administrative role:

```sql
CREATE ROLE software_operations_api_app
    LOGIN
    PASSWORD 'change-me';

CREATE DATABASE software_operations_api_dev
    OWNER software_operations_api_app;

CREATE DATABASE software_operations_api_test
    OWNER software_operations_api_app;
```

`change-me` is a public placeholder and must be replaced locally. Equivalent
role and database names are valid, but the integration-test database name must
end with `_test`.

## Build

Configure the dedicated PostgreSQL test database before running the test suite:

```bash
export TEST_DB_URL=jdbc:postgresql://127.0.0.1:5432/software_operations_api_test
export TEST_DB_USERNAME=software_operations_api_app
export TEST_DB_PASSWORD=change-me
```

The integration suite queries `SELECT current_database()` during Spring context
initialization and refuses to run unless the actual connected database name ends
with `_test`. This guard runs before destructive test cleanup; tests must never
target the development database. From the repository root:

```bash
./mvnw test
./mvnw package
```

For builds starting without previous generated output:

```bash
./mvnw clean test
./mvnw clean package
```

`test` compiles sources and runs domain, application/service, PostgreSQL
persistence, transaction/rollback, HTTP-boundary, configuration, health,
logging, test-safety, and concurrency tests. `package` also creates an
executable Spring Boot JAR under `target/`.

## Run

Configure the application datasource before startup:

```bash
export DB_URL=jdbc:postgresql://127.0.0.1:5432/software_operations_api_dev
export DB_USERNAME=software_operations_api_app
export DB_PASSWORD=change-me
```

Flyway applies pending migrations during application startup, and Hibernate
validates the resulting schema without creating or updating it.

```bash
./mvnw spring-boot:run
```

Spring Boot 4.1.1 starts an HTTP server on port **8080** by default.
Alternatively, after `./mvnw package`:

```bash
java -jar target/software-operations-api-0.1.0-SNAPSHOT.jar
```

Stop native execution with `Ctrl-C`; Spring handles the termination signal and
shuts the application down cleanly.

### Runtime configuration

The application reads its runtime configuration from environment variables. No
database credentials or local environment values are versioned.

| Variable | Required | Default | Purpose |
|---|---:|---|---|
| `DB_URL` | yes | none | PostgreSQL JDBC URL |
| `DB_USERNAME` | yes | none | PostgreSQL role |
| `DB_PASSWORD` | yes | none | PostgreSQL credential |
| `SERVER_PORT` | no | `8080` | HTTP server port |
| `APP_ENVIRONMENT` | no | `local` | Runtime environment label |
| `APP_LOG_LEVEL` | no | `INFO` | Application package log level |

For example, start the already packaged JAR on a different port without
rebuilding it:

```bash
SERVER_PORT=18080 APP_ENVIRONMENT=staging APP_LOG_LEVEL=INFO \
  java -jar target/software-operations-api-0.1.0-SNAPSHOT.jar
```

`APP_ENVIRONMENT` is an application-owned label used in the startup marker. No
Spring profile-specific configuration files are currently required.

### Health and logging

Actuator exposes only health information:

```bash
curl http://localhost:8080/actuator/health
```

`status=UP` means the running application health is up. When the configured
PostgreSQL datasource is available, `components.db.status=UP` confirms its
health. Other Actuator endpoints are intentionally not exposed.

Application logs are emitted to stdout/stderr. They include application ready,
application shutdown, successful WorkOrder creation, successful lifecycle
transitions, and unexpected request failures. WorkLog is the persisted,
authoritative operational history; application logs are runtime diagnostics and
do not replace it.

### Native verification sequence

For a fresh native setup: provision PostgreSQL as above, configure `TEST_DB_*`,
run `./mvnw --batch-mode --no-transfer-progress clean verify`, configure
`DB_*`, start the application, wait for Flyway and startup to complete, check
`/actuator/health`, exercise the API, and stop the process with `Ctrl-C`.

## Docker Compose runtime

The containerized runtime was tested with Docker Engine 29.8.1 and Docker
Compose v5.5.1. It requires Docker Engine with the Compose v2-compatible CLI;
it does not replace the native Java/PostgreSQL workflow above.

Create local configuration from the public placeholders before starting:

```bash
cp .env.example .env
```

Replace the demonstration database password in `.env`. The local `.env` is
ignored by Git, while `.env.example` contains placeholders only. Do not copy
private host PostgreSQL credentials into it. Environment variables provide a
reproducible local setup, not encrypted production secret management.

Build the application image, create the application and PostgreSQL containers,
and inspect their state:

```bash
docker compose config --quiet
docker compose build
docker compose up -d
docker compose ps
```

The application is published on `APP_PORT` (8080 by default). Check application
and configured datasource health with:

```bash
curl http://localhost:8080/actuator/health
```

An overall `UP` status and `components.db.status=UP` confirm the application and
its configured PostgreSQL datasource are healthy. This is local operational
health, not a Kubernetes or production-readiness claim.

Compose captures the application's stdout/stderr streams. Inspect service logs
and the direct Java process with:

```bash
docker compose logs -f app
docker compose logs -f db
docker compose top app
```

Inside the Compose project network, the application connects to `db:5432`.
`db` is the service name resolved by Compose DNS; `localhost` inside the app
would refer to the app container itself. PostgreSQL is not published to the
host. The host reaches only the application through `APP_PORT`.

PostgreSQL data lives in a Docker named volume. Removing and recreating the
containers preserves that volume and its data:

```bash
docker compose down
docker compose up -d
```

To stop services, diagnose startup, or verify PostgreSQL internally:

```bash
docker compose ps
docker compose logs app
docker compose logs db
docker compose top app
docker compose exec db pg_isready
ss -ltn
curl http://localhost:8080/actuator/health
```

To reset all containerized database data, use the following destructive command
only when loss of the named PostgreSQL volume is intended:

```bash
docker compose down -v
```

## Continuous Integration

GitHub Actions runs for pushes to `main` and pull requests targeting `main`. It
verifies the complete Maven build and test suite against PostgreSQL 18, validates
the Docker Compose configuration, and builds the application Docker image.

After configuring the dedicated PostgreSQL test database, reproduce the central
Maven check locally with:

```bash
./mvnw --batch-mode --no-transfer-progress clean verify
```

Reproduce the Docker checks by supplying disposable placeholder values required
by Compose interpolation:

```bash
POSTGRES_DB=software_operations_api_ci \
POSTGRES_USER=software_operations_api_ci \
POSTGRES_PASSWORD=ci-placeholder \
APP_PORT=8080 \
APP_ENVIRONMENT=ci \
APP_LOG_LEVEL=INFO \
docker compose config --quiet

docker build --tag software-operations-api:ci .
```

CI does not deploy the application or publish build artifacts or images.

## HTTP basics

Register a component:

```bash
curl -i -H 'Content-Type: application/json' \
  -d '{"name":"configuration-service","description":"Configuration API"}' \
  http://localhost:8080/api/components
```

Creation returns `201 Created`, a JSON representation, and a `Location` header.
Use the returned component ID to retrieve it or create a work order:

```bash
curl http://localhost:8080/api/components/COMPONENT_ID

curl -i -H 'Content-Type: application/json' \
  -d '{"componentId":"COMPONENT_ID","title":"Deploy release","type":"DEPLOYMENT","priority":"HIGH","targetVersion":"2.4.1"}' \
  http://localhost:8080/api/work-orders

curl http://localhost:8080/api/work-orders/WORK_ORDER_ID
```

Replace the ID placeholders with UUIDs returned by the API. Retrieval returns
`200 OK`; new work orders start in `CREATED`.

### Component management

List components, update the permitted component fields, or deactivate a
component with the dedicated operation:

```bash
curl http://localhost:8080/api/components

curl -i -X PATCH -H 'Content-Type: application/json' \
  -d '{"description":"Updated component description"}' \
  http://localhost:8080/api/components/COMPONENT_ID

curl -i -X POST \
  http://localhost:8080/api/components/COMPONENT_ID/deactivation
```

Component PATCH requests may edit `name` and `description`. Deactivation is not
part of generic PATCH, and there is no reactivation endpoint. A component cannot
be deactivated while it has a WorkOrder in `CREATED`, `PLANNED`, `IN_PROGRESS`,
or `BLOCKED`.

### Collection queries and pagination

`GET /api/components` and `GET /api/work-orders` return paged collections. Pages
are zero-based: the default is `page=0`, the default size is `20`, and the
maximum size is `100`.

```bash
curl 'http://localhost:8080/api/components?page=0&size=20'

curl 'http://localhost:8080/api/work-orders?page=0&size=20'
curl 'http://localhost:8080/api/work-orders?status=BLOCKED&priority=CRITICAL&page=0&size=20'
```

WorkOrder collection queries accept exactly these optional filters:
`componentId`, `status`, `type`, and `priority`. Supplied filters combine with
AND semantics. A nonexistent `componentId` is a valid criterion and returns an
empty page when no WorkOrders match.

Both collections use fixed server-side ordering: `createdAt` descending, then
`id` ascending. v1 does not support client-defined sorting. A valid page beyond
the last page returns `200 OK` with an empty `items` array and truthful totals.

The collection response shape is:

```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

### WorkOrder updates

Modify permitted WorkOrder fields with PATCH:

```bash
curl -i -X PATCH -H 'Content-Type: application/json' \
  -d '{"title":"Deploy approved release","priority":"CRITICAL","targetVersion":"2.4.2"}' \
  http://localhost:8080/api/work-orders/WORK_ORDER_ID
```

The permitted fields are `componentId`, `type`, `title`, `description`,
`priority`, and `targetVersion`. Field editability narrows as work progresses:

| WorkOrder field | Editable while |
|---|---|
| `componentId` | `CREATED` |
| `type` | `CREATED` |
| `title` | `CREATED`, `PLANNED` |
| `description` | `CREATED`, `PLANNED` |
| `priority` | `CREATED`, `PLANNED`, `IN_PROGRESS`, `BLOCKED` |
| `targetVersion` | `CREATED`, `PLANNED` |

A WorkOrder may be reassigned only while `CREATED`; a different target
component must exist and be active. Status is never directly editable and must
change through the explicit lifecycle endpoint. `COMPLETED` and `CANCELLED` are
terminal states.

### Concurrent writes

Mutable SoftwareComponent and WorkOrder rows use optimistic locking, so a stale
conflicting write is rejected instead of silently overwriting a committed
change. HTTP-level optimistic conflicts return `409 Conflict`; persistence
versions are internal and are not exposed in requests or responses.

WorkOrder creation, reassignment to a different component, and component
deactivation coordinate through a transaction-scoped row lock on the affected
SoftwareComponent. This protects the active-component/active-work invariant
without globally serializing requests or automatically retrying stale work.

### Operational notes

A WorkOrder represents current operational state. WorkLog entries represent
historical operational information associated with that WorkOrder. Add a manual
note and list its history with:

```bash
curl -i -H 'Content-Type: application/json' \
  -d '{"message":"Investigated the database timeout"}' \
  http://localhost:8080/api/work-orders/WORK_ORDER_ID/logs

curl http://localhost:8080/api/work-orders/WORK_ORDER_ID/logs
```

Manual creation always produces a `NOTE`; the entry ID, type, creation time,
and WorkOrder association are server controlled. `STATUS_CHANGE` is reserved
for system-controlled history and cannot be selected by clients. Successful
`PLAN`, `START`, `BLOCK`, `RESUME`, `COMPLETE`, and `CANCEL` operations each
append one `STATUS_CHANGE`; creation and generic PATCH updates do not. Manual
notes and automatic status changes appear in one oldest-first timeline.

Manual notes are also allowed after a WorkOrder reaches `COMPLETED` or
`CANCELLED`, and adding one does not change the WorkOrder's current status or
`updatedAt` value. While blocked, the WorkOrder exposes the current
`blockingReason` and `blockedAt`. Resuming clears that current snapshot, while
the earlier BLOCK entry remains in WorkLog history. The lifecycle mutation and
its automatic `STATUS_CHANGE` persistence execute in the same application
transaction.

## Lifecycle transitions and errors

Use an explicit action to change a WorkOrder lifecycle state:

```bash
curl -i -H 'Content-Type: application/json' \
  -d '{"action":"PLAN"}' \
  http://localhost:8080/api/work-orders/WORK_ORDER_ID/transitions
```

The available actions are `PLAN`, `START`, `BLOCK`, `RESUME`, `COMPLETE`, and
`CANCEL`. `BLOCK`, `COMPLETE`, and `CANCEL` require their corresponding domain
context (`blockingReason`, `resolutionSummary`, and `cancellationReason`).
Successful transitions return the updated WorkOrder with `200 OK`.

Errors use `application/problem+json`:

- `400 Bad Request`: invalid request or operation input.
- `404 Not Found`: unknown component or WorkOrder.
- `409 Conflict`: lifecycle action incompatible with the WorkOrder state,
  duplicate component name, inactive component selected for creation or
  reassignment, component deactivation blocked by active work, or a stale
  concurrent write.

For example, a blank required field returns a ProblemDetail response with an
`errors` list. The API does not expose direct status editing.

Component and WorkOrder HTTP operations use PostgreSQL-backed Spring Data
repositories. Successful lifecycle transitions are written to PostgreSQL before
their responses are returned. Flyway owns schema creation and evolution, while
Hibernate validates that the mapped entities match the migrated schema. HTTP-created
resources remain available across application restarts while their PostgreSQL data
is retained.

Write use cases execute within Spring transaction boundaries over PostgreSQL.
Existing managed entities are persisted through JPA dirty checking at commit.

## v1 limitations

- No authentication, authorization, user identity, or RBAC model.
- No deployment/CD automation or distributed deployment coordination.
- No external observability stack beyond application logs and Actuator health.
- No client-visible version, ETag, or `If-Match` concurrency protocol.
- Runtime logs are diagnostic; PostgreSQL state and persisted WorkLog history
  are authoritative. A success log can be emitted inside a transaction before
  its final commit, without implying that rolled-back data was persisted.

## Source layout

- `src/main/java/` contains application source code.
- `src/main/resources/` contains application configuration and Flyway migrations.
- `src/test/java/` contains domain, application/service, PostgreSQL persistence,
  transaction/rollback, HTTP-boundary, configuration/health/logging,
  test-safety, and concurrency tests.
- `target/` contains generated build output and is ignored by Git.

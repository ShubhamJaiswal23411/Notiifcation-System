# Multi-Tenant Notification Service

A standalone Spring Boot service that lets multiple tenants send notifications across
email, SMS, push, and in-app channels using tenant-defined templates, with immediate
and scheduled sends, per-tenant rate limiting, retry-with-backoff on failure, and full
delivery tracking — built as a monolith with an in-process bounded worker pool instead
of an external message broker.

---

## Table of Contents

1. [Tech Stack](#tech-stack)
2. [How to Run](#how-to-run)
3. [Architecture: How the Modules Connect](#architecture-how-the-modules-connect)
4. [Data Model / Entity Relationships](#data-model--entity-relationships)
5. [Core Flow: Sending a Notification, Step by Step](#core-flow-sending-a-notification-step-by-step)
6. [Roles and Permissions](#roles-and-permissions)
7. [API Reference](#api-reference)
8. [Design Decisions](#design-decisions)
9. [Assumptions](#assumptions)
10. [Testing](#testing)
11. [Project Structure](#project-structure)
12. [Out of Scope](#out-of-scope)

---

## Tech Stack

| Concern | Choice | Why |
|---|---|---|
| Language / Runtime | Java 21 (LTS) | Stable, fully supported by Spring Boot 4.x |
| Framework | Spring Boot 4.1.1 | Current GA release at time of writing |
| Build tool | Gradle | Faster iteration than Maven for this project's size |
| Web layer | Spring Web MVC | REST APIs |
| Persistence | Spring Data JPA + Hibernate | Repository pattern, entity mapping |
| Database | H2 (in-memory, default) / PostgreSQL (optional profile) | See [Design Decisions](#h2-vs-postgresql) |
| Migrations | Flyway | Version-controlled schema, not Hibernate auto-DDL |
| Auth | JWT (jjwt 0.12.x) | Stateless, no session storage needed |
| Security | Spring Security | Role-based method security (`@PreAuthorize`) |
| Concurrency | `ThreadPoolTaskExecutor` + `@Async` + `@Scheduled` | In-process dispatch, see [Design Decisions](#no-message-broker) |
| Testing | JUnit 5, Mockito, AssertJ, `@WebMvcTest`, Jacoco | Unit + slice tests, 90%+ coverage target |

---

## How to Run

**Prerequisites:** JDK 21. No external database required for the default profile.

```bash
./gradlew bootRun
```

The app starts on `http://localhost:8080`. Flyway runs its migrations automatically on
startup against an in-memory H2 database — nothing to install or configure manually.

### Running against PostgreSQL (optional profile)

```bash
# Set credentials as environment variables first (never commit these)
$env:DB_USERNAME="postgres"; $env:DB_PASSWORD="yourpassword"
./gradlew bootRun --args='--spring.profiles.active=postgres'
```

Flyway will create the identical schema against a real Postgres database on first run.

### Running Tests

```bash
./gradlew test jacocoTestReport
```

Coverage report: `build/reports/jacoco/test/html/index.html`

### Manual smoke test

`scripts/smoke-test.ps1` (PowerShell) exercises the full flow end-to-end — auth, RBAC,
tenants, channels, templates, notification send/dispatch, idempotency, and scheduled
sends — against a running instance. Safe to re-run repeatedly.

```powershell
./scripts/smoke-test.ps1
```

---

## Architecture: How the Modules Connect

```
HTTP Request
     |
     v
+-----------------------------------------------------------------+
|  JwtAuthFilter  (runs once per request, before DispatcherServlet) |
|  - reads Authorization: Bearer <token>                            |
|  - validates via JwtService, loads user via                       |
|    AppUserDetailsService, populates SecurityContext                |
+-----------------------------------------------------------------+
     |
     v
+-----------------------------------------------------------------+
|  Controller layer  (@RestController)                              |
|  - @PreAuthorize enforces role (PLATFORM_ADMIN / TENANT_ADMIN)     |
|  - tenantId is pulled from the JWT principal via CurrentUser,      |
|    NEVER trusted from a path/query param                           |
|  - validates request DTOs (@Valid + Bean Validation)                |
+-----------------------------------------------------------------+
     |
     v
+-----------------------------------------------------------------+
|  Service layer                                                     |
|  - business logic, transaction boundaries (@Transactional)         |
|  - throws ResourceNotFoundException / relies on DB constraints     |
|    for conflicts (caught by GlobalExceptionHandler)                |
+-----------------------------------------------------------------+
     |
     v
+-----------------------------------------------------------------+
|  Repository layer  (Spring Data JPA)                                |
+-----------------------------------------------------------------+
     |
     v
+-----------------------------------------------------------------+
|  Database  (H2 in-memory, or PostgreSQL)                           |
|  schema owned by Flyway migrations (src/main/resources/db/migration)|
+-----------------------------------------------------------------+
```

### The dispatch pipeline (separate from the request/response cycle)

Sending a notification returns to the caller immediately (`201 Created`, status
`QUEUED` or `SCHEDULED`) — the actual delivery happens asynchronously:

```
NotificationService.createNotification()
     |  (persists a Notification row, status = QUEUED or SCHEDULED)
     v
NotificationDispatchService.dispatchAsync()   <- @Async, runs on notificationExecutor
     |  (thin wrapper: catches + logs any exception so nothing is silently swallowed)
     v
NotificationDispatchExecutor.dispatch()       <- @Transactional, separate bean
     |  1. check rate limit (RateLimiterService, per tenant+channel)
     |  2. render template (TemplateRenderer, {{variable}} substitution)
     |  3. call MockNotificationProvider.send() (simulated ~85% success)
     |  4. record a DeliveryAttempt row
     |  5. update Notification status: SENT / FAILED (with next_retry_at) / DEAD_LETTER
     v
NotificationSweeper                            <- @Scheduled, every 30s
     |  polls for SCHEDULED notifications whose scheduled_at has arrived,
     |  and FAILED notifications whose next_retry_at has arrived,
     |  resubmits each to NotificationDispatchService.dispatchAsync()
```

This design deliberately avoids Kafka or any message broker — see
[Design Decisions](#no-message-broker) for why.

---

## Data Model / Entity Relationships

```
platform_admins                    tenants
  id (PK)                            id (PK)
  email (unique)                     name
  password_hash                      api_key (unique)
                                      status
                                          |
                    +---------------------+---------------------+------------------+
                    |                     |                     |                  |
                    v                     v                     v                  v
              tenant_admins           channels              templates         rate_limits
                id (PK)                 id (PK)               id (PK)           id (PK)
                tenant_id (FK)          tenant_id (FK)         tenant_id (FK)    tenant_id (FK)
                email                   channel_type           channel_type      channel_type
                password_hash           config_json            name              max_per_minute
                                        enabled                subject           max_per_day
                                                                body
                                                                version
                                                                     |
                                                                     v
                                                              notifications
                                                                id (PK)
                                                                tenant_id (FK)
                                                                channel_type
                                                                template_id (FK)
                                                                recipient
                                                                variables_json
                                                                status
                                                                scheduled_at
                                                                idempotency_key (unique)
                                                                attempt_count
                                                                next_retry_at
                                                                version   <- optimistic lock
                                                                     |
                                                                     v
                                                             delivery_attempts
                                                               id (PK)
                                                               notification_id (FK)
                                                               attempt_number
                                                               status
                                                               error_message
                                                               attempted_at
```

Every tenant-owned table cascades from `tenants`; every notification-owned table
cascades from `notifications`. `variables_json` and `config_json` are stored as
`TEXT` (not native `jsonb`) so the schema behaves identically on both H2 and
PostgreSQL.

---

## Core Flow: Sending a Notification, Step by Step

1. **Tenant admin authenticates** — `POST /api/auth/login` returns a JWT containing
   their role (`ROLE_TENANT_ADMIN`) and `tenantId` as claims.
2. **Tenant admin sends a notification** — `POST /api/notifications` with a
   `templateId`, `recipient`, and `variables` map. `tenantId` is taken from the JWT,
   not the request body.
3. **Idempotency check** — if an `idempotencyKey` was supplied (or auto-generated) and
   already exists, the existing notification is returned unchanged — no duplicate is
   created. This is checked both at the service layer (fast path) and enforced by a
   database unique constraint (the actual guarantee against races — see
   [Design Decisions](#idempotency)).
4. **Notification is persisted** as `QUEUED` (send now) or `SCHEDULED` (future
   `scheduledAt`), and the API responds immediately — the caller never waits for
   actual delivery.
5. **Dispatch runs asynchronously**, on a bounded thread pool, off the request thread:
   - Per-tenant, per-channel rate limit is checked first; if exceeded, the
     notification is marked `FAILED` with a short retry delay and nothing is sent.
   - The template's `{{variable}}` placeholders are substituted with the supplied
     values.
   - A (simulated) provider call attempts delivery.
   - A `DeliveryAttempt` row is recorded regardless of outcome — this is the audit
     trail.
   - On success: status becomes `SENT`.
   - On failure: if attempts remain, status becomes `FAILED` with an exponentially
     increasing `next_retry_at`; once attempts are exhausted, status becomes
     `DEAD_LETTER`.
6. **The sweeper** (`NotificationSweeper`, every 30s) picks up anything due — either a
   `SCHEDULED` notification whose time has arrived, or a `FAILED` one whose retry
   delay has elapsed — and resubmits it through the same dispatch path.
7. **Concurrency safety**: `Notification` carries a JPA `@Version` column. If the
   sweeper and a direct dispatch call ever raced the same row, the second `save()`
   would hit an optimistic-lock conflict rather than silently overwriting the first —
   preventing a notification from being sent twice.

---

## Roles and Permissions

| Role | Can do |
|---|---|
| **Platform Admin** | Create/list/view tenants; provision tenant-admin accounts; set per-tenant rate limits |
| **Tenant Admin** | Manage channels and templates for their own tenant only; send notifications; view their own notifications and delivery attempt history |

Enforcement happens at two layers:
- **Coarse-grained**: `@PreAuthorize("hasRole('...')")` on each controller/method —
  wrong role -> `403 Forbidden`.
- **Fine-grained (tenant isolation)**: every tenant-scoped service method takes the
  caller's `tenantId` (from the JWT, via the `CurrentUser` helper) and filters or
  validates against it — a tenant admin cannot read or modify another tenant's data
  even if they guess a valid ID; such requests return `404 Not Found` (not `403`),
  so existence of another tenant's records is never leaked.

A request with no token, or an invalid/expired one, returns `401 Unauthorized`. A
request with a valid token but the wrong role returns `403 Forbidden`. This
distinction is enforced explicitly (anonymous authentication is disabled — see
[Design Decisions](#401-vs-403)) rather than relying on Spring Security's default
behavior, which conflates the two.

---

## API Reference

All endpoints except `/api/auth/login` require `Authorization: Bearer <token>`.

### Auth

| Method | Path | Role | Description |
|---|---|---|---|
| `POST` | `/api/auth/login` | Public | Authenticate (platform admin or tenant admin), returns a JWT |

### Tenants

| Method | Path | Role | Description |
|---|---|---|---|
| `POST` | `/api/tenants` | Platform Admin | Create a new tenant (auto-generates an API key) |
| `GET` | `/api/tenants` | Platform Admin | List all tenants |
| `GET` | `/api/tenants/{id}` | Platform Admin | Get a single tenant |
| `POST` | `/api/tenants/{id}/admins` | Platform Admin | Provision a tenant-admin account under a tenant |
| `PUT` | `/api/tenants/{id}/rate-limits` | Platform Admin | Set (create or update) a per-channel rate limit for a tenant |

### Channels

| Method | Path | Role | Description |
|---|---|---|---|
| `POST` | `/api/channels` | Tenant Admin | Create a channel (email/SMS/push/in-app) for the caller's own tenant |
| `GET` | `/api/channels` | Tenant Admin | List the caller's tenant's channels |

### Templates

| Method | Path | Role | Description |
|---|---|---|---|
| `POST` | `/api/templates` | Tenant Admin | Create a template with `{{variable}}` placeholders, for the caller's own tenant |
| `GET` | `/api/templates` | Tenant Admin | List the caller's tenant's templates |

### Notifications

| Method | Path | Role | Description |
|---|---|---|---|
| `POST` | `/api/notifications` | Tenant Admin | Send (or schedule) a notification. Supports an `idempotencyKey` to prevent duplicate sends |
| `GET` | `/api/notifications` | Tenant Admin | List the caller's tenant's notifications |
| `GET` | `/api/notifications/{id}` | Tenant Admin | Get a single notification's current status |
| `GET` | `/api/notifications/{id}/attempts` | Tenant Admin | View the full delivery-attempt history for one notification |

### Request/response shapes

**`POST /api/notifications`**
```json
{
  "channelType": "EMAIL",
  "templateId": 7,
  "recipient": "customer@example.com",
  "variables": { "name": "Alex", "orderId": "1001", "eta": "Friday" },
  "scheduledAt": null,
  "idempotencyKey": "optional-caller-supplied-key"
}
```
`scheduledAt` omitted or in the past means sent immediately (`QUEUED`). A future
timestamp results in `SCHEDULED`, picked up by the sweeper.

---

## Design Decisions

### No message broker
The assignment explicitly excludes distributed systems and microservices, so no
Kafka/RabbitMQ/etc. is used. Instead, the `notifications` table *is* the queue
(status-driven), a bounded `ThreadPoolTaskExecutor` provides concurrent dispatch, and
a `@Scheduled` sweeper handles redelivery — the same three guarantees a broker would
give (durable queue, bounded concurrency, per-tenant fairness) achieved in-process.

### Idempotency
Enforced at two levels: a fast-path check in the service layer (returns the existing
notification if the key is already known), and a **database unique constraint** on
`idempotency_key` as the real correctness guarantee — the fast-path check alone has a
race window under true concurrency; the constraint is what actually prevents a
duplicate row from ever being committed.

### H2 vs PostgreSQL
H2 in-memory is the default so the app is genuinely standalone — clone and run, no
external database to install. PostgreSQL is available as an opt-in profile
(`--spring.profiles.active=postgres`), with credentials supplied only via environment
variables, never committed. Both run against the identical Flyway-managed schema.

### 401 vs 403
Spring Security's default behavior treats a request with no credentials as
"anonymous but authenticated," which can make a missing-token request surface as
`403` instead of `401`, depending on filter internals. Anonymous authentication is
explicitly disabled, and a custom `AuthenticationEntryPoint`/`AccessDeniedHandler`
pair guarantees a deterministic split: `401` for no/invalid/expired token, `403` for
a valid token with the wrong role.

### Optimistic locking over pessimistic locking
`Notification` carries a `@Version` column rather than using `SELECT ... FOR UPDATE`.
Given dispatch is infrequent-per-row (a notification is only ever actively processed
a handful of times across its lifetime), optimistic locking avoids holding row locks
during the (relatively slow, simulated-network) provider call, while still
guaranteeing no double-send if two dispatch attempts ever race the same row.

### Rate limiting: fixed-window counter, not a true token bucket
A `ConcurrentHashMap<tenant+channel, AtomicInteger>` reset every 60 seconds gives the
same practical guarantee as a token bucket for this scope, without the added
complexity — acceptable given the single-instance, in-process nature of the whole
design.

---

## Assumptions

- A tenant's channels are unique per channel type (one EMAIL config, one SMS config,
  etc.) — modeled as a database unique constraint on `(tenant_id, channel_type)`.
- Template names are unique per tenant *and* channel type, not globally per tenant —
  the same template name can exist once for EMAIL and once for SMS.
- No self-service tenant signup: only a platform admin can create a tenant and its
  first tenant-admin account, per the role definitions in the requirement.
- The mock notification provider simulates an ~85% success rate, to exercise the
  retry/backoff/dead-letter paths without needing a real email/SMS provider
  integration (explicitly out of scope).
- Retry policy: exponential backoff starting at 30 seconds, doubling each attempt,
  capped at 1 hour, with a maximum of 5 attempts before a notification is marked
  `DEAD_LETTER`.
- The sweeper interval (30 seconds) is a demo-friendly value; a production
  deployment would tune this based on actual volume and latency requirements.
- JWTs expire after 1 hour; there is no refresh-token flow, consistent with
  "advanced authentication" being out of scope.

---

## Testing

- **Unit tests** (JUnit 5 + Mockito): pure-logic classes (`TemplateRenderer`,
  `RetryBackoffCalculator`, `RateLimiterService`) tested directly with no mocking;
  service-layer classes tested with mocked repositories, covering success paths,
  not-found cases, conflict/idempotency handling, and the retry/dead-letter
  branching logic.
- **Slice tests** (`@WebMvcTest`): every controller, verifying role enforcement
  (`403`), missing/invalid auth (`401`), validation failures (`400`), and correct
  happy-path responses — without booting the full application context.
- **Coverage target**: 90%+ across `service`, `service.dispatch`, `controller`,
  `security`, `exception`, and `scheduler` packages, measured via Jacoco
  (`./gradlew test jacocoTestReport`, report at
  `build/reports/jacoco/test/html/index.html`).
- **Manual/integration verification**: `scripts/smoke-test.ps1` exercises the full
  running application end-to-end, including the real dispatch pipeline and sweeper
  timing, which unit tests intentionally don't cover (async/scheduled behavior is
  tested at the unit level by calling the underlying methods directly and mocking
  the scheduler trigger, not by waiting on real timers in the test suite).

---

## Project Structure

```
src/main/java/com/example/notificationservice/
├── config/       - SecurityConfig, AsyncConfig, JacksonConfig
├── domain/       - JPA entities, enums
├── dto/          - request/response DTOs, grouped by feature
├── repository/   - Spring Data JPA repositories
├── service/      - business logic
│   └── dispatch/ - TemplateRenderer, RetryBackoffCalculator, RateLimiterService,
│                   MockNotificationProvider, NotificationDispatchService/Executor
├── controller/   - REST controllers
├── security/     - JwtService, JwtAuthFilter, AppUserDetailsService, CurrentUser
├── exception/    - GlobalExceptionHandler, ResourceNotFoundException
└── scheduler/    - NotificationSweeper

src/main/resources/
├── application.properties          - default (H2) configuration
├── application-postgres.properties - optional Postgres profile
└── db/migration/                   - Flyway migrations (V1-V4)

scripts/
└── smoke-test.ps1                  - end-to-end manual verification script
```

---

## Out of Scope

Per the assignment's explicit instructions: no UI/frontend, no deployment /
containerization / CI-CD, no distributed systems or microservices, no OAuth / SSO /
MFA, no production-grade observability or monitoring.

## AI-Assisted Development

This project was built with AI assistance (Claude). See `Claude.md` for the
development log — the workflow used, key decisions, and bugs found and fixed along
the way.

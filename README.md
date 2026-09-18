# Multi-Tenant Notification Service

A standalone Spring Boot service that lets multiple tenants send notifications across
email, SMS, push, and in-app channels using tenant-defined templates, with scheduled
sends, per-tenant rate limiting, retry-with-backoff on failure, and full delivery
tracking — built as a monolith with an in-process bounded worker pool instead of an
external message broker (see [Design Decisions](#design-decisions)).

## Tech Stack

- **Language / Runtime:** Java 21
- **Framework:** Spring Boot 4.1.1 (Spring Web MVC, Spring Data JPA, Spring Security)
- **Build tool:** Gradle
- **Database:** H2 (in-memory, default) — optional PostgreSQL profile, see below
- **Migrations:** Flyway
- **Auth:** JWT (via jjwt)
- **Testing:** JUnit 5, Mockito, Jacoco (90% coverage gate)

## Prerequisites

- JDK 21
- No local database install required — H2 runs in-memory by default

## How to Run

```bash
./gradlew bootRun
```

The app starts on `http://localhost:8080`. Flyway runs its migrations automatically on
startup — no manual schema setup needed.

### Running with PostgreSQL (optional)

```bash
DB_USERNAME=youruser DB_PASSWORD=yourpass ./gradlew bootRun --args='--spring.profiles.active=postgres'
```

## Running Tests

```bash
./gradlew check
```

Runs the full test suite and enforces a 90% line-coverage gate via Jacoco. Coverage
report is generated at `build/reports/jacoco/test/html/index.html`.

## Roles

| Role | Permissions |
|---|---|
| **Platform Admin** | Manage tenants, set global rate limits, view cross-tenant reports |
| **Tenant Admin** | Manage own tenant's templates, channel configuration, view own delivery reports |

Auth is via JWT bearer tokens; role is embedded in the token claims and enforced with
method-level `@PreAuthorize` checks.

## Core Flows

- Tenant & channel configuration
- Template creation with `{{variable}}` substitution
- Immediate and scheduled notification sends
- Per-tenant, per-channel rate limiting (token bucket)
- Retry with exponential backoff on transient failures, dead-letter after max attempts
- Idempotent sends — duplicate delivery is prevented via a DB-enforced idempotency key
- Delivery tracking and audit trail (every attempt logged, not just final status)

## API Overview

> Fill in as endpoints are built — method, path, role required, short description.

| Method | Path | Role | Description |
|---|---|---|---|
| `POST` | `/api/tenants` | Platform Admin | Create a tenant |
| `POST` | `/api/templates` | Tenant Admin | Create a notification template |
| `POST` | `/api/notifications` | Tenant Admin | Send/schedule a notification |
| `GET`  | `/api/notifications/{id}` | Tenant Admin | Check delivery status |

## Design Decisions

> Document meaningful assumptions and reasoning here as you build. Suggested topics:

- **Why no Kafka / message broker:** the assignment explicitly excludes distributed
  systems and microservices. Dispatch is handled in-process via a bounded
  `ThreadPoolTaskExecutor`, with the `notifications` table acting as the durable queue
  (status-driven) and a `@Scheduled` sweeper picking up queued/retry-due work — this
  gets the "concurrent dispatch, bounded pools, per-tenant fairness" requirements
  without introducing broker infrastructure the requirements rule out.
- **Rate limiting approach:** in-memory token bucket per (tenant, channel), refilled on
  a schedule — no external cache/Redis needed for a single-instance monolith.
- **Idempotency:** enforced at the database layer via a unique constraint on
  `idempotency_key`, not just application-level checks.
- **H2 vs Postgres:** H2 in-memory is the default so the app is truly standalone and
  runs with zero external setup; Postgres is available as an opt-in profile.
- *(add more as you make them — auth design, refund/backoff policy specifics, etc.)*

## Assumptions

> List anything you interpreted or scoped yourself, per the assignment's framing.

- (fill in)

## What's Out of Scope

Per assignment instructions: no UI/frontend, no deployment/containerization/CI-CD, no
distributed systems/microservices, no OAuth/SSO/MFA, no production-grade observability.

## Project Structure

```
src/main/java/com/example/notificationservice/
├── config/       — SecurityConfig, AsyncConfig, SchedulerConfig
├── domain/       — JPA entities
├── dto/          — request/response DTOs
├── repository/   — Spring Data JPA repositories
├── service/      — business logic, incl. service/dispatch for the dispatcher,
│                   rate limiter, and backoff calculator
├── controller/   — REST controllers
├── security/     — JWT filter, auth config
├── exception/    — GlobalExceptionHandler, custom exceptions
└── scheduler/    — retry sweeper, scheduled-send sweeper
```

## AI-Assisted Development

This project was built with AI assistance (Claude). See `Claude.md` for the
development log, prompts, and decisions made during the process, as required by the
submission guidelines.
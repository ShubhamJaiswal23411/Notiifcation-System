# Claude.md — AI-Assisted Development Log

This document records how Claude (Anthropic) was used throughout the development of
this project, per the submission requirement to disclose the AI workflow used.

## Starting point

The Multi-Tenant Notification Service was selected as the target project (over the
three other candidate options provided) on the basis of its role model (two roles,
simpler to reason about exhaustively than the three-to-four-role alternatives), its
single well-defined concurrency problem (dispatch/delivery, rather than compound
inventory-plus-payment races), and because its constraint of "no distributed systems"
against a naturally distributed-systems-shaped problem (multi-channel, high-volume
notification delivery) was judged to be the strongest opportunity to demonstrate
engineering judgment under a real constraint, rather than just feature volume.

With the target fixed and a 36-hour internal deadline set (ahead of the assignment's
48-hour limit, to leave buffer for review and the recorded walkthrough), development
was planned and executed in explicit phases, each with a defined deliverable and a
verification step before moving to the next. Claude was used throughout as a build
partner: generating code against a phase's requirements, and — critically — reviewing
and testing every change against the running application before it was accepted.
Nothing was merged on the strength of an explanation alone.

## Development plan

The 36 hours were allocated up front into six phases, each ending in a working,
manually-verified checkpoint before the next began:

| Phase | Scope | Checkpoint |
|---|---|---|
| 1 | Project scaffold, dependency stack, database/migration strategy | App boots, empty schema created via Flyway on both H2 and PostgreSQL |
| 2 | Schema design and entity mapping | `ddl-auto: validate` passes clean against every table |
| 3 | Auth and RBAC (JWT, two roles, tenant isolation) | Login flow proven end-to-end, 401/403 behavior verified deliberately, not assumed |
| 4 | Core notification/dispatch pipeline (send, render, rate-limit, retry, dead-letter) | Full lifecycle observed live: immediate send, scheduled send, forced retry |
| 5 | Remaining role-requirement gaps (delivery reporting, rate-limit management) | Cross-checked against the original role definitions line by line |
| 6 | Test suite to 90%+ coverage, documentation, recording | Jacoco report reviewed package-by-package, gaps closed deliberately |

Each phase's design decisions were made before implementation, not discovered
reactively — for example, the decision to reject a message-broker architecture in
favor of a DB-backed queue and bounded in-process executor was made at the planning
stage, specifically because the assignment's "no distributed systems" constraint ruled
out the more obvious approach a notification service would normally take at scale.

## Phase 1 — Scaffold and dependency stack

Java 21 and Gradle were chosen up front (Java for LTS stability, Gradle for faster
iteration than Maven at this project's size). Spring Boot 4.1.1 was selected as the
current GA release. Setting this stack up surfaced Spring Boot 4's modularized starter
system (separate `-web`/`-webmvc`, `-data-jpa`, `-security` starters, each paired with
its own `-test` companion, plus a new `spring-boot-h2console` module) early, before any
feature code was written — this was treated as a first-phase discovery to resolve
completely before building on top of it, rather than something to patch around later.

H2 was set as the default datasource specifically so the finished application would be
genuinely standalone (clone and run, no external database required to evaluate it),
with PostgreSQL wired as an explicit opt-in profile, verified to produce an identical
schema via the same Flyway migrations on both.

## Phase 2 — Schema and entity design

Flyway migrations were written first, as the schema's source of truth, with JPA
entities mapped against them second and checked via `ddl-auto: validate` — a
deliberate choice so any mismatch between the intended schema and the entity mapping
would fail loudly at application startup rather than silently drift, which is the
kind of gap that's easy to miss without an explicit verification step.

This phase's validation step caught two real issues before they could propagate into
later phases:
- A shared `BaseEntity` had been designed to provide `created_at` to every entity, but
  `DeliveryAttempt` intentionally uses `attempted_at` instead — the inheritance
  hierarchy was corrected by splitting out an `IdentifiedEntity` root (id only), with
  `BaseEntity` adding `created_at` only for entities that actually have that column.
- A JPA lifecycle callback (`@PrePersist`) added to a subclass shared its method name
  with the parent class's own callback, which Java's standard method-overriding rules
  silently applied to — meaning the parent callback never ran. Resolved by giving each
  lifecycle callback a unique name across the entity hierarchy, and this rule was
  carried forward explicitly for every entity written afterward.

## Phase 3 — Authentication and RBAC

JWT-based auth was designed around two roles (platform admin, tenant admin), with
tenant-scoping deliberately derived from JWT claims rather than trusted from request
parameters — the design decision was to make cross-tenant access structurally
difficult, not just checked. As part of this phase's checkpoint, the 401-vs-403
distinction was tested explicitly rather than assumed correct: this test uncovered
that Spring Security's default handling of anonymous (tokenless) requests can produce
an inconsistent status code depending on internal filter behavior. The fix — disabling
anonymous authentication and wiring a custom `AuthenticationEntryPoint` /
`AccessDeniedHandler` pair — was verified against both the live application and the
later controller test suite, closing the gap for good rather than patching one
symptom of it.

## Phase 4 — Core notification and dispatch pipeline

The dispatch architecture was planned before implementation: the `notifications`
table's `status` column as the durable queue, a bounded `ThreadPoolTaskExecutor` for
concurrent dispatch, a `@Scheduled` sweeper for retries and scheduled sends, and
optimistic locking (`@Version`) for concurrency safety — deliberately reproducing the
guarantees a message broker would provide, without the broker, per the assignment's
constraints.

This phase's checkpoint (observing a notification move through its full lifecycle
live) caught a genuine concurrency-framework bug: a `@Transactional` method called
from within the same class as its `@Async` caller silently bypassed Spring's proxy
entirely, meaning the transaction never actually applied and an exception was being
swallowed without any visible trace — notifications were getting permanently stuck.
This was resolved by splitting the dispatch logic into its own bean so the proxy
mechanism applies correctly, with explicit exception logging added to the async
boundary as a standing safeguard against the same class of silent failure recurring
elsewhere.

## Phase 5 — Closing remaining role-requirement gaps

Before moving to the test-writing phase, the implemented endpoints were checked
against the original role definitions line by line ("platform admin: manage tenants
and global limits," "tenant admin: ... view delivery reports"). This review found two
gaps not yet covered by anything built in phases 1–4 — rate limit management and
delivery/attempt reporting — which were scoped and closed before feature work was
considered complete, rather than being left implicit.

## Phase 6 — Test strategy and coverage

Test strategy was decided deliberately rather than applying one technique uniformly:
- **Pure-logic classes** (`TemplateRenderer`, `RetryBackoffCalculator`,
  `RateLimiterService`) — plain JUnit, no mocking, since these have zero framework
  dependency.
- **Service layer** — JUnit + Mockito, with one test per meaningful branch
  (not-found, conflict/idempotency, rate-limit-exceeded, retry-vs-dead-letter),
  chosen specifically because engineering these exact conditions is far faster and
  more precise with mocked repositories than with real database state.
- **Controllers** — `@WebMvcTest` slices with the real security configuration
  imported, so role enforcement is genuinely exercised rather than assumed.
- **Full-stack verification** — a repeatable PowerShell smoke-test script
  (`scripts/smoke-test.ps1`) covering the entire running application end-to-end,
  including real asynchronous dispatch and scheduler timing that the unit test suite
  deliberately does not wait on.

Writing the controller test slice surfaced two further Spring Boot 4-specific
findings, each resolved and folded into the pattern used for every subsequent
controller test:
- `@MockBean` was removed in Spring Boot 4.0 in favor of Spring Framework 7's
  `@MockitoBean`, and `@WebMvcTest` requires its own dedicated test starter.
- Mocking the JWT filter itself broke the request pipeline entirely (an unstubbed
  mocked `Filter` never calls `chain.doFilter(...)`) — resolved by mocking the
  filter's dependencies instead of the filter, letting the real filter execute.

Coverage was then reviewed package-by-package against Jacoco's report, with gaps
(rather than an aggregate percentage) driving which classes received additional
tests, until every package in scope reached the 90%+ target.

## Skills / tooling used during development
- Claude's conversational code generation, systematic review, and debugging (this
  log summarizes the process end-to-end).
- No external code-generation tools, scaffolding CLIs, or IDE AI plugins were used
  beyond the conversation itself and standard IDE/Gradle tooling for compiling,
  running, and testing the generated code.

## Raw development artifacts
The full conversation transcript used during development accompanies this submission
per the requirement to include raw files used during development.

# Restaurant Operating System

**A multi-tenant restaurant operations backend built with Java 17 and Spring Boot 4.**

[![Java 17](https://img.shields.io/badge/Java-17-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot 4](https://img.shields.io/badge/Spring_Boot-4.0.1-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![MySQL 8](https://img.shields.io/badge/MySQL-8-4479A1?logo=mysql&logoColor=white)](https://www.mysql.com/)
[![Docker](https://img.shields.io/badge/Docker-Containerized-2496ED?logo=docker&logoColor=white)](Dockerfile)
[![CI](https://github.com/ahmadkadri978/restaurant-operating-system/actions/workflows/build-push.yaml/badge.svg)](https://github.com/ahmadkadri978/restaurant-operating-system/actions/workflows/build-push.yaml)

Restaurant Operating System is a **backend-focused platform for managing multiple restaurants** through one application. It connects the full operational flow—from QR-based table ordering to kitchen coordination, service requests, billing, cash register reconciliation, and owner analytics.

Guests scan a table QR code and enter an active session code to browse the menu and place orders without installing an app. Restaurant staff receive real-time updates; managers and owners administer operations and financial workflows.

> **Portfolio scope:** This repository contains the **Spring Boot backend**. The HTML/JavaScript frontend is maintained separately. See the [system overview](SYSTEM_OVERVIEW.md) for additional implementation notes.

## Application Demo

Watch the restaurant workflow in action, including customer ordering, staff and kitchen interfaces, live order status updates, and table-session operations.

<video src="https://raw.githubusercontent.com/ahmadkadri978/restaurant-operating-system/main/restaurant-operating-system-demo-muted-1.mp4" controls muted playsinline width="100%">
  Your browser does not support embedded video. [Watch the demo on GitHub](https://github.com/ahmadkadri978/restaurant-operating-system/blob/main/restaurant-operating-system-demo-muted-1.mp4).
</video>

[Open the demo video](https://github.com/ahmadkadri978/restaurant-operating-system/blob/main/restaurant-operating-system-demo-muted-1.mp4) · 1 min 55 sec · no audio

> The application's user interface is in Arabic, while the demo includes English on-screen explanations to guide viewers through the workflow. It demonstrates backend-driven operations and real-time coordination across different restaurant roles.

## What the system does

| Area | Capabilities |
| --- | --- |
| **Customer ordering** | Table QR access, four-digit session verification, digital menus, order submission and tracking |
| **Restaurant operations** | Restaurant/menu/table/staff management, order processing, kitchen workflows, service calls |
| **Real-time updates** | SSE notifications, Redis Pub/Sub distribution across application instances, FCM staff alerts |
| **Financial workflows** | Restaurant-specific charges, bill previews/finalization, cash payments, tips, change, PDF invoices |
| **Cash register** | Opening balances, cash-in/out movements, closing reconciliation and discrepancy tracking |
| **Owner analytics** | Revenue and operational reporting with configurable reporting periods |
| **Administration** | Role-based access and tenant-scoped operations |

### End-to-end workflow

1. **Configure a restaurant:** Create restaurant records, menus, tables, and staff accounts.
2. **Open a table session:** Staff activate a table session and its temporary four-digit code.
3. **Place an order:** A guest scans the table QR code, verifies the session, and orders from the current menu.
4. **Fulfill and assist:** Staff and kitchen teams process orders and receive real-time updates; guests can request assistance.
5. **Settle the session:** Calculate restaurant-specific charges, record payment, generate an invoice, and release the table.
6. **Review operations:** Owners inspect analytics and cash register reconciliation.

## Technology stack

| Concern | Implementation |
| --- | --- |
| Language / framework | Java 17, Spring Boot 4.0.1, Spring Web MVC |
| Persistence | Spring Data JPA, Hibernate, MySQL 8 |
| Schema management | Flyway migrations |
| Security | Spring Security, JWT, BCrypt |
| Real-time delivery | Server-Sent Events (SSE), Redis Pub/Sub |
| Notifications | Firebase Cloud Messaging |
| Documentation | OpenAPI / Swagger UI |
| PDF invoices | OpenPDF |
| Observability | Spring Boot Actuator, Micrometer/Prometheus, structured JSON logging |
| Tests | JUnit 5, Mockito, Spring Boot Test, MockMvc, H2 test profile |
| Delivery | Maven, GitHub Actions, Docker, Kubernetes/Helm configuration |

## Architecture

The backend is a **domain-oriented modular monolith**: one deployable Spring Boot application, organized into business-focused packages.

```text
    Guest (QR + session)            Staff / Kitchen / Owner
             |                               |
             +------------ REST API ---------+
                              |
                   +----------------------+
                   |  Spring Boot API     |
                   |  Security / JWT      |
                   |----------------------|
                   | Restaurant & Menu    |
                   | Tables & Sessions    |
                   | Orders & Service     |
                   | Finance & Invoices   |
                   | Owner Analytics      |
                   +----------------------+
                        |             |
                   JPA / Flyway   Domain events
                        |             |
                      MySQL       AFTER_COMMIT
                                      |
                                 SSE bridge
                                 /        \
                         Local dispatch  Redis Pub/Sub
                                 \        /
                                  SSE clients

                       Firebase push notifications
```

### Engineering decisions

**Transactional order processing.** Order submission verifies the active session, locks the table row for the write path, validates item availability and quantities, resolves prices server-side, and persists the order. A per-table rate limit reduces repeated submissions. Restaurant-specific display order numbers are generated in the backend.

**Concurrency-aware table sessions.** Opening and closing sessions use pessimistic row locking. An already-open session can return its existing code; an already-closed session can be handled idempotently in the standard close path. Session history is stored separately from the table's current session state.

**Publish events after commit.** The `SseAfterCommitListener` handles outbound events using `@TransactionalEventListener(phase = AFTER_COMMIT)`. The `SseEventBridge` delegates to local dispatch or Redis-based fan-out. This prevents notifications for rolled-back transactions; delivery itself remains best-effort, so clients should resynchronize after interruptions.

**Financial consistency.** Bills are computed from recorded order totals and restaurant-specific financial rules. Rules support fixed charges and percentages, including compound percentages applied in a defined order. Payment-based session closure finalizes the bill, validates cash received, calculates tips and change, generates an invoice, records the payment, and releases the table within a transactional service method.

**Invoice snapshots and idempotency.** Invoice generation checks for an existing session invoice and records a snapshot of relevant invoice data and items, rather than relying exclusively on later-changing menu information.

**Cash reconciliation.** The cash register computes expected closing cash as opening balance + received cash − returned change + cash-in movements − cash-out movements, and records the difference from the actual counted balance.

**Tenant boundaries.** Restaurant-scoped queries and ownership checks separate operations belonging to different restaurants. Service-layer isolation tests cover representative cross-restaurant scenarios; they are not a claim of formally verified isolation across every endpoint.

Explore the implementation in [`src/main/java/com/restaurantqr/`](src/main/java/com/restaurantqr/) and the [system overview](SYSTEM_OVERVIEW.md).

## Testing and CI/CD

The repository contains automated tests for domain logic, API behavior, and selected concurrency and tenant-isolation scenarios. The GitHub Actions pipeline runs Maven tests with Java 17 **before** the Docker build job.

```text
Push / Pull Request
        |
   Maven tests
        |
   Docker build
        |
   Image publishing (push events)
        |
   Helm artifact updates (push events)
```

Run the test suite:

```bash
bash ./mvnw -B test -Dspring.profiles.active=test
```

The test profile uses H2 and disables Firebase initialization.

[View CI workflow](.github/workflows/build-push.yaml) · [View test sources](src/test/) · [View workflow runs](https://github.com/ahmadkadri978/restaurant-operating-system/actions)

## Performance testing

A [documented 30-minute k6 soak test](SOAK_TEST_REPORT.md), run in a Docker Compose environment, reports:

| Metric | Reported result |
| --- | ---: |
| Virtual users | 30 |
| Restaurants represented | 200 |
| HTTP requests | 11,987 |
| Orders created | 4,441 |
| p95 response time | ~51 ms |
| Request error rate | ~0.09% |

These are **results reported for that specific test setup**, not independently reproduced benchmarks, production SLAs, or evidence of maximum system capacity. Consult the [full report](SOAK_TEST_REPORT.md) for its methodology and limitations.

## Project structure

```text
src/main/java/com/restaurantqr/
├── admin/          Restaurant administration
├── auth/           Authentication and authorization
├── config/         Application configuration
├── exception/      Exception handling
├── finance/        Bills, payments, invoices, cash register
├── menu/           Menu management
├── order/          Customer and staff order workflows
├── owner/          Owner analytics
├── restaurant/     Restaurant domain
├── servicecall/    Customer service requests
├── sse/            Real-time event delivery
├── table/          Tables and session lifecycle
└── user/           User domain

src/main/resources/  Application configuration and Flyway migrations
src/test/            Automated tests
helm/                Kubernetes Helm configuration
.github/workflows/   CI/CD
Dockerfile           Container image definition
```

## Configuration and local development

**Prerequisites:** Java 17, a compatible MySQL 8 database, and the Maven Wrapper. Redis is used when multi-instance SSE distribution is enabled; Firebase requires application credentials when its integration is enabled.

The application uses Spring profiles and environment-based configuration. Key values include:

| Environment variable | Purpose |
| --- | --- |
| `APP_JWT_SECRET` | JWT signing secret |
| `DB_URL` | Database JDBC URL |
| `DB_USERNAME` / `DB_PASSWORD` | Database credentials |
| `ADMIN_PASSWORD` | Initial administrator password |
| `REDIS_ENABLED` / `REDIS_HOST` | Redis event distribution |
| `APP_UPLOAD_DIR` | Upload storage directory |

With the required environment and database configured, the application can be started using the Maven Wrapper:

```bash
bash ./mvnw spring-boot:run
```

A fully verified, one-command local environment setup has **not yet been documented**. Review [application configuration](src/main/resources/), the [Dockerfile](Dockerfile), and [system notes](SYSTEM_OVERVIEW.md) before running. Never commit real credentials.

When the application is running with the appropriate settings, the API documentation is exposed through Swagger UI:

```text
http://localhost:8080/swagger-ui/index.html
```

## Deployment and operations

The repository includes a [Dockerfile](Dockerfile), [Helm chart](helm/backend-repo/), Kubernetes health probes, and GitHub Actions automation for image publishing and Helm release updates. The deployment configuration includes Redis, database configuration, ingress, and sealed secret resources.

Deployment manifests are provided as implementation artifacts; they do not by themselves guarantee production readiness in a new environment.

## Further reading

- [System overview and implementation notes](SYSTEM_OVERVIEW.md)
- [Soak test report](SOAK_TEST_REPORT.md)
- [Application source](src/main/java/com/restaurantqr/)
- [Automated tests](src/test/)
- [CI/CD workflow](.github/workflows/build-push.yaml)
- [Helm configuration](helm/backend-repo/)

---

**Developed by [Ahmad Kadri](https://github.com/ahmadkadri978)**  
Java Backend Development · Spring Boot · REST APIs · MySQL · Docker · Kubernetes

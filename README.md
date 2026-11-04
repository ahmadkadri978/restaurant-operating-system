# Restaurant Operating System

**Multi-tenant QR ordering, table service, and restaurant operations — built with Java 17 and Spring Boot 4.**

A backend-focused restaurant platform where guests scan a table QR code, enter an active session code, browse the menu, place orders, request staff assistance, and review their bill. Staff and kitchen teams receive updates in real time; restaurant owners manage financial rules and review operational analytics.

> **Portfolio project:** This repository showcases backend architecture, transactional consistency, authorization, event delivery, and operational tooling. See [System Overview](SYSTEM_OVERVIEW.md) for implementation details.

## At a glance

| Area | Implementation |
| --- | --- |
| Backend | Java 17, Spring Boot 4.0.1, Spring Web MVC, Spring Data JPA |
| Security | Spring Security, JWT, BCrypt, role-based authorization |
| Persistence | MySQL 8, Flyway migrations |
| Real-time | Server-Sent Events (SSE), Redis pub/sub for multi-instance fan-out |
| Notifications | Firebase Cloud Messaging (FCM) |
| Operations | Docker, Nginx, Helm configuration, Micrometer/Prometheus, structured logging |
| API documentation | OpenAPI / Swagger UI |
| Billing | Per-restaurant financial rules, invoice PDF generation |

## Product workflow

1. **Restaurant setup:** Administrators configure restaurants, menus, tables, and staff accounts.
2. **Table session:** A manager opens a session; a guest scans the table QR code and enters the four-digit session code.
3. **Ordering:** Guests browse the live menu, submit orders, and track their status.
4. **Fulfillment:** Staff and kitchen roles process orders with SSE-driven updates and a polling fallback.
5. **Service and billing:** Guests request assistance or a bill; restaurant-specific charges are applied and invoices can be generated.
6. **Management:** Owners access analytics and configure financial rules.

## Architecture

```text
Guest browser (QR + session)       Staff / Kitchen / Owner
              |                              |
              +----------- HTTP API ---------+
                             |
                    Spring Boot backend
                    |       |        |
              JWT / RBAC  Domain   SSE events
                         services     |
                            |     After-commit
                      JPA + Flyway    |
                            |       Redis pub/sub
                          MySQL        |
                                   SSE clients
                                      |
                                FCM staff alerts
```

The codebase is organized by domain (authentication, restaurant administration, menus, orders, table sessions, service calls, finance, and owner analytics). Service-layer transactions and pessimistic locking address concurrent order/session operations. Outbound SSE notifications are published after database commit to avoid broadcasting rolled-back changes.

**Design considerations:** Tenant-scoped data access, role-specific endpoints, rate limiting, event fan-out across instances, and structured request tracing. For trade-offs and constraints, read the [architecture and system notes](SYSTEM_OVERVIEW.md).

## Engineering highlights

- **Multi-tenant boundaries:** Restaurant-specific access checks and data scoping.
- **Consistency under concurrency:** Transaction boundaries and pessimistic locking for critical order/session paths.
- **Real-time delivery:** SSE with Redis pub/sub and a client-side resynchronization strategy.
- **Security:** JWT-based authentication, BCrypt password hashing, role-based authorization, and QR/session checks for guest flows.
- **Operational visibility:** Metrics, correlation IDs, and structured logs.
- **Deployment artifacts:** [Dockerfile](Dockerfile) and [Helm charts](helm/).

## Testing and performance

The repository includes a [30-minute k6 soak-test report](SOAK_TEST_REPORT.md). **In that documented Docker Compose test environment only**, the report records:

| Metric | Reported result |
| --- | ---: |
| Requests | 11,987 |
| Virtual users | 30 |
| p95 latency | ~51 ms |
| Request error rate | ~0.09% |

These measurements are **not production SLAs, general capacity guarantees, or independently reproduced benchmarks**. See the report for the workload, infrastructure, and methodology.

## Explore the code

- [Application source](src/)
- [Maven build](pom.xml)
- [Architecture and domain details](SYSTEM_OVERVIEW.md)
- [Soak-test report](SOAK_TEST_REPORT.md)
- [GitHub Actions workflows](.github/workflows/)
- [Helm configuration](helm/)

## Running locally

This is a multi-service application with external dependencies, including MySQL and optionally Redis and Firebase. Configuration and credentials are environment-specific. Consult the application configuration, Dockerfile, and system overview before running it; **do not commit real credentials**. A fully verified, one-command local setup guide is still to be documented.

## Project scope

This repository contains the **backend** of the restaurant platform. The system overview also describes a separate HTML/JavaScript frontend. Deployment, performance, and security claims should be assessed against the specific configuration and test environment.

---

**Author / portfolio:** [Ahmad Kadri](https://github.com/ahmadkadri978) · [Technical system overview](SYSTEM_OVERVIEW.md)

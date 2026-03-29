# SYSTEM_OVERVIEW.md — Restaurant QR Ordering & Service Call System

> **Version:** 1.0  
> **Last Updated:** 2026-03-30  
> **Purpose:** Comprehensive system memory/context file. Read this before any development work.

---

## Table of Contents

1. [System Overview](#1-system-overview)
2. [Architecture](#2-architecture)
3. [Core Domains](#3-core-domains)
4. [Data Flow](#4-data-flow)
5. [Realtime System (SSE + Redis)](#5-realtime-system-sse--redis)
6. [Security Model](#6-security-model)
7. [Financial Model](#7-financial-model)
8. [Scaling Strategy](#8-scaling-strategy)
9. [Observability](#9-observability)
10. [Key Design Decisions](#10-key-design-decisions)
11. [Known Constraints / Tradeoffs](#11-known-constraints--tradeoffs)
12. [Future Improvements](#12-future-improvements)

---

## 1. System Overview

### What It Is

A **multi-tenant restaurant ordering and service call system** built around QR codes. Customers scan a QR code on their table, enter a 4-digit session code provided by staff, browse the restaurant's menu, place orders, request services (waiter, bill, hookah/nara), and view their itemized bill — all from their mobile browser without installing an app.

### What Problem It Solves

- **Eliminates paper menus** — the menu is digital, always up-to-date, with images and availability toggles.
- **Reduces wait times** — orders go directly from the customer's phone to the staff/kitchen screen in real time.
- **Streamlines service calls** — customers request a waiter, bill, or hookah with one tap; staff see it instantly via SSE + push notifications.
- **Automates billing** — session bills are computed with configurable taxes and fees; invoices are generated as PDFs on session close.
- **Supports multiple restaurants** — each restaurant is fully isolated (data, users, financial rules) on a shared infrastructure.
- **Scales horizontally** — multiple backend instances can run behind a load balancer, with Redis pub/sub ensuring all SSE clients receive events regardless of which instance handled the write.

### Tech Stack Summary

| Layer | Technology |
|---|---|
| Backend | Java 17, Spring Boot 4.0.1, Spring Web MVC, Spring Data JPA, Spring Security |
| Database | MySQL 8.0, Flyway migrations (19 versioned scripts) |
| Auth | JWT (jjwt 0.12.5), BCrypt passwords, `@PreAuthorize` method security |
| Realtime | Server-Sent Events (SSE) via `SseEmitter`, Redis pub/sub for multi-instance fan-out |
| Push Notifications | Firebase Cloud Messaging (FCM) for staff push alerts |
| PDF Generation | OpenPDF 2.0.3 with embedded Amiri Arabic font |
| Frontend | Static HTML + vanilla JavaScript (no framework), served via Nginx |
| Infrastructure | Docker, Docker Compose, Nginx reverse proxy, Redis 7 |
| Observability | Micrometer + Prometheus metrics, structured JSON logging (Logstash encoder), MDC correlation IDs |
| API Docs | SpringDoc OpenAPI 3.0.1 (Swagger UI) |

---

## 2. Architecture

### 2.1 Backend Architecture

The backend is a Spring Boot application organized into **domain-based packages** under `com.restaurantqr`:

```
com.restaurantqr/
├── admin/           # ADMIN restaurant CRUD (setup, images, users, tables, menu)
│   └── dto/         # Admin request/response records
├── auth/            # JWT auth (login, filter, service, security config)
│   └── dto/         # AuthPrincipal, LoginRequest, LoginResponse
├── config/          # Seeders, Firebase, OpenAPI, ProfileLogger, RequestCorrelationFilter
├── exception/       # Global handler, custom exceptions (ApiException, BusinessRuleException, etc.)
├── finance/         # Financial rules, bill calculation, invoices, PDF rendering
│   └── dto/         # Bill/rule/invoice DTOs
├── menu/            # MenuCategory, MenuItem, MenuController (public)
├── order/           # CustomerOrder, OrderItem, public OrderController
│   └── staff/       # Staff/Kitchen order controllers, StaffOrderService, OrderStatus enum
├── owner/           # Owner dashboard (analytics, trends, peak hours)
├── restaurant/      # Restaurant entity and repository
├── servicecall/     # ServiceCall, FCM push, cooldown logic
├── sse/             # SSE emitter registries, event publisher, heartbeat, event types
│   └── bridge/      # SseEventBridge interface, RedisEventBridge, LocalEventBridge, config
├── table/           # RestaurantTable, TableSession, public/staff session controllers
└── user/            # User entity, Role enum, UserRepository
```

**Key architectural patterns:**

- **DTOs as Java records** — all request/response objects are immutable records.
- **Service interfaces + impl** — `BillCalculationService`, `InvoiceService`, `FinancialRuleService`, `RestaurantSetupService`, `OwnerDashboardService` have interface/implementation separation.
- **Transaction boundaries** — `@Transactional` on service methods; SSE events are published via `@TransactionalEventListener(phase = AFTER_COMMIT)` to prevent phantom broadcasts on rollback.
- **Pessimistic locking** — used for order submission (table row), session open/close (table row), and order counter increment to prevent race conditions under concurrency.
- **Pagination** — staff/kitchen order listings use Spring `Page<T>` with configurable page/size.

### 2.2 Frontend Structure

The frontend is a collection of standalone HTML pages, each with embedded CSS and JavaScript. No build step or framework is used.

| File | Purpose | Auth |
|---|---|---|
| `screen1.html` | **Customer menu** — browse categories, items, add to cart | Public (QR token + session code) |
| `screen2.html` | **Customer cart** — review cart, submit order | Public |
| `screen3.html` | **Customer order tracking** — view session orders + statuses | Public |
| `screen4.html` | **Customer service calls** — request waiter/bill/nara | Public |
| `screen5.html` | **Staff login** — username/password → JWT | Public |
| `screen6.html` | **Staff new orders** — see NEW orders, send to kitchen | JWT (MANAGER/STAFF) |
| `screen6-calls.html` | **Staff service calls** — see active service calls | JWT (MANAGER/STAFF) |
| `screen7.html` | **Kitchen screen** — see SENT_TO_KITCHEN orders, mark DONE | JWT (KITCHEN/MANAGER) |
| `screen_table_session.html` | **Table session management** — open/close sessions | JWT (MANAGER) |
| `session.html` | **Customer session entry** — enter 4-digit code after QR scan | Public |
| `owner-login.html` | **Owner login** | Public |
| `owner-dashboard.html` | **Owner analytics dashboard** — charts, trends, statistics | JWT (OWNER) |
| `owner-financial-settings.html` | **Owner financial rules** — CRUD tax/fee rules | JWT (OWNER) |
| `invoices.html` | **Invoice listing** — list/search/download PDF invoices | JWT (MANAGER/OWNER) |
| `admin-login.html` | **Admin login** | Public |
| `admin-setup.html` | **Admin restaurant setup** — create restaurants with menu, tables, users | JWT (ADMIN) |
| `config.js` | **Runtime config** — auto-detects API base URL, sets timezone/locale/currency | — |

**Config resolution:** `config.js` sets `window.APP_CONFIG.API_BASE` dynamically. If running on `localhost:3000` (frontend dev port), it points to `localhost:8080`. Otherwise, it uses the current origin (when served through Nginx gateway).

### 2.3 Realtime Architecture (SSE + Redis)

Two independent SSE registries serve different audiences:

1. **Staff SSE** (`SseEmitterRegistry`) — endpoint: `GET /api/v1/sse/events` (JWT required)
   - Receives: `ORDER_CREATED`, `ORDER_SENT_TO_KITCHEN`, `ORDER_DONE`, `ORDER_CANCELLED`, `SERVICE_CALL`
   - Consumers: `screen6.html`, `screen6-calls.html`, `screen7.html`

2. **Public Menu SSE** (`PublicMenuSseRegistry`) — endpoint: `GET /api/v1/public/sse/menu` (QR token + session code)
   - Receives: `MENU_ITEM_UPDATED`, `MENU_ITEM_DELETED`
   - Consumers: `screen1.html`

Both registries use `ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>>` keyed by `restaurantId`.

For multi-instance deployments, the **`SseEventBridge`** abstraction ensures events reach all instances:
- `RedisEventBridge` — publishes to Redis channel `sse:restaurant-events`; activated when `app.redis.enabled=true`
- `LocalEventBridge` — fallback; broadcasts directly to local registries when Redis is not configured

### 2.4 Deployment Architecture

```
                    ┌──────────────────────────────────────┐
                    │           Nginx Gateway              │
                    │        (port 80, reverse proxy)      │
                    │  - /app/*     → frontend:80          │
                    │  - /api/*     → backend upstream     │
                    │  - /uploads/* → frontend:80/uploads  │
                    │  - Rate limiting + SSE proxy config  │
                    └──────┬──────────────┬────────────────┘
                           │              │
                    ┌──────▼──────┐ ┌─────▼───────┐
                    │ backend-1   │ │ backend-2   │   (round-robin)
                    │ Spring Boot │ │ Spring Boot │
                    │ port 8080   │ │ port 8080   │
                    └──────┬──────┘ └──────┬──────┘
                           │               │
              ┌────────────┴───────────────┴────────────┐
              │                                          │
        ┌─────▼─────┐                          ┌────────▼────────┐
        │  MySQL 8.0 │                          │   Redis 7       │
        │  (data)    │                          │   (pub/sub      │
        │            │                          │    SSE fan-out)  │
        └────────────┘                          └─────────────────┘
```

**Docker services (docker-compose.yml):**

| Service | Image | Purpose |
|---|---|---|
| `mysql` | `mysql:8.0` | Primary data store. 200 max connections, 256MB InnoDB buffer pool |
| `flyway` | `flyway/flyway:10` | Runs migrations on startup, then exits |
| `redis` | `redis:7-alpine` | SSE event pub/sub backplane. 128MB memory limit |
| `frontend` | Custom (nginx:alpine + static files) | Serves HTML/JS/CSS |
| `backend` | Custom (eclipse-temurin:17-jre-alpine + Spring Boot JAR) | Application server |
| `gateway` | `nginx:alpine` | Reverse proxy, rate limiting, SSE proxy config |

**Multi-instance mode** uses `docker-compose.multi-instance.yml` (overlay file) that replaces the single `backend` with `backend-1` and `backend-2`, each with a unique `INSTANCE_ID` and `REDIS_ENABLED=true`.

---

## 3. Core Domains

### 3.1 Restaurant

- **Entity:** `Restaurant` (table: `restaurants`)
- **Fields:** `id`, `name`, `code` (unique), `isActive`, `createdAt`, `updatedAt`
- **Multi-tenancy:** All queries filter by `restaurantId` extracted from the JWT. Users only see their own restaurant's data.
- **Setup:** Created by ADMIN via `POST /api/v1/admin/restaurants/setup` with menu categories, items, tables, and users in a single atomic transaction.

### 3.2 Table & QR Flow

- **Entity:** `RestaurantTable` (table: `restaurant_tables`)
- **Fields:** `id`, `restaurant_id`, `tableNumber`, `qrToken` (unique UUID), `isActive`, `sessionCode` (4-digit, nullable), `sessionOpenedAt` (nullable)
- **QR Code:** Each table has a permanent `qrToken`. The QR code encodes a URL like `https://domain/app/session.html?token={qrToken}`.
- **Session fields on table:** `sessionCode` and `sessionOpenedAt` represent the **live** session state. When a session is open, these are populated. When closed, both are set to `null`.

### 3.3 TableSession Lifecycle

- **Entity:** `TableSession` (table: `table_sessions`)
- **Fields:** `id`, `restaurant_id`, `table_id`, `sessionCode`, `openedAt`, `closedAt`, `durationMinutes`, `createdAt`
- **Purpose:** Historical record. While `RestaurantTable.sessionCode` holds the live state, `TableSession` is the permanent audit trail.

**Lifecycle:**

```
[Staff opens session]
  → StaffTableSessionService.openSession()
  → Pessimistic lock on table row (findByIdForUpdate)
  → Generate random 4-digit code (retry on UNIQUE collision, up to 30 attempts)
  → Set table.sessionCode + table.sessionOpenedAt
  → Create TableSession record with openedAt
  → Return code to staff → staff tells customer the code

[Customer scans QR]
  → Lands on session.html?token={qrToken}
  → Enters 4-digit code
  → POST /api/v1/public/tables/{qrToken}/session/verify
  → PublicSessionService.verifySessionOrThrow() validates code matches
  → Redirected to menu (screen1.html)

[Staff closes session]
  → StaffTableSessionService.closeSession()
  → Pessimistic lock on table row
  → Find open TableSession, set closedAt + calculate durationMinutes
  → Finalize session bill (snapshot financial rules, compute totals)
  → Generate invoice (snapshot all session data)
  → Clear table.sessionCode + table.sessionOpenedAt
```

### 3.4 Orders

- **Entities:** `CustomerOrder` (table: `customer_orders`), `OrderItem` (table: `order_items`)
- **Key fields on CustomerOrder:** `id`, `restaurant_id`, `table_id`, `table_session_id` (FK), `restaurantOrderNumber` (auto-incremented per restaurant), `status`, `totalAmount`, `note`, `sentToKitchenAt`, `doneAt`, `cancelledAt`, `createdAt`
- **Key fields on OrderItem:** `id`, `order_id`, `menuItemId`, `quantity`, `unitPrice` (snapshot), `totalPrice`, `note`

**Order status flow:**

```
NEW → SENT_TO_KITCHEN → DONE
                      → CANCELLED
NEW → CANCELLED
```

- `NEW` — customer submitted; visible on staff screen (screen6)
- `SENT_TO_KITCHEN` — staff forwarded to kitchen; visible on kitchen screen (screen7)
- `DONE` — kitchen marked complete
- `CANCELLED` — staff cancelled the order

**Business rules:**
- Prices are **server-side snapshots** from `menu_items.price` at order time — the frontend never sends prices.
- **Rate limit:** one order per table per 60 seconds (enforced both in backend and Nginx).
- **Order number:** atomically generated per restaurant using `RestaurantOrderCounter` with pessimistic locking.
- Orders are linked to the active `TableSession` via FK when available.

### 3.5 Service Calls

- **Entity:** `ServiceCall` (table: `service_calls`)
- **Fields:** `id`, `restaurant_id`, `table_id`, `callType`, `createdAt`
- **CallType enum:** `WAITER`, `BILL`, `NARA` (hookah)
- **Cooldown:** 180 seconds (3 minutes) between calls from the same table. Active calls are those created within the last 3 minutes.
- **Notifications:** Each service call triggers both an SSE event to connected staff AND an FCM push notification to staff devices with registered push tokens.

### 3.6 Menu

- **Entities:** `MenuCategory` (table: `menu_categories`), `MenuItem` (table: `menu_items`)
- **MenuItem fields:** `id`, `restaurant_id`, `category_id`, `name`, `description`, `price` (DECIMAL 10,2), `imageUrl`, `isActive`, `isAvailable`, `displayOrder`
- **Public endpoint:** `GET /api/v1/public/tables/{qrToken}/menu?code={code}`
- **Live updates:** Admin changes (create/update/delete items) broadcast `MENU_ITEM_UPDATED` / `MENU_ITEM_DELETED` events via the public menu SSE stream so customer screens update in real time.

---

## 4. Data Flow

### 4.1 Complete Flow: QR Scan → Order → Kitchen → Billing → Invoice

```
Step 1: QR SCAN
────────────────
Customer scans QR code on table
  → Browser opens: /app/session.html?token={qrToken}
  → session.html extracts qrToken from URL

Step 2: SESSION VERIFICATION
─────────────────────────────
Customer enters 4-digit code (given verbally by staff)
  → POST /api/v1/public/tables/{qrToken}/session/verify
  → Backend: PublicSessionService.verifySessionOrThrow()
    → Loads table by qrToken
    → Checks table.isActive, restaurant.isActive
    → Compares input code with table.sessionCode
  → On success: frontend stores qrToken + code in sessionStorage
  → Redirects to screen1.html (menu)

Step 3: BROWSE MENU
────────────────────
  → GET /api/v1/public/tables/{qrToken}/menu?code={code}
  → Backend: MenuService.getMenuByQrToken()
    → Returns categories + items (only active & available)
  → Customer browses, adds items to in-memory cart
  → Public menu SSE connection opened for live item updates

Step 4: SUBMIT ORDER
─────────────────────
Customer taps "Send Order"
  → POST /api/v1/public/tables/{qrToken}/orders?code={code}
  → Body: { items: [{ menuItemId, quantity, note }], note }
  → Backend: OrderService.submitOrder()
    → Verify session code
    → Pessimistic lock on table row
    → Rate limit check (1 order/min/table)
    → Validate all menuItemIds exist, are active, available
    → Snapshot prices from DB (server-side calculation)
    → Create CustomerOrder + OrderItems
    → Generate restaurantOrderNumber (atomic counter)
    → Link order to active TableSession
    → Publish SseOutboundEvent(ORDER_CREATED) via ApplicationEventPublisher
  → @TransactionalEventListener(AFTER_COMMIT)
    → SseAfterCommitListener receives event
    → Delegates to SseEventBridge
      → Redis mode: RedisEventBridge publishes to Redis channel
      → Local mode: LocalEventBridge sends directly to local registry
  → All backend instances receive event via Redis subscriber
  → Each instance broadcasts to its local SSE emitters
  → Staff screen (screen6.html) receives ORDER_CREATED via SSE

Step 5: STAFF SENDS TO KITCHEN
───────────────────────────────
Staff sees new order on screen6.html
  → PATCH /api/v1/manager/orders/{orderId}/sent-to-kitchen
  → Backend: StaffOrderService.markSentToKitchen()
    → Pessimistic lock on order row
    → Status: NEW → SENT_TO_KITCHEN
    → Set sentToKitchenAt timestamp
    → Publish ORDER_SENT_TO_KITCHEN event
  → Kitchen screen (screen7.html) receives event via SSE
  → Order appears on kitchen display

Step 6: KITCHEN MARKS DONE
───────────────────────────
Kitchen completes the order
  → PATCH /api/v1/kitchen/orders/{orderId}/done
  → Backend: StaffOrderService.markDone()
    → Status: SENT_TO_KITCHEN → DONE
    → Set doneAt timestamp
    → Publish ORDER_DONE event
  → Staff screen updates order status
  → Customer screen (screen3.html) shows updated status on next poll

Step 7: VIEW BILL (optional, while session is open)
─────────────────────────────────────────────────────
Customer requests bill
  → GET /api/v1/public/tables/{qrToken}/bill?code={code}
  → Backend: BillCalculationService.getOrComputeBill()
    → If finalized bill exists → return snapshot
    → Otherwise compute DRAFT bill:
      → Sum totalAmount of all non-cancelled session orders
      → Load active financial rules (ordered by apply_order)
      → Apply each rule (FIXED or PERCENTAGE, simple or compound)
      → Return itemsSubtotal + charges breakdown + grandTotal
  → Customer sees itemized bill with taxes/fees

Step 8: SESSION CLOSE → BILL FINALIZE → INVOICE
──────────────────────────────────────────────────
Staff closes the session
  → POST /api/v1/manager/tables/{tableId}/session/close
  → Backend: StaffTableSessionService.closeSession()
    → Pessimistic lock on table row
    → Close TableSession (set closedAt, calculate duration)
    → Finalize bill:
      → BillCalculationService.finalizeBill()
      → Snapshot all financial rules + computed amounts
      → Persist SessionBill (FINALIZED) + SessionBillCharges
    → Generate invoice:
      → InvoiceService.generateInvoice()
      → Create SessionInvoice with snapshot of all session data
      → Aggregate order items by (name, unitPrice) for line items
      → Generate invoice number: INV-YYYYMMDD-SEQ
    → Clear table.sessionCode + table.sessionOpenedAt

Step 9: VIEW/DOWNLOAD INVOICE
──────────────────────────────
Manager/Owner accesses invoices.html
  → GET /api/v1/manager/invoices?date=2026-03-30
  → GET /api/v1/manager/invoices/{invoiceId}/pdf
  → Backend: InvoicePdfService.render()
    → Generates professional Arabic RTL PDF with:
      → Restaurant name, invoice number, table/session info
      → Itemized order lines, charges breakdown, grand total
    → PDF is generated on-demand (not stored on disk)
  → Browser downloads PDF
```

---

## 5. Realtime System (SSE + Redis)

### 5.1 Event Types

```java
public enum SseEventType {
    ORDER_CREATED,          // New customer order submitted
    ORDER_SENT_TO_KITCHEN,  // Staff forwarded order to kitchen
    ORDER_DONE,             // Kitchen marked order complete
    ORDER_CANCELLED,        // Staff cancelled order
    SERVICE_CALL,           // Customer requested waiter/bill/nara
    MENU_ITEM_UPDATED,      // Admin changed a menu item
    MENU_ITEM_DELETED,      // Admin deleted a menu item
    HEARTBEAT,              // Keep-alive signal
    CONNECTED               // Initial connection confirmation
}
```

### 5.2 Event Envelope

Every SSE event is wrapped in an `SseEvent<T>` record:

```java
public record SseEvent<T>(
    String eventId,           // UUID, used as SSE id field
    SseEventType type,
    Long restaurantId,
    Instant timestamp,
    int payloadVersion,       // Schema version (currently 1)
    String sourceInstanceId,  // Which backend instance produced the event
    T payload                 // Typed payload (OrderCreatedPayload, etc.)
)
```

### 5.3 Event Publishing Pipeline

```
Service method (e.g., OrderService.submitOrder)
  │  Inside @Transactional
  │
  ├── DB mutations (order, items, etc.)
  ├── eventPublisher.publishEvent(SseOutboundEvent.of(...))
  │     └── Spring ApplicationEventPublisher (in-memory)
  │
  └── Transaction commits
        │
        ▼
SseAfterCommitListener (@TransactionalEventListener, phase = AFTER_COMMIT)
  │  Only fires AFTER successful commit — no phantom events on rollback
  │
  └── eventBridge.publish(event)
        │
        ├── [Redis mode] RedisEventBridge
        │     └── Serialize → redisTemplate.convertAndSend("sse:restaurant-events", json)
        │           │
        │           ▼
        │     RedisEventSubscriber.onMessage() [on ALL instances]
        │           └── Deserialize → dispatchToLocalRegistry()
        │                 └── RestaurantEventPublisher.publishXxx()
        │                       └── SseEmitterRegistry.broadcast(restaurantId, event)
        │                             └── Send to each SseEmitter via emitter.send()
        │
        └── [Local mode] LocalEventBridge
              └── RestaurantEventPublisher.publishXxx()
                    └── SseEmitterRegistry.broadcast(restaurantId, event)
```

### 5.4 SSE Connection Management

- **Timeout:** 30 minutes (`SSE_TIMEOUT_MS`). After timeout, client reconnects automatically (browser `EventSource` behavior).
- **Heartbeats:** Staff SSE every 15 seconds, public menu SSE every 20 seconds. Heartbeats detect stale connections.
- **Connection limit:** Configurable per restaurant (default 500, via `app.sse.max-connections-per-restaurant`). Connections beyond the limit receive an error event and are completed.
- **Cleanup:** On completion, timeout, or error, emitters are removed from the registry. Empty restaurant lists are purged.
- **Metrics:** Active SSE connection count is exposed as a Micrometer gauge (`sse.staff.connections.active`, `sse.public.connections.active`).

### 5.5 Frontend SSE Integration

Staff screens (`screen6.html`, `screen6-calls.html`, `screen7.html`) use a **hybrid SSE + polling** strategy:

1. **Primary:** SSE `EventSource` connection for real-time push
2. **Fallback:** REST polling (every 2–3 seconds with `since` timestamp) if SSE disconnects
3. **Resync:** On SSE reconnect, a full data reload is triggered to catch any events missed during disconnection

Customer menu screen (`screen1.html`) uses the public menu SSE for live menu updates and applies upsert/delete operations directly into the in-memory `menuData`.

---

## 6. Security Model

### 6.1 Authentication

- **Mechanism:** JWT (JSON Web Tokens) using HMAC-SHA signing (jjwt library)
- **Login endpoint:** `POST /api/v1/auth/login` — accepts `{ username, password }`, returns `{ token, role, restaurantId }`
- **Password storage:** BCrypt hashed
- **Token claims:**
  - `sub` — userId
  - `role` — user's role (STAFF, MANAGER, KITCHEN, OWNER, ADMIN)
  - `restaurantId` — scoping claim (null for ADMIN users who are system-wide)
  - `iss` — `restaurant-qr`
  - `exp` — 15 days (21600 minutes)
- **Token transport:** `Authorization: Bearer <token>` header. For SSE endpoints (which use `EventSource`), the token is passed as a query parameter and extracted by `JwtAuthFilter`.

### 6.2 Roles and Permissions

| Role | Scope | Access |
|---|---|---|
| `ADMIN` | System-wide | Create/manage restaurants, menu, tables, users. Full CRUD via `/api/v1/admin/**` |
| `OWNER` | Per-restaurant | Analytics dashboard, financial rules CRUD, invoice viewing. `/api/v1/owner/**` |
| `MANAGER` | Per-restaurant | All staff operations + table sessions + invoices. `/api/v1/manager/**`, `/api/v1/staff/**`, `/api/v1/kitchen/**` |
| `STAFF` | Per-restaurant | View/manage orders, handle service calls. `/api/v1/staff/**`, `/api/v1/manager/**` (orders only) |
| `KITCHEN` | Per-restaurant | View SENT_TO_KITCHEN orders, mark DONE. `/api/v1/kitchen/**` |
| (anonymous) | Per-table | Public endpoints: menu, orders, service calls, bills. `/api/v1/public/**` |

### 6.3 Endpoint Protection

**Spring Security filter chain** (`SecurityConfig`):

```
/api/v1/auth/**                    → permitAll
/api/v1/public/**                  → permitAll
/actuator/health/**                → permitAll
/swagger-ui/**, /v3/api-docs/**    → permitAll
/api/v1/admin/**                   → ROLE_ADMIN
/api/v1/owner/**                   → ROLE_OWNER
/api/v1/manager/invoices/**        → ROLE_MANAGER or ROLE_OWNER
/api/v1/manager/**                 → ROLE_MANAGER
/api/v1/staff/**                   → ROLE_STAFF or ROLE_MANAGER
/api/v1/kitchen/**                 → ROLE_KITCHEN or ROLE_MANAGER
/api/v1/sse/**                     → ROLE_MANAGER or ROLE_STAFF or ROLE_KITCHEN
anyRequest                         → authenticated
```

**Restaurant isolation:** Every authenticated request extracts `restaurantId` from the JWT `AuthPrincipal`. All service-layer queries filter by this `restaurantId`, ensuring users never access data from other restaurants.

### 6.4 Public Endpoint Security

Public endpoints (customer-facing) are secured by:
- **QR token** — a UUID-based token unique to each table. Without it, the endpoint cannot resolve the restaurant/table.
- **Session code** — a 4-digit code valid only while the session is open. Must match the one stored on the table.
- **Rate limiting** — Nginx rate limits (`30r/s` general API, `2r/s` order submission) + backend rate limits (1 order/min/table, 3-min service call cooldown).

### 6.5 Additional Security

- **CORS:** Restricted to specific origins (localhost variants + production domain `syrianorder.com`)
- **CSRF:** Disabled (stateless JWT architecture)
- **Session management:** Stateless (`SessionCreationPolicy.STATELESS`)
- **Security headers:** `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `X-XSS-Protection: 1; mode=block` (via Nginx)
- **Admin seeder:** An ADMIN user is created on first boot with a configurable password (`ADMIN_PASSWORD` env var)

---

## 7. Financial Model

### 7.1 Financial Rules

- **Entity:** `RestaurantFinancialRule` (table: `restaurant_financial_rules`)
- **Per-restaurant:** Each restaurant configures its own set of tax/fee rules.
- **Rule types:**
  - `FIXED` — a flat monetary amount added to every bill (e.g., energy fee = 500 SYP)
  - `PERCENTAGE` — a percentage of the base amount (e.g., VAT = 10%)
- **Compound flag:** If `isCompound = true`, a PERCENTAGE rule is applied on `subtotal + all prior charges` (compound). If `false`, it is applied only on the items subtotal (simple).
- **Apply order:** Rules are applied in sequence defined by `applyOrder` (lower value first).
- **Soft delete:** Rules can be deactivated (`isActive = false`) without deletion. Deactivated rules stop applying to new sessions but past session bills retain their snapshots.
- **Limit:** Max 20 active rules per restaurant (application-enforced).
- **Management:** OWNER role via `owner-financial-settings.html` and `/api/v1/owner/financial-rules` endpoints.

### 7.2 Session Bills

- **Entity:** `SessionBill` (table: `session_bills`)
- **One-to-one with TableSession.**
- **Fields:** `itemsSubtotal`, `chargesTotal`, `grandTotal`, `currency` (default "SYP"), `status` (DRAFT/FINALIZED), `finalizedAt`
- **Bill charges:** `SessionBillCharge` (table: `session_bill_charges`) — one row per applied financial rule, containing a snapshot of the rule's name, type, value, and computed amount.

**Bill lifecycle:**

| State | When | Behavior |
|---|---|---|
| **No bill** | Session has zero orders | Bill endpoint returns computed zero totals |
| **DRAFT** | Session open, customer/staff requests bill | Computed live: sum non-cancelled order totals + apply current active rules. **Not persisted.** |
| **FINALIZED** | Session closed | Persisted snapshot. Immutable. Future rule changes do not affect it. |

**Calculation pipeline:**

```
1. itemsSubtotal = SUM(totalAmount) of all non-cancelled orders in session
2. For each active rule (ordered by apply_order):
   a. FIXED rule: chargeAmount = rule.value
   b. PERCENTAGE rule (simple):  chargeAmount = itemsSubtotal × rule.value / 100
   c. PERCENTAGE rule (compound): chargeAmount = (itemsSubtotal + runningCharges) × rule.value / 100
   d. runningCharges += chargeAmount
3. chargesTotal = SUM(all chargeAmounts)
4. grandTotal = itemsSubtotal + chargesTotal
```

### 7.3 Invoices

- **Entity:** `SessionInvoice` (table: `session_invoices`)
- **One-to-one with TableSession and SessionBill.**
- **Generated on session close** — an immutable snapshot of all session data.
- **Invoice number format:** `INV-YYYYMMDD-SEQ` (daily sequence per restaurant, e.g., `INV-20260330-003`)
- **Snapshot fields:** `tableNumber`, `sessionCode`, `sessionOpenedAt`, `sessionClosedAt`, `itemsSubtotal`, `chargesTotal`, `grandTotal`, `currency`
- **Invoice items:** `SessionInvoiceItem` — order items aggregated by `(itemName, unitPrice)` with summed quantities.
- **PDF generation:** On-demand via `GET /api/v1/manager/invoices/{id}/pdf`. Uses OpenPDF with embedded Amiri Arabic font. RTL layout. Professional invoice format with restaurant header, itemized lines, charges breakdown, and grand total. **PDFs are not stored — they are generated from the snapshot data on each request.**

---

## 8. Scaling Strategy

### 8.1 Why Redis Is Used

In a single-instance deployment, SSE events are published directly to the local `SseEmitterRegistry`. This breaks when multiple backend instances run behind a load balancer because:

- A client connected to **instance A** has its emitter registered only on A
- A write handled by **instance B** publishes the event only to B's local registry
- The client on A **never receives the event**

Sticky sessions alone do NOT solve this — the event producer is distributed, not just the consumer.

### 8.2 How Multi-Instance Works

Redis pub/sub acts as a **broadcast backplane**:

1. Any instance that commits a transaction publishes the event to Redis channel `sse:restaurant-events`
2. **All instances** (including the publisher) subscribe to this channel via `RedisEventSubscriber`
3. Each instance receives the event and broadcasts it to its locally-registered SSE emitters
4. Every connected client receives the event regardless of which instance handled the original write

This is a **uniform fan-out** model — every instance receives every event. Each instance filters internally (only broadcasting to emitters for the relevant `restaurantId`).

### 8.3 Conditional Activation

- `app.redis.enabled=true` → `RedisEventBridge` and `RedisSseConfig` are activated (`@ConditionalOnProperty`)
- `app.redis.enabled=false` (or absent) → `LocalEventBridge` is used as fallback (`@ConditionalOnMissingBean`)
- This makes Redis **optional** — the system works correctly in single-instance mode without Redis.

### 8.4 Database Concurrency

Horizontal scaling introduces database contention. The system handles this with:

- **Pessimistic row-level locks** (`SELECT ... FOR UPDATE`) on table rows during order submission and session open/close
- **Pessimistic lock on order counter** for atomic order number generation
- **Idempotent operations** — opening an already-open session returns the existing code; closing an already-closed session succeeds silently
- **HikariCP connection pool** — 20 max connections per instance, with leak detection (60s threshold)
- **`spring.jpa.open-in-view: false`** — prevents DB connections from being held during long-lived SSE streams

---

## 9. Observability

### 9.1 Logging

**Structured JSON logging** in production (Logstash encoder). Human-readable text in development.

**MDC correlation fields** (set by `RequestCorrelationFilter`):

| MDC Key | Source | Purpose |
|---|---|---|
| `requestId` | `X-Request-Id` header (or generated UUID) | Trace a request across log lines |
| `instanceId` | `app.instance-id` config / `INSTANCE_ID` env var / hostname | Identify which backend instance processed the request |

**Log pattern (dev):**
```
2026-03-30T14:22:33.456+03:00 INFO [http-nio-8080-exec-1] [rid=abc-123] [iid=backend-1] c.r.order.OrderService - Order created | orderId=42 | ...
```

**Log pattern (prod):** Structured JSON with the same fields, consumable by ELK/Loki/CloudWatch.

### 9.2 Metrics (Micrometer + Prometheus)

Actuator endpoint: `GET /actuator/prometheus` (exposed in production config).

**Custom metrics:**

| Metric | Type | Description |
|---|---|---|
| `sse.staff.connections.active` | Gauge | Active staff SSE connections on this instance |
| `sse.public.connections.active` | Gauge | Active public menu SSE connections on this instance |
| `sse.redis.publish{result=success\|failure}` | Counter | Redis pub/sub publish count |
| `sse.redis.consume{result=success\|failure}` | Counter | Redis pub/sub consume count |

**Standard metrics** (via Actuator + Micrometer): JVM memory, GC, HikariCP pool stats, HTTP request timings, Tomcat thread pool.

### 9.3 Health Endpoints

| Endpoint | Purpose |
|---|---|
| `GET /actuator/health/liveness` | Kubernetes-style liveness probe. Used by Docker `HEALTHCHECK`. |
| `GET /actuator/health/readiness` | Readiness probe. Includes `readinessState` + `redis` health checks. |
| `GET /actuator/health` | Aggregated health (details shown when authorized). |

**Docker health checks:**
- Backend: `curl -f http://localhost:8080/actuator/health/liveness` every 30s
- MySQL: `mysqladmin ping` every 10s
- Redis: `redis-cli ping` every 10s

---

## 10. Key Design Decisions

### 10.1 SSE Instead of WebSocket

**Why SSE was chosen:**
- **Simpler protocol** — works over standard HTTP/1.1; no upgrade handshake.
- **Auto-reconnect built into browsers** — `EventSource` automatically reconnects with exponential backoff.
- **Unidirectional is sufficient** — the system only needs server→client push. Client→server communication uses REST.
- **Nginx-friendly** — SSE requires only `proxy_buffering off` and long timeouts; no WebSocket upgrade config.
- **Event ID support** — `Last-Event-ID` header on reconnect enables future replay capability.

**Tradeoff:** Cannot receive client messages over the same connection (must use separate REST calls for writes).

### 10.2 Redis Pub/Sub (Not Streams)

**Why pub/sub:**
- **Fire-and-forget is sufficient** — SSE events are transient UI updates. If no one is listening, the event can be dropped.
- **Zero configuration** — no consumer groups, no ACKs, no stream trimming.
- **Minimal latency** — sub-millisecond fan-out to all subscribers.
- **Simple operational model** — Redis is a single process with minimal resource requirements (128MB).

**Tradeoff:** No durable replay. If an instance is temporarily down or a client disconnects, events during that window are lost. Frontend handles this with polling-based resync on reconnect.

### 10.3 Snapshot-Based Invoices

**Why invoices snapshot all data:**
- **Immutability** — once a bill is finalized and an invoice is generated, the data must never change, even if financial rules, menu prices, or restaurant details change later.
- **Auditability** — the invoice is a legal/financial document that must reflect exactly what was billed at the time of the session.
- **Decoupling** — the invoice does not depend on the current state of financial rules or menu items. It is self-contained.

**What is snapshotted:** table number, session code, session timestamps, items subtotal, all charge breakdowns (rule names, types, values, computed amounts), grand total, currency, and aggregated order line items.

### 10.4 No PDF Storage

**Why PDFs are generated on-demand:**
- **Storage cost** — storing binary PDFs for every session would grow quickly.
- **Freshness** — if the PDF template or formatting is improved, all future downloads benefit immediately.
- **Simplicity** — no file storage layer, no cleanup jobs, no backup concerns for binary blobs.
- **All data is already persisted** — the `SessionInvoice` + `SessionBillCharge` tables contain everything needed to regenerate the PDF identically.

**Tradeoff:** CPU cost on each download. In practice, invoice PDFs are small (~1 page) and generation is fast.

### 10.5 After-Commit Event Publishing

**Why events are published after transaction commit:**
- **Prevents phantom events** — if a transaction rolls back, no SSE event is sent. Without this, clients could receive events for orders that never actually persisted.
- **Implementation:** `SseOutboundEvent` is published via Spring's `ApplicationEventPublisher` inside the `@Transactional` method. `SseAfterCommitListener` uses `@TransactionalEventListener(phase = AFTER_COMMIT)` to guarantee the event only fires after successful commit.

---

## 11. Known Constraints / Tradeoffs

### 11.1 No Durable Event Replay

- There is no server-side event store or replay mechanism.
- If a client disconnects and reconnects, it may miss events that occurred during the disconnection window.
- **Mitigation:** Frontend screens use hybrid SSE + REST polling. On reconnect, they perform a full data reload to resync state.
- **Future:** A Redis Streams or database-backed event log could be added for replay based on `Last-Event-ID`.

### 11.2 Frontend Resync Reliance

- The frontend is responsible for detecting SSE disconnections and triggering a full REST resync.
- This adds complexity to each frontend screen and means there is a brief window (seconds) where the UI may be stale after reconnection.

### 11.3 No WebSocket / Bidirectional Channel

- Customer actions (orders, service calls) always go through REST endpoints.
- There is no server-push channel to the customer for order status updates — `screen3.html` relies on REST polling.

### 11.4 Single-Database Architecture

- All restaurants share a single MySQL instance.
- High write volume from many restaurants could eventually bottleneck.
- **Mitigation:** Connection pooling (20 per instance), pessimistic locks with short hold times, query-level indexes.

### 11.5 Session Code Collision

- 4-digit codes (0000–9999) offer 10,000 combinations per restaurant.
- Collision is possible if many tables are open simultaneously. The system retries up to 30 times.
- With a `UNIQUE(restaurant_id, session_code)` constraint, collisions are safely detected.

### 11.6 Rate Limiting Granularity

- Backend rate limits are per-table (1 order/minute, 3-minute service call cooldown).
- Nginx rate limits are per-IP. A single aggressive client could still send many requests to different endpoints.

### 11.7 No Real-Time Customer Push for Order Status

- Customers see their order status on `screen3.html` via REST polling, not SSE.
- The public menu SSE stream only covers menu updates, not order status changes.

### 11.8 Timezone Assumption

- The system defaults to `Asia/Damascus` timezone throughout (database, PDF, frontend localization).
- No per-restaurant timezone configuration exists.

---

## 12. Future Improvements

### 12.1 Technical Improvements

- **Event replay via Redis Streams** — store recent events (e.g., last 5 minutes) in a Redis Stream. On SSE reconnect, replay events since `Last-Event-ID` to eliminate the full-resync requirement.
- **Customer SSE for order status** — extend the public SSE stream to include `ORDER_SENT_TO_KITCHEN`, `ORDER_DONE`, `ORDER_CANCELLED` events scoped to the customer's session, eliminating polling on `screen3.html`.
- **Database read replicas** — separate read traffic (menu queries, dashboard analytics) from write traffic (orders, session management).
- **Per-restaurant timezone configuration** — allow each restaurant to set its timezone for invoice formatting and dashboard analytics.
- **WebSocket upgrade** — if bidirectional communication is ever needed (e.g., live chat between staff and kitchen), consider upgrading to WebSocket for that specific use case while keeping SSE for broadcast.

### 12.2 Scaling Improvements

- **Auto-scaling backend instances** — use container orchestration (Docker Swarm, Kubernetes) to scale backend instances based on SSE connection count or CPU usage.
- **Redis Sentinel / Cluster** — for Redis high availability in production.
- **Database sharding** — if multi-restaurant load exceeds single-database capacity, shard by `restaurant_id`.
- **CDN for static assets** — serve frontend HTML/CSS/JS and uploaded images via a CDN to reduce Nginx load.

### 12.3 UX Improvements

- **Push notifications for customers** — notify customers when their order is ready (via FCM or Web Push) so they don't need to keep the screen open.
- **Multi-language support** — menu items and UI already support Arabic/English names. Full i18n of all UI strings could be added.
- **Order history for customers** — allow customers to view past session orders after the session is closed (within a time window).
- **QR code generation UI** — allow ADMIN/MANAGER to generate and print QR code stickers directly from the admin panel.
- **Table-level notifications** — visual/audio alerts on staff screens for specific table events.

### 12.4 Business Features

- **Payment integration** — integrate with payment gateways for in-app payment at session close.
- **Loyalty/rewards system** — track customer visits and offer discounts based on spending.
- **Inventory management** — link menu item availability to ingredient stock levels.
- **Multi-branch support** — allow restaurant chains to manage multiple branches under one owner account.
- **Reservation system** — allow customers to reserve tables in advance.

---

## Appendix: API Endpoint Reference

### Public Endpoints (No JWT)

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/auth/login` | Login → JWT |
| `POST` | `/api/v1/public/tables/{qrToken}/session/verify` | Verify session code |
| `GET` | `/api/v1/public/tables/{qrToken}/menu?code=` | Get menu |
| `POST` | `/api/v1/public/tables/{qrToken}/orders?code=` | Submit order |
| `GET` | `/api/v1/public/tables/{qrToken}/orders?code=` | Get session orders |
| `POST` | `/api/v1/public/tables/{qrToken}/service-calls?code=` | Create service call |
| `GET` | `/api/v1/public/tables/{qrToken}/service-calls/active?code=` | Get active calls |
| `GET` | `/api/v1/public/tables/{qrToken}/service-calls/cooldown?code=` | Cooldown status |
| `GET` | `/api/v1/public/tables/{qrToken}/bill?code=` | Get session bill |
| `GET` | `/api/v1/public/sse/menu?token=&code=` | Public menu SSE stream |

### Staff Endpoints (JWT: MANAGER/STAFF)

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/v1/manager/orders/new` | List NEW orders (paginated) |
| `GET` | `/api/v1/manager/orders/{id}` | Order details |
| `PATCH` | `/api/v1/manager/orders/{id}/sent-to-kitchen` | Send to kitchen |
| `PATCH` | `/api/v1/manager/orders/{id}/cancelled` | Cancel order |
| `GET` | `/api/v1/manager/tables` | List tables with session info |
| `POST` | `/api/v1/manager/tables/{id}/session/open` | Open session |
| `POST` | `/api/v1/manager/tables/{id}/session/close` | Close session |
| `GET` | `/api/v1/staff/tables/{id}/bill` | Staff view of session bill |
| `GET` | `/api/v1/staff/service-calls` | Get active service calls |

### Kitchen Endpoints (JWT: KITCHEN/MANAGER)

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/v1/kitchen/orders/sent-to-kitchen` | List SENT_TO_KITCHEN orders |
| `GET` | `/api/v1/kitchen/orders/{id}` | Order details |
| `PATCH` | `/api/v1/kitchen/orders/{id}/done` | Mark order DONE |

### Owner Endpoints (JWT: OWNER)

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/v1/owner/dashboard` | Dashboard analytics |
| `GET` | `/api/v1/owner/financial-rules` | List financial rules |
| `POST` | `/api/v1/owner/financial-rules` | Create rule |
| `PUT` | `/api/v1/owner/financial-rules/{id}` | Update rule |
| `DELETE` | `/api/v1/owner/financial-rules/{id}` | Delete (soft) rule |
| `PUT` | `/api/v1/owner/financial-rules/reorder` | Reorder rules |

### Invoice Endpoints (JWT: MANAGER/OWNER)

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/v1/manager/invoices?date=&invoiceNumber=&zone=` | List/search invoices |
| `GET` | `/api/v1/manager/invoices/{id}/pdf` | Download invoice PDF |

### Admin Endpoints (JWT: ADMIN)

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/admin/restaurants/setup` | Create restaurant (full setup) |
| `POST` | `/api/v1/admin/restaurants/setup-with-images` | Create restaurant with images |
| `GET` | `/api/v1/admin/restaurants` | List all restaurants |
| `GET` | `/api/v1/admin/restaurants/{id}` | Restaurant details |
| `PUT` | `/api/v1/admin/restaurants/{id}` | Update restaurant |
| `DELETE` | `/api/v1/admin/restaurants/{id}` | Delete restaurant |

### SSE Endpoints

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/v1/sse/events` | JWT (MANAGER/STAFF/KITCHEN) | Staff SSE stream |
| `GET` | `/api/v1/public/sse/menu?token=&code=` | QR token + session code | Public menu SSE stream |

---

## Appendix: Database Schema (Flyway Migrations)

Migrations are in `migrations/` (V1 through V19):

| Migration | Purpose |
|---|---|
| `V1__init.sql` | Core tables: restaurants, users, menu_categories, menu_items, customer_orders, order_items, restaurant_tables |
| `V2__add_order_status.sql` | Order status column |
| `V3__add_menu_item_image_url.sql` | Image URL on menu items |
| `V4__add-column_done_to_customer_orders.sql` | done_at timestamp |
| `V5__add-column_cancelled_to_customer_orders.sql` | cancelled_at timestamp |
| `V6__add-roles.sql` | Role column on users |
| `V7__push_tokens.sql` | staff_push_tokens table (FCM) |
| `V8__session_table.sql` | session_code + session_opened_at on restaurant_tables |
| `V9__add_unique_session_code_per_restaurant.sql` | UNIQUE(restaurant_id, session_code) |
| `V10__add_indexes.sql` | Performance indexes |
| `V11__add_owner_role.sql` | OWNER value in role enum |
| `V12__table_session_history.sql` | table_sessions table |
| `V13__add_admin_role.sql` | ADMIN value in role enum |
| `V14__add_restaurant_order_number.sql` | restaurant_order_number + counter table |
| `V15__add_performance_indexes.sql` | Additional indexes |
| `V16__restaurant_financial_rules.sql` | restaurant_financial_rules table |
| `V17__session_bills.sql` | session_bills + session_bill_charges tables |
| `V18__customer_orders_session_fk.sql` | table_session_id FK on customer_orders |
| `V19__session_invoices.sql` | session_invoices + session_invoice_items tables |


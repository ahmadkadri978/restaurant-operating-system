#  Restaurant QR Ordering & Service Call System – Backend API

A backend system that enables restaurants to handle **QR-based ordering** and **service calls** efficiently, with a clear separation between **customer-facing APIs** and **staff dashboards**.

---

##  Key Features

### Customer (Public APIs)
- Scan table QR code
- View restaurant menu
- Client-side cart management
- Submit orders (1 order per table per minute)
- Send service calls (waiter / bill)

### Staff (Authenticated APIs)
- JWT-based authentication (STAFF / MANAGER)
- View recent orders (paging + polling)
- Mark orders as **Sent to Kitchen**
- View active service calls (polling, last 3 minutes)
- Near real-time updates using polling 

---

##  Design Decisions

- **Cart is client-side only**  
  Backend stores orders only after submission.

- **Polling **  
  Lightweight, simple, and sufficient for restaurant environments.

- **Minimal order workflow**
    - `NEW`
    - `SENT_TO_KITCHEN`

- **Multi-tenant ready**
    - Each staff user belongs to one restaurant
    - Restaurant isolation enforced via JWT claims

---

##  Tech Stack

- Java + Spring Boot
- Spring Security (JWT)
- JPA / Hibernate
- MySQL (production-ready schema)
- Flyway (database migrations)
- Swagger / OpenAPI (API documentation)

---

##  Authentication

### Login

Returns a JWT token containing:
- `userId`
- `role`
- `restaurantId`

All staff endpoints require:

---

##  API Structure

### Public APIs (No Authentication)

- GET /api/v1/public/tables/{qrToken}/menu
- POST /api/v1/public/tables/{qrToken}/orders
- POST /api/v1/public/tables/{qrToken}/service-calls

### Staff APIs (JWT Required)
- GET /api/v1/staff/orders
- GET /api/v1/staff/orders/{id}
- PATCH /api/v1/staff/orders/{orderId}/sent-to-kitchen
- GET /api/v1/staff/service-calls

---

##  Polling (Near Real-Time Updates)

Both **orders** and **service calls** support polling via an optional `since` query parameter.

Example:

### Recommended Intervals
- Orders: every **2–3 seconds**
- Service Calls: every **2 seconds**

Only new data is returned, keeping requests lightweight.

---
## API Documentation

/swagger-ui.html


##  Error Handling

All errors follow a unified structure:

```json
{
  "timestamp": "2026-01-07T21:15:30.123Z",
  "status": 429,
  "error": "Too Many Requests",
  "code": "RATE_LIMIT_EXCEEDED",
  "message": "Only one order per minute is allowed",
  "path": "/api/v1/public/tables/DEMO/orders"
}


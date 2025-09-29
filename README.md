# Help Unker – Request Board Backend (Spring Boot + REST + SSE)

A lightweight backend for a mobile-first request board where **elderly users post requests for help** and **volunteers accept & complete** them. Built around **HTTP APIs for commands/queries** and **SSE for real-time updates**, with an **internal Pub/Sub** backbone for decoupled processing (notifications, matching, analytics).

---

## Table of contents

* [Architecture overview](#architecture-overview)
* [API contracts](#api-contracts)

  * [Requests](#requests)
  * [Assignments](#assignments)
  * [Query parameters & pagination](#query-parameters--pagination)
  * [Errors](#errors)
* [Real-time updates (SSE)](#real-time-updates-sse)
* [Auth & roles](#auth--roles)
* [Data model](#data-model)
* [Internal events (Pub/Sub + Outbox)](#internal-events-pubsub--outbox)
* [Notifications](#notifications)
* [Moderation & safety](#moderation--safety)
* [Persistence & infra](#persistence--infra)
* [Local setup](#local-setup)
* [Non-functional concerns](#non-functional-concerns)
* [Roadmap](#roadmap)

---

## Architecture overview

**Mobile app → REST API (HTTPS)**

* Elderly create requests; volunteers browse and act via HTTP.
* **Realtime**: clients subscribe to **SSE** streams for board/request updates (WebSocket optional if you later need bidirectional features).
* **Internal Pub/Sub**: domain events emitted on persistence flow to decoupled consumers for notifications, matching/scoring, and analytics.
* **Storage**: PostgreSQL (optionally PostGIS), Redis for rate limiting/presence/caching.

```
Mobile App ──HTTP──▶ Spring Boot API ──JPA──▶ Postgres
          ◀─SSE─────┘        │
                             ├─ Outbox → Broker (RabbitMQ/Kafka)
                             │       ├─ Notifier (FCM/APNs)
                             │       ├─ Matcher/Scorer
                             │       └─ Analytics/Audit
                             └─ Redis (rate limits, presence, cache)
```

---

## API contracts

### Requests

**Create a request (Elderly)**

```
POST /requests
Content-Type: application/json
Authorization: Bearer <jwt>
```

**Body**

```json
{
  "title": "Buy groceries",
  "details": "2L milk, bread, eggs",
  "category": "GROCERIES",
  "locationLat": 1.3521,
  "locationLng": 103.8198,
  "address": "Blk 123, #05-67"
}
```

**201 Response**

```json
{
  "id": "uuid",
  "title": "Buy groceries",
  "details": "2L milk, bread, eggs",
  "category": "GROCERIES",
  "status": "OPEN",
  "locationLat": 1.3521,
  "locationLng": 103.8198,
  "address": "Blk 123, #05-67",
  "elderlyId": "uuid",
  "createdAt": "2025-09-29T12:34:56Z"
}
```

**List/browse requests (Volunteer)**

```
GET /requests?status=OPEN&near=1.3521,103.8198&radiusKm=3&sort=createdAt,DESC&page=0&size=20
Authorization: Bearer <jwt>
```

**200 Response**

```json
{
  "content": [ { "id": "uuid", "title": "...", "status": "OPEN", "locationLat": 1.35, "locationLng": 103.82, "createdAt": "..." } ],
  "page": 0, "size": 20, "totalElements": 42, "totalPages": 3
}
```

**Get one request**

```
GET /requests/{id}
Authorization: Bearer <jwt>
```

**Update/cancel (owner elderly or admin)**

```
POST /requests/{id}/cancel
Authorization: Bearer <jwt>
```

### Assignments

**Accept a request (Volunteer)**

```
POST /requests/{id}/accept
Authorization: Bearer <jwt>
```

* First-come-first-served with optimistic locking; fails if not `OPEN`.

**Mark arrived / complete (Volunteer)**

```
POST /requests/{id}/arrived
POST /requests/{id}/complete
Authorization: Bearer <jwt>
```

**Assignment resource (read-only)**

```
GET /assignments/{id}
Authorization: Bearer <jwt>
```

### Query parameters & pagination

* `status`: `OPEN|ASSIGNED|ONGOING|COMPLETED|CANCELLED`
* `near=lat,lng` + `radiusKm=`: geo filter (simple Haversine; PostGIS later)
* `sort=field,DESC|ASC`
* `page`, `size` (Spring pagination defaults)

### Errors

* JSON Problem Details style:

```json
{
  "type": "https://errors.help-unker.com/request-not-open",
  "title": "Request is not open",
  "status": 409,
  "detail": "This request has already been assigned.",
  "instance": "/requests/..."
}
```

---

## Real-time updates (SSE)

Prefer **SSE** (server→client only). Great for the live board and per-request status.

**Board stream (all OPEN changes / newly created / assigned)**

```
GET /stream/board
Accept: text/event-stream
```

**Per-request stream**

```
GET /stream/requests/{id}
Accept: text/event-stream
```

**Event payload examples**

```json
{ "type": "RequestCreated", "request": { "id": "uuid", "title": "..." } }
{ "type": "RequestAccepted", "requestId": "uuid", "volunteerId": "uuid" }
{ "type": "RequestCompleted", "requestId": "uuid" }
```

> If you later need client→server messages (presence pings, typing, chat), you can add **WebSocket (STOMP)** without changing the HTTP command/query model.

---

## Auth & roles

* **Spring Security + JWT**.
* **Roles**: `ELDERLY`, `VOLUNTEER`, `ADMIN`.
* **Elderly**: phone OTP → exchange for JWT (short lived + refresh).
* **Volunteer**: email/password or SSO; must have `volunteerVerified=true` for accepting.
* **Endpoint guards**:

  * `POST /requests`: `ELDERLY|ADMIN`
  * `POST /requests/{id}/accept|arrived|complete`: `VOLUNTEER|ADMIN`
  * `GET /requests`: `VOLUNTEER|ADMIN` (or open read if you want a public board)
  * `POST /requests/{id}/cancel`: owner elderly or `ADMIN`

---

## Data model

**Core tables**

* `users(id, display_name, role, phone, email, volunteer_verified, password_hash, created_at, updated_at, version)`
* `requests(id, elderly_id, title, details, category, status, location_lat, location_lng, address, created_at, updated_at, version)`
* `request_photos(id, request_id, url, content_type, created_at)`
* `assignments(id, request_id, volunteer_id, accepted_at, arrived_at, completed_at, cancelled_at, created_at, version)`

**Supporting**

* `device_tokens(id, user_id, provider, token, created_at, last_seen_at)`
* `outbox_events(id, aggregate_type, aggregate_id, event_type, payload, created_at, processed_at)`

Enums:

* `user_role = (ELDERLY, VOLUNTEER, ADMIN)`
* `request_status = (OPEN, ASSIGNED, ONGOING, COMPLETED, CANCELLED)`

> Start with `location_lat/lng` (NUMERIC(9,6)). Upgrade to **PostGIS** `GEOGRAPHY(POINT,4326)` + GIST index when you need fast radius queries.

---

## Internal events (Pub/Sub + Outbox)

* On each write, publish a domain event (e.g., `RequestCreated`, `RequestAccepted`) to **`outbox_events`** within the same DB transaction.
* A background **outbox poller** reads rows and forwards them to your broker (**RabbitMQ** or **Kafka**), then marks `processed_at`.
* **Consumers**:

  * **Notifier** → FCM/APNs push
  * **Matching/Scoring** → identify nearby on-duty volunteers
  * **Analytics/Audit** → warehouse / BI
* SSE streams consume from an in-memory **Flux/Sink** fed by outbox consumption.

**Event shape**

```json
{
  "aggregate_type": "Request",
  "aggregate_id": "uuid",
  "event_type": "RequestAccepted",
  "payload": { "requestId": "uuid", "volunteerId": "uuid", "at": "2025-09-29T12:34:56Z" }
}
```

---

## Notifications

* **Push** via **FCM (Android)** / **APNs (iOS)**:

  * Store `device_tokens` per user; refresh on app open.
  * Use deep links: `helpunker://requests/{id}`.
* Use push when the app is backgrounded; when foregrounded, SSE covers UI updates.

---

## Moderation & safety

* Simple rules engine on create/update:

  * **Text/image flagging** (profanity, PII, unsafe content).
  * **Duplicate detection** (similarity on `title+details`).
  * **Escalation** route to `ADMIN` with audit trail.
* **SLA**: After `accept`, volunteer must **confirm arrival** within X minutes or the system auto-reopens the request.

---

## Persistence & infra

* **PostgreSQL 14+** (Liquibase changelog in SQL):
  `classpath:db/changelog/db.changelog-master.sql`
* **Redis**:

  * Rate limiting (per-IP/per-user)
  * Presence cache (`VOLUNTEER online/offline`)
  * Hot board cache (`OPEN` within common radii)
* **Optional**: **PostGIS** for geo performance at scale.

---

## Local setup

**Prerequisites**

* JDK 21, Maven
* Docker & Docker Compose (for the provided `infra/` setup)
* Spring Boot 3.5.x (managed by the project `pom.xml`)

**Option A – Docker Compose (recommended)**

The `infra/` directory mirrors our other services. It contains a Dockerfile for the API, a dedicated test image, and a `docker-compose.yml` that wires the application together with PostgreSQL.

```bash
cd infra
docker compose up --build
```

* `app` runs the packaged Spring Boot application on port `8080`.
* `db` provides PostgreSQL 16 with data persisted to a named Docker volume.
* `tests` (disabled by default) can be launched with `docker compose --profile tests up tests` to execute the Maven test suite inside a container.

**Option B – Manual Postgres & local tooling**

```bash
docker run --name help-unker-pg -e POSTGRES_PASSWORD=postgres -p 5432:5432 -d postgres:16
```

**Application config (example)**

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/postgres
    username: postgres
    password: postgres
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
  liquibase:
    change-log: classpath:db/changelog/db.changelog-master.sql

app:
  sse:
    board-path: /stream/board
    request-path: /stream/requests/{id}
security:
  jwt:
    issuer: help-unker
```

**Run**

*With Compose running:* `docker compose logs -f app`

*With local tooling:* `./mvnw spring-boot:run`

**Smoke test (curl)**

```bash
# Create (replace token)
curl -H "Authorization: Bearer <jwt>" -H "Content-Type: application/json" \
  -d '{"title":"Buy milk","details":"2L milk","locationLat":1.35,"locationLng":103.82}' \
  http://localhost:8080/requests

# Subscribe to board SSE
curl -H "Accept: text/event-stream" http://localhost:8080/stream/board
```

---

## Non-functional concerns

* **Idempotency**: `POST /requests` supports `Idempotency-Key` header to prevent duplicates.
* **Concurrency**: optimistic locking on `requests` + unique assignment per request.
* **Rate limiting**: per-user and per-IP on create/accept endpoints.
* **Observability**: structured logs (JSON), request IDs, metrics (Micrometer), traces (OpenTelemetry).
* **Security**: input validation, size caps on details/photos, CORS, strict scopes/roles.
* **Privacy**: redact personal addresses in public board responses unless volunteer is authenticated/nearby.

---

## Roadmap

* [ ] OTP login flow (Elderly) & JWT minting
* [ ] Volunteer verification flag & guard on accept
* [ ] SSE event fan-out backed by outbox consumer
* [ ] FCM/APNs notifier consumer
* [ ] Geo upgrade to PostGIS + radius query endpoint
* [ ] Admin moderation endpoints & dashboard
* [ ] Integrate Redis rate limits & presence
* [ ] E2E tests: request lifecycle + SSE stream assertions

---

### Notes for Codex/code generators

* Keep DTOs, mappers (MapStruct), entities, and repositories aligned with the above contracts.
* Generate controllers with **`@Validated`** and **ProblemDetails** for errors.
* Place Liquibase SQL at `db/changelog/db.changelog-master.sql`.
* Expose **SSE** endpoints producing `text/event-stream` using **`Flux<ServerSentEvent<?>>`**.

That’s it—this README captures the architecture, contracts, and operational glue so you can scaffold the code quickly.

# TicketFlow — Build Progress: Phases 1–4

Status as of this document: **Phases 1–4 complete.** Phases 5–10 remain
(Booking, Waitlist, Notification, Frontend, Nginx integration pass, final docs).

---

## Phase 1 — Infrastructure Layer

**Goal:** stand up every piece of shared infrastructure so `docker compose up`
works before any service logic exists.

**Delivered:**
- `docker-compose.yml` — orchestrates Postgres, Redis, RabbitMQ, ElasticSearch,
  Nginx, and all 6 microservices + frontend (service builds gated behind a
  `services` Compose profile until each has a Dockerfile)
- `.env.example` — every config variable used across all later phases
- `infra/postgres/init-scripts/01-create-schemas.sql` — creates one schema
  per microservice (`auth`, `venue_event`, `seat_hold`, `booking`, `waitlist`)
  with **no cross-schema foreign keys**, enforcing service decoupling at the
  database level
- `infra/rabbitmq/definitions.json` + `rabbitmq.conf` — pre-declares topic
  exchanges, durable queues, and dead-letter routing for the async events the
  system will need: `booking.confirmed`, `booking.cancelled` (fans out to
  both notification and waitlist), `waitlist.offer.created`,
  `waitlist.offer.accepted`
- `infra/nginx/nginx.conf` + `conf.d/default.conf` + `proxy-common.inc` — API
  gateway routing `/api/<service>/*` to the correct upstream; `ip_hash` on the
  seat-hold upstream for WebSocket session stickiness; `/ws/` location with
  upgrade headers for STOMP/SockJS

---

## Phase 2 — Auth Service (port 8081)

**Goal:** registration, login, JWT issuance, role-based auth shared by every
other service.

**Delivered:**
- Entities: `User` (email/password hash/role/enabled), `RefreshToken`
  (hashed, revocable)
- **JWT design:** stateless HS256 access tokens carrying `userId`, `email`,
  `role` claims — every other microservice verifies them independently using
  the shared `JWT_SECRET`, with no callback to auth-service required. Refresh
  tokens are opaque random strings, stored server-side as SHA-256 hashes, and
  **rotated** on every use (old one revoked, new one issued)
- Endpoints: `POST /api/auth/register|login|refresh|logout|logout-all`,
  `GET /api/auth/me`, admin-only `GET/PATCH /api/auth/users/**`
- Business rules: `ADMIN` cannot self-register (403), duplicate emails
  rejected (409), disabled accounts blocked at login, password complexity
  enforced
- Flyway migration for `auth.users` + `auth.refresh_tokens`
- Unit tests: `JwtProviderTest`, `AuthServiceTest` (registration/login/
  disabled-account paths, Mockito-based, no DB needed)

---

## Phase 3 — Venue & Event Service (port 8082)

**Goal:** venue/seat-layout/category management (admin), event & show
creation with per-category pricing (organiser), searchable via ElasticSearch.

**Data model:**
```
Venue (admin-owned)
 └─ SeatCategory (Premium/Standard/etc, per-venue)
 └─ Seat (physical layout — bulk-generated via row-blocks)

Event (organiser-owned movie/concert listing)
 └─ Show (specific date/time/venue instance)
     └─ ShowPricing (per-category price, can differ per show)
```

**Delivered:**
- Full CRUD for venues/categories/seat-layout (admin-only writes, public
  reads)
- Full CRUD for events/shows (organiser-only writes, ownership enforced in
  the service layer — an organiser can only edit their own listings)
- `ShowSearchSyncService` — best-effort sync of a flattened
  `ShowSearchDocument` into ElasticSearch on show create/cancel. **Postgres
  stays authoritative**; ES write failures are logged, not thrown, so a
  temporary ES outage never blocks show creation
- Public search endpoint: `GET /api/events/search?q=&city=&eventType=&from=&to=`
- Flyway migration for the `venue_event` schema
- Unit tests for ownership enforcement (`EventServiceTest`)

**Cross-service addition:** a flat `GET /api/shows/{showId}` endpoint (no
`eventId` needed in the path) was added in Phase 4 for internal
service-to-service lookups.

---

## Phase 4 — Seat Map & Hold Service (port 8083)

**Goal:** the core evaluation piece — TTL-based seat holds, concurrency-safe
locking, real-time seat map via WebSocket.

**Concurrency mechanism:**
- All hold state lives in Redis: key `seat:hold:<showId>:<seatId>` = value
  `<holdId>|<customerId>`, with a TTL
- Multi-seat holds are acquired via a **Lua script** executed atomically by
  Redis (single-threaded execution guarantees no other client can interleave
  between the "check all seats free" and "set all seats" steps) — this is
  what actually prevents two customers from both winning the same seat
- Release uses a similar Lua script that only deletes a key if it's still
  owned by the releasing `holdId`

**Auto-release on TTL expiry:**
- No polling or cron job. Redis's own key expiry fires a keyspace
  notification (`__keyevent@*__:expired`); `SeatMapRedisSubscriber` catches
  it and broadcasts the seat back to `AVAILABLE`
- `notify-keyspace-events Ex` is enabled both at Redis-server level
  (`docker-compose.yml`) and programmatically at app startup (defense in
  depth)

**Real-time seat map (WebSocket + Redis Pub/Sub fan-out):**
- STOMP/SockJS endpoint at `/ws`; clients subscribe to
  `/topic/shows/{showId}/seatmap`
- Every hold/release/expiry publishes a `SeatMapEvent` to Redis channel
  `seatmap:<showId>`. **Every service instance** subscribes and re-broadcasts
  to its own locally-connected WebSocket clients — this is what makes seat
  status correct when the service is horizontally scaled (a hold placed via
  instance A is seen instantly by a browser connected to instance B)

**Other pieces:**
- `SeatHoldService` — seat-map assembly (merges venue-event-service's static
  layout with live Redis status), hold create/release, `markConverted` hook
  for booking-service (Phase 5) to call once a hold becomes a real booking
- `VenueEventClient` — REST client to venue-event-service for show/venue/
  seat/category data
- RabbitMQ consumer for `waitlist.offer.accepted` → converts an accepted
  waitlist offer into a real TTL hold
- `HoldAudit` Postgres table — durable history (Redis remains the live
  source of truth; this table never drives application logic)
- Unit tests for the concurrency conflict path, ownership checks, and
  seat-validation logic (Mockito-based against a mocked Redis service, since
  the Lua scripts themselves need a real Redis instance to execute against)

---

## What's NOT done yet (Phases 5–10)

| Phase | Scope |
|---|---|
| 5 | Booking Service — confirm booking from a hold, cancellation, booking history, organiser revenue summary |
| 6 | Waitlist Service — per-category queue, auto-assignment on cancellation, time-limited offer flow |
| 7 | Notification Service — RabbitMQ consumer, QR code generation, Gmail SMTP delivery |
| 8 | React Frontend — customer/organiser/admin views, seat map UI, WebSocket client |
| 9 | Nginx gateway wiring / full integration pass |
| 10 | README overhaul, API docs, DB schema docs, 800-word system design write-up, deployment |

---

## Running what exists today

```bash
cp .env.example .env        # fill in JWT_SECRET, SMTP creds (SMTP not used until Phase 7)
docker compose up -d postgres redis rabbitmq elasticsearch
docker compose --profile services up -d --build auth-service venue-event-service seat-hold-service
```

- Auth: `POST http://localhost:8081/api/auth/register`
- Venues: `POST http://localhost:8082/api/venues` (as ADMIN)
- Events/Shows: `POST http://localhost:8082/api/events` (as ORGANISER)
- Seat map: `GET http://localhost:8083/api/seats/shows/{showId}/map`
- WebSocket: connect to `ws://localhost:8083/ws`, subscribe to
  `/topic/shows/{showId}/seatmap`

Booking-service, waitlist-service, and notification-service exist only as
empty folder skeletons until Phases 5–7 land — their Dockerfiles aren't
written yet, so `docker compose --profile services up` will fail on them
until then. Bring up only the three services above for now.

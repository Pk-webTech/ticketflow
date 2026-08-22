# TicketFlow — Movie & Concert Ticket Booking Platform

Microservices ticket booking system with real-time visual seat maps, TTL-based
seat holds, concurrency-safe booking, waitlist auto-assignment with
time-limited offers, and QR-code email tickets.

## Stack

| Layer | Technology |
|---|---|
| Frontend | React 18 + Vite, STOMP/SockJS |
| Backend | Spring Boot 3.3 / Java 21 — one JVM + one schema per service |
| Relational DB | PostgreSQL (schema-per-service) |
| Search | ElasticSearch (event browse/filter) |
| Cache / locks / TTL holds | Redis |
| Async messaging | RabbitMQ (topic exchanges + DLQs) |
| Real-time | WebSocket (STOMP) + Redis Pub/Sub fan-out |
| Gateway / LB | Nginx |
| QR + Email | ZXing + Spring Mail (any free-tier SMTP) |
| Containerization | Docker / Docker Compose |

## Services

| Service | Port | Schema | Responsibility |
|---|---|---|---|
| `auth-service` | 8081 | `auth` | Registration, login, JWT, roles |
| `venue-event-service` | 8082 | `venue_event` | Venues, seat layout, categories, events, shows, pricing, ES search |
| `seat-hold-service` | 8083 | `seat_hold` | Seat map, Redis TTL holds, concurrency locking, WebSocket push |
| `booking-service` | 8084 | `booking` | Confirm from hold, cancel, history, organiser revenue |
| `waitlist-service` | 8085 | `waitlist` | Per-category queue, auto-assignment, time-limited offers |
| `notification-service` | 8086 | `notification` | QR generation, SMTP delivery |
| `frontend` | 80 | — | React SPA |
| `nginx` | 80 | — | Gateway + load balancing |

---

## Setup

### Prerequisites
Docker + Docker Compose. (For local non-Docker dev: JDK 21, Maven 3.9, Node 20.)

### 1. Configure environment

```bash
cp .env.example .env
```

Fill in at minimum:

```bash
JWT_SECRET=<at least 32 characters, shared by every service>
SMTP_USERNAME=your.address@gmail.com
SMTP_PASSWORD=<16-char Gmail App Password>
MAIL_FROM=your.address@gmail.com
PUBLIC_BASE_URL=http://localhost
```

> **Gmail:** enable 2FA, then create an App Password at
> <https://myaccount.google.com/apppasswords>. Your normal password will not
> authenticate. Any SMTP provider (Brevo, Mailtrap, SendGrid) works by changing
> only `SMTP_*`. To demo without credentials, set `MAIL_DELIVERY_ENABLED=false`
> — emails are rendered and logged but not sent.

### 2. Start infrastructure

```bash
docker compose up -d postgres redis rabbitmq elasticsearch
```

### 3. Start services

```bash
docker compose --profile services up -d --build
```

App: **http://localhost** · RabbitMQ UI: http://localhost:15672

### 4. Seed a bookable show

1. Register an **ADMIN** → create a venue → add categories (Premium, Standard)
   → generate seat rows.
2. Register an **ORGANISER** → create an event → schedule a show at that venue
   with per-category pricing.
3. Register a **CUSTOMER** → browse → pick seats.

### Local dev without Docker

```bash
# each service
cd services/<service> && mvn spring-boot:run

# frontend (proxies /api and /ws to the gateway)
cd frontend && npm install && npm run dev
```

---

## Seat hold logic

A hold is a single Redis key with a TTL:

```
seat:hold:<showId>:<seatId>  →  <holdId>|<customerId>     EX 600
```

**Acquisition is atomic across all requested seats.** A loop of per-seat
`SETNX` calls is *not* atomic as a set — another request can interleave and
both customers end up with partial holds. A Lua script instead performs
"check every seat is free, then set every seat" in one indivisible call
(Redis executes scripts single-threaded). The loser is told which seat blocked
them and receives HTTP 409; nothing is written on failure.

**Release** uses a mirror script that deletes a key only if it is still owned
by the releasing `holdId`, so a late release can never stomp a newer hold.

**Auto-release on abandonment** is delegated to Redis itself. With
`notify-keyspace-events Ex`, Redis emits `__keyevent@*__:expired` the moment a
key lapses; `SeatMapRedisSubscriber` catches it and broadcasts that seat back
to `AVAILABLE`. No polling, no cron, sub-second latency. The frontend also
releases explicitly on unmount — instant in the common case — but the TTL is
what guarantees release when a browser is force-killed.

### Concurrency protection

| Layer | Mechanism | Protects against |
|---|---|---|
| 1 | Redis Lua atomic multi-seat `SETNX` | Two customers holding the same seat |
| 2 | Partial unique index `(show_id, seat_id) WHERE active` | Two customers *booking* the same seat, even if Redis is bypassed or flushed |
| 3 | `@TransactionalEventListener` (after-commit publish) | A QR ticket being emailed for a booking that rolled back |
| 4 | `SELECT … FOR UPDATE SKIP LOCKED` on the waitlist head | Two cancellations offering the same seat to the same person |

Layer 2 is the real guarantee; layer 1 exists so the overwhelming majority of
conflicts are caught fast and produce a friendly error rather than a database
exception.

## Waitlist logic

**Joining.** When a show is sold out, a customer joins a queue for a specific
seat **category**. One live entry per `(show, category, customer)`, enforced by
a partial unique index. Position is derived from `created_at`, never stored —
so leaving or converting never requires renumbering.

**Auto-assignment.** Cancelling publishes `booking.cancelled` with the freed
seats and their category IDs. Waitlist-service groups them by category and
offers each group to the head of that category's queue, using
`FOR UPDATE SKIP LOCKED` so concurrent cancellations can't double-offer. Excess
seats cascade to subsequent waiters in the same pass — a 4-seat cancellation
can satisfy four single-seat waiters at once. If a queue is empty, the seats
simply return to general availability.

**Time-limited offer.** The offer stores the seats, a deadline
(`WAITLIST_OFFER_TTL_SECONDS`, default 15 min) and a 256-bit URL-safe token.
The customer is emailed a claim link containing that token. Viewing the offer
needs only the token; *accepting* also requires a signed-in session matching
the offer's owner. Accepting flips the status, so replaying the link is a no-op.

**Expiry cascade.** `WaitlistOfferSweeper` runs every 30s, marks overdue
PENDING offers EXPIRED, and re-offers their seats to the next person in line
(each in its own `REQUIRES_NEW` transaction, so one bad row can't roll back the
batch). The sweep interval affects only *latency* — `expires_at` is re-checked
on read, so a stale link is rejected even before the sweeper runs.

**Acceptance** publishes `waitlist.offer.accepted`; seat-hold-service converts
it into an ordinary TTL hold, and the customer completes checkout through the
standard flow. There is no separate purchase path to secure.

## QR tickets

On confirmation, `booking.confirmed` is published with a fully self-contained
payload. notification-service generates a 300×300 PNG QR (ZXing, error
correction level M — tolerates ~15% damage, which matters for cracked phone
screens) encoding **only the booking reference** (`TF-8F3K2QD1`), never a URL
or personal data. A lost printed ticket therefore leaks nothing, and gate staff
scan it into the authenticated `GET /api/bookings/reference/{ref}`.

The QR is attached as an **inline CID resource**, not a base64 data-URI —
Gmail and Outlook silently strip `data:` images in HTML mail.

Delivery is idempotent: a partial unique index on `(type, dedupe_key) WHERE
status='SENT'` means RabbitMQ's at-least-once redelivery can't email the same
ticket twice.

## Docs

- [`docs/SYSTEM_DESIGN.md`](docs/SYSTEM_DESIGN.md) — 800-word design write-up
- [`docs/API.md`](docs/API.md) — full endpoint reference
- [`docs/DB_SCHEMA.md`](docs/DB_SCHEMA.md) — tables, indexes, relationships
- [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md) — hosting guide

## Testing the flows

**TTL auto-release** — hold seats in one browser, watch them grey out in
another; wait out `SEAT_HOLD_TTL_SECONDS` (drop it to 30 for a quick demo) and
watch them go green in both without a refresh.

**Concurrency** — two browsers select the same seat and click Hold
simultaneously; exactly one succeeds, the other gets a 409 naming the seat.

**Waitlist** — sell out a category, join the waitlist as a second customer,
cancel the first customer's booking; the offer email arrives within seconds.
Ignore it and watch the sweeper cascade it to the next person.

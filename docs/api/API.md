# TicketFlow — API Reference

All requests go through the Nginx gateway at `http://localhost` (or your
deployed origin). Authenticated endpoints require:

```
Authorization: Bearer <accessToken>
```

Roles: `CUSTOMER`, `ORGANISER`, `ADMIN`.

Errors share one shape:

```json
{ "timestamp": "2026-08-23T10:15:00Z", "status": 409, "error": "Conflict", "message": "..." }
```

| Status | Meaning in this system |
|---|---|
| 400 | Validation failure |
| 401 | Missing/expired token |
| 403 | Wrong role, or acting on someone else's resource |
| 404 | Not found |
| 409 | **Seat/hold/offer conflict** — the interesting one |
| 502 | Upstream service unreachable |

---

## Auth — `/api/auth`

| Method | Path | Auth | Body / Notes |
|---|---|---|---|
| POST | `/register` | — | `{ fullName, email, password, role }` |
| POST | `/login` | — | `{ email, password }` → `{ accessToken, refreshToken, userId, email, role }` |
| POST | `/refresh` | — | `{ refreshToken }` |

---

## Venues — `/api/venues` (writes: ADMIN)

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/api/venues` | public | List venues |
| GET | `/api/venues/{id}` | public | |
| POST | `/api/venues` | ADMIN | `{ name, city, address }` |
| GET | `/api/venues/{id}/categories` | public | Seat categories |
| POST | `/api/venues/{id}/categories` | ADMIN | `{ name, description }` |
| GET | `/api/venues/{id}/seats` | public | Full seat layout |
| POST | `/api/venues/{id}/seats` | ADMIN | `{ categoryId, rowStart, rowEnd, seatsPerRow }` — bulk row-block generation |

---

## Events & Shows — `/api/events` (writes: ORGANISER, ownership enforced)

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/api/events` | public | |
| GET | `/api/events/search?q=&city=&eventType=&from=&to=` | public | ElasticSearch-backed |
| GET | `/api/events/{id}` | public | |
| POST | `/api/events` | ORGANISER | `{ title, eventType, description, city }` |
| GET | `/api/events/{id}/shows` | public | |
| POST | `/api/events/{id}/shows` | ORGANISER | `{ venueId, startsAt, pricing:[{categoryId, price}] }` |
| GET | `/api/shows/{showId}` | public | Flat lookup used service-to-service |

---

## Seat map & holds — `/api/seats`

### `GET /api/seats/shows/{showId}/map`
Public. Merges the venue's static layout with live Redis hold state.

```json
{
  "showId": "…",
  "seats": [
    { "seatId": "…", "seatLabel": "A12", "rowLabel": "A", "seatNumber": 12,
      "categoryId": "…", "categoryName": "Premium", "price": 450.00,
      "status": "AVAILABLE" }
  ]
}
```

`status` ∈ `AVAILABLE | HELD | BOOKED`.

### `POST /api/seats/holds`
CUSTOMER. Atomically holds every listed seat, or none.

```json
{ "showId": "…", "seatIds": ["…", "…"] }
```

**201** →
```json
{ "holdId": "…", "showId": "…", "seatIds": ["…"],
  "expiresAt": "2026-08-23T10:25:00Z", "ttlSeconds": 600 }
```

**409** — another customer already holds one of these seats. Nothing was
written; the message names the blocking seat.

### `DELETE /api/seats/holds/{holdId}`
CUSTOMER. Explicit release. Idempotent — releasing an already-expired hold is a
no-op, not an error.

### `WS /ws` → `/topic/shows/{showId}/seatmap`
STOMP over SockJS. Every hold, release, TTL expiry and booking is pushed:

```json
{ "showId": "…", "seatIds": ["…"], "status": "HELD",
  "holdId": "…", "timestamp": "2026-08-23T10:15:00Z" }
```

---

## Bookings — `/api/bookings`

### `POST /api/bookings`
CUSTOMER. Converts a live hold into a confirmed booking.

```json
{ "showId": "…", "holdId": "…", "seatIds": ["…"], "customerName": "Alice" }
```

**201** → full booking with `bookingReference`, priced seats, `totalAmount`.
Triggers the QR ticket email.

| Failure | Status | Cause |
|---|---|---|
| Hold expired | 409 | TTL lapsed before checkout |
| Not hold owner | 403 | Hold belongs to another customer |
| Seat already booked | 409 | Lost the unique-index race |

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/api/bookings/me?page=&size=` | CUSTOMER | Paged history, newest first |
| GET | `/api/bookings/{id}` | owner / ORGANISER / ADMIN | |
| GET | `/api/bookings/reference/{ref}` | ORGANISER / ADMIN | **Gate scan** — the value in the QR |
| PATCH | `/api/bookings/{id}/cancel` | owner / ADMIN | Frees seats, triggers waitlist assignment |
| GET | `/api/bookings/shows/{showId}/booked-seats` | public | Seat IDs permanently booked |
| GET | `/api/bookings/summary/shows/{showId}` | ORGANISER / ADMIN | |
| GET | `/api/bookings/summary/events/{eventId}` | ORGANISER / ADMIN | |

Cancellation fails with **409** if already cancelled, or if the show has
already started.

Revenue summary:
```json
{ "scope": "EVENT", "confirmedBookings": 42, "cancelledBookings": 3,
  "seatsSold": 87, "grossRevenue": 39150.00, "refundedAmount": 1350.00,
  "netRevenue": 39150.00,
  "perCategory": [{ "categoryId": "…", "categoryName": "Premium",
                    "seatsSold": 30, "revenue": 13500.00 }] }
```

---

## Waitlist — `/api/waitlist`

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/api/waitlist` | CUSTOMER | `{ showId, eventId, categoryId, categoryName, quantity }` → 409 if already queued |
| GET | `/api/waitlist/me` | CUSTOMER | Entries with 1-based `position` (null unless ACTIVE) |
| DELETE | `/api/waitlist/{entryId}` | owner | Leave the queue |
| GET | `/api/waitlist/shows/{showId}/categories/{categoryId}/length` | CUSTOMER | `{ "waiting": 7 }` |
| GET | `/api/waitlist/offers/token/{token}` | **public** | The emailed link — the token is the credential |
| POST | `/api/waitlist/offers/token/{token}/accept` | CUSTOMER | Must match the offer's owner |

Offer response:
```json
{ "offerId": "…", "showId": "…", "seatIds": ["…"], "seatLabels": ["A12"],
  "status": "PENDING", "expiresAt": "2026-08-23T10:30:00Z",
  "secondsRemaining": 842 }
```

Accepting publishes `waitlist.offer.accepted`; seat-hold-service converts it to
a normal TTL hold and the customer finishes checkout on the seat map.

**409** on accept: offer expired, or already used.

---

## Async events (RabbitMQ)

| Exchange | Routing key | Producer | Consumers |
|---|---|---|---|
| `booking.events` | `booking.confirmed` | booking | notification (QR email) |
| `booking.events` | `booking.cancelled` | booking | notification (email), waitlist (**auto-assignment**) |
| `waitlist.events` | `waitlist.offer.created` | waitlist | notification (claim-link email) |
| `waitlist.events` | `waitlist.offer.accepted` | waitlist | seat-hold (offer → TTL hold) |

Every queue dead-letters to `dlx.ticketflow` after retry exhaustion, so a
poison message never blocks the queue behind it.

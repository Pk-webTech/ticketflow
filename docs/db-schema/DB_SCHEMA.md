# TicketFlow — Database Schema

One PostgreSQL instance, **one schema per microservice**, no cross-schema
foreign keys or joins. Services communicate over REST/AMQP, never through the
database — so each schema can be extracted to its own instance without code
changes. Each service owns its own Flyway migrations.

```
auth · venue_event · seat_hold · booking · waitlist · notification
```

---

## `auth`

| Table | Key columns |
|---|---|
| `users` | `id`, `email` (unique), `password_hash` (BCrypt cost 12), `full_name`, `role`, `created_at` |
| `refresh_tokens` | `id`, `user_id`, `token_hash`, `expires_at`, `revoked_at` |

Refresh tokens are opaque, high-entropy strings stored **hashed** so a database
leak can't be replayed. Access tokens are stateless JWTs (`sub`, `email`,
`role`, `type=access`) signed with a shared `JWT_SECRET`, so every service
verifies independently without a call back to auth-service.

---

## `venue_event`

```
venues ──< seat_categories ──< seats
   │
events ──< shows ──< show_pricing
```

| Table | Notes |
|---|---|
| `venues` | `id`, `name`, `city`, `address` |
| `seat_categories` | `id`, `venue_id`, `name` (Premium/Standard), `description` |
| `seats` | `id`, `venue_id`, `category_id`, `row_label`, `seat_number`, `label` — the physical layout, generated in row-blocks |
| `events` | `id`, `organiser_id`, `title`, `event_type`, `description`, `city` |
| `shows` | `id`, `event_id`, `venue_id`, `starts_at`, `status` |
| `show_pricing` | `show_id`, `category_id`, `price` — pricing is per **show**, so the same venue category can cost differently on a Friday night |

Shows are also flattened into an ElasticSearch `ShowSearchDocument` for browse
and filter. **Postgres stays authoritative**; ES write failures are logged, not
thrown, so an ES outage never blocks show creation.

---

## `seat_hold`

| Table | Notes |
|---|---|
| `hold_audit` | `hold_id` (PK), `show_id`, `customer_id`, `seat_ids` (CSV), `status`, `ttl_seconds`, `created_at`, `resolved_at` |

`status ∈ CREATED | RELEASED | EXPIRED | CONVERTED`.

**This table is an audit trail only.** Live hold state lives in Redis
(`seat:hold:<showId>:<seatId>` with a TTL) and nothing here drives application
logic — it exists for support, fraud review and reconciliation.

---

## `booking`

| Table | Notes |
|---|---|
| `bookings` | `id`, `booking_reference` (unique, QR payload), `show_id`, `event_id`, `hold_id`, `customer_id`, `customer_email`, `status`, `total_amount`, denormalised `event_title`/`venue_name`/`show_starts_at`, `created_at`, `cancelled_at` |
| `booking_seats` | `id`, `booking_id` → bookings, `show_id`, `seat_id`, `seat_label`, `row_label`, `seat_number`, `category_id`, `category_name`, `price`, **`active`** |

### The concurrency guard

```sql
CREATE UNIQUE INDEX uq_active_seat_per_show
  ON booking.booking_seats (show_id, seat_id)
  WHERE active;
```

This is the authoritative protection against double-booking. Two concurrent
transactions cannot both commit the same `(show_id, seat_id)`; the loser gets a
unique violation, translated to HTTP 409.

Cancelling sets `active = false` rather than deleting: one flag both releases
the seat under the index and preserves history. Show/venue metadata is
denormalised onto `bookings` so tickets and history render without cross-service
calls.

---

## `waitlist`

| Table | Notes |
|---|---|
| `waitlist_entries` | `id`, `show_id`, `event_id`, `category_id`, `category_name`, `customer_id`, `customer_email`, `quantity`, `status`, `created_at` |
| `seat_offers` | `id`, `waitlist_entry_id` → entries, `show_id`, `category_id`, `seat_ids` (CSV), `seat_labels`, `token` (unique, 256-bit), `status`, `offered_at`, `expires_at`, `resolved_at` |

`WaitlistStatus ∈ ACTIVE | OFFERED | CONVERTED | EXPIRED | CANCELLED`
`OfferStatus ∈ PENDING | ACCEPTED | EXPIRED | DECLINED`

```sql
-- one live entry per customer per (show, category)
CREATE UNIQUE INDEX uq_waitlist_live_entry
  ON waitlist.waitlist_entries (show_id, category_id, customer_id)
  WHERE status IN ('ACTIVE', 'OFFERED');

-- drives the "who is next?" FOR UPDATE SKIP LOCKED query
CREATE INDEX idx_waitlist_queue
  ON waitlist.waitlist_entries (show_id, category_id, status, created_at);

-- the sweeper scans exactly this
CREATE INDEX idx_offer_pending_expiry
  ON waitlist.seat_offers (status, expires_at);
```

**Position is derived, never stored.** Ordering by `created_at` (tie-broken by
`id`) is stable and index-backed; storing integer positions would force
renumbering every downstream row on each leave/convert — write amplification
plus a race.

---

## `notification`

| Table | Notes |
|---|---|
| `notification_log` | `id`, `type`, `recipient`, `subject`, `dedupe_key`, `status`, `error_detail`, `created_at` |

```sql
CREATE UNIQUE INDEX uq_notification_dedupe
  ON notification.notification_log (type, dedupe_key)
  WHERE status = 'SENT';
```

RabbitMQ is at-least-once: a consumer that crashes after sending but before
acking sees the message again. `dedupe_key` (booking reference or offer id)
makes redelivery a no-op instead of a second QR ticket in the customer's inbox.

---

## Redis keyspace

| Key / channel | Purpose |
|---|---|
| `seat:hold:<showId>:<seatId>` = `<holdId>\|<customerId>`, TTL | Live seat hold — the source of truth for "is this seat held" |
| `seatmap:<showId>` (pub/sub) | Seat-map events fanned out to every service instance |
| `__keyevent@0__:expired` | Redis's own expiry notification — drives TTL auto-release |

Requires `notify-keyspace-events Ex`, set both in `docker-compose.yml` and
programmatically at startup (defense in depth).

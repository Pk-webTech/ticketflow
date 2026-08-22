# TicketFlow — System Design Write-up

*(~790 words)*

## Architecture

Six Spring Boot microservices behind an Nginx gateway, each owning one Postgres
schema exclusively. Services never read each other's tables — they talk over
REST for synchronous lookups and RabbitMQ for everything else. Redis backs seat
holds and WebSocket fan-out; ElasticSearch backs event search. A React SPA is
served from the same origin as the API, so the browser sees one host.

## Seat hold and TTL mechanism

A hold is a Redis key: `seat:hold:<showId>:<seatId>` → `<holdId>|<customerId>`,
written with a TTL (default 600s, configurable via `SEAT_HOLD_TTL_SECONDS`).
Redis is the live source of truth for "is this seat currently held"; a Postgres
`hold_audit` table records history but never drives logic.

Expiry is delegated entirely to Redis rather than a scheduler. With
`notify-keyspace-events Ex` enabled, Redis emits `__keyevent@*__:expired` the
instant a key lapses; `SeatMapRedisSubscriber` catches it, and broadcasts that
seat back to AVAILABLE. This means abandoned checkouts self-heal with zero
polling and sub-second latency, and there is no cron job whose interval becomes
a correctness parameter. The frontend also releases explicitly on unmount — a
courtesy that makes the common case instant, but the TTL is what makes the
guarantee hold when a browser is force-killed.

## Concurrency prevention

Two independent layers, because Redis and Postgres fail differently.

**Layer one — atomic multi-seat acquisition.** Holding N seats must be
all-or-nothing. A per-seat `SETNX` is atomic individually, but a loop of them
is not atomic *across the set*: another request can interleave and both
customers end up with partial holds. Instead a Lua script does "check every
seat is free, then set every seat" in one call. Redis executes scripts
single-threaded, so that sequence is indivisible from every other client's
point of view. The loser learns which seat blocked them and gets a 409.
Release uses a mirror script that only deletes a key still owned by the
releasing `holdId`, so a late release can't stomp a newer customer's hold.

**Layer two — the database has the final say.** `booking_seats` carries a
partial unique index: `(show_id, seat_id) WHERE active`. Even if Redis were
flushed, restarted, or bypassed, two CONFIRMED rows for the same seat are
physically impossible. Booking-service checks the Redis hold first (fast, and
yields a friendly "your hold expired" message), then lets the insert run; a
unique violation is translated to 409. Cancellation flips `active = false`
rather than deleting, which simultaneously frees the seat under the index and
preserves history.

A third, quieter guarantee: the QR-ticket email is published via
`@TransactionalEventListener` (after-commit). A booking that loses the race
rolls back and therefore can never produce a ticket for a booking that doesn't
exist.

## Waitlist auto-assignment

Queues are partitioned per `(showId, categoryId)` — a Premium waiter is never
offered a Standard seat. Position is derived from `created_at` rather than
stored as an integer, so leaving or converting never requires renumbering
downstream rows.

Cancelling a booking publishes `booking.cancelled` carrying the freed seats
with their category IDs. Waitlist-service consumes it, groups seats by
category, and offers each group to the head of that category's queue.

The "who is next" query uses `SELECT … FOR UPDATE SKIP LOCKED`. Two
cancellations for the same show can be processed concurrently on different
instances; without the lock both would read the same head row and offer that
one person two seat sets while the person behind gets nothing. With SKIP
LOCKED, the second consumer transparently takes the second person in line —
nobody blocks, nobody is double-offered. If a cancellation frees more seats
than the head waiter wants, the remainder cascades to subsequent waiters in the
same pass, so a four-seat cancellation can satisfy four single-seat waiters at
once.

## Time-limited offers

An offer row stores the allocated seats, a deadline (`WAITLIST_OFFER_TTL_SECONDS`,
default 900s), and a 256-bit URL-safe token. The token is emailed inside the
claim link and *is* the credential for viewing, so it must be unguessable;
accepting additionally requires a signed-in session matching the offer's owner.
Accepting flips the status, making link replay a no-op.

Offers use a swept table rather than a Redis TTL, deliberately. Seat holds are
high-frequency and ephemeral, and losing an expiry event costs almost nothing.
Offer expiry instead triggers a *transactional cascade* — mark expired,
re-offer to the next waiter, emit a new email — which needs the database
anyway and must survive a restart. A keyspace notification is fire-and-forget:
if no instance is subscribed when it fires, the offer would hang PENDING
forever. A swept table is self-healing; whatever is overdue is picked up on the
next tick regardless of downtime. Critically the sweep interval affects only
cascade *latency*, never correctness — `expires_at` is re-checked on read, so a
customer clicking a stale link is rejected even before the sweeper runs.

Accepting publishes `waitlist.offer.accepted`, which seat-hold-service converts
into an ordinary TTL hold. The customer then completes checkout through the
exact same path as any other buyer — there is no second purchase flow to secure
or maintain.

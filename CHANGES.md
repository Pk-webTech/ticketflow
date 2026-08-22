# Merge notes — Phases 5–10 integrated

This is your repo with phases 5–10 added. Phases 1–4 are your original files,
untouched except for the four deliberate fixes listed at the bottom.

## Added

```
services/booking-service/        37 Java files, Flyway migration, Dockerfile, 11 tests
services/waitlist-service/       30 Java files, Flyway migration, Dockerfile, 4 tests
services/notification-service/   10 Java files, 3 Thymeleaf email templates, 2 tests
frontend/                        React 18 + Vite SPA (builds clean — verified)
docs/api/API.md                  Full endpoint reference
docs/db-schema/DB_SCHEMA.md      Tables, indexes, Redis keyspace
docs/system-design/SYSTEM_DESIGN.md   816-word write-up
docs/DEPLOYMENT.md               Railway / Render / VM guide
README.md                        Replaced the phase-1 placeholder
```

## Fixed in your existing files

**1. `infra/nginx/conf.d/default.conf` — prefix-stripping bug (this one was
breaking every route).** Every `proxy_pass` ended in a trailing slash, which
makes Nginx *strip the matched location prefix*. `/api/auth/login` was arriving
at auth-service as `/login`, which no controller maps — so every API call
through the gateway would have 404'd. Trailing slashes removed. Also added the
missing `/api/shows/` route (the flat show lookup had no gateway entry at all).

**2. `services/seat-hold-service/.../SecurityConfig.java` — dead matcher.**
`DELETE /api/seats/holds/**` never matched, because the real route is
`/api/seats/shows/{showId}/holds/{holdId}`. Release requests fell through to
`anyRequest().authenticated()`, so any signed-in ORGANISER or ADMIN could
release a customer's hold. Changed to `/api/seats/shows/**`.

**3. `infra/postgres/init-scripts/01-create-schemas.sql`** — added the
`notification` schema and its grant; notification-service needs it for the
dedupe table.

**4. `docker-compose.yml`** — notification-service had no DB env vars (it needs
Postgres for delivery idempotency); waitlist-service had no `PUBLIC_BASE_URL`
(without it, emailed claim links point nowhere). Added both, plus
`MAIL_DELIVERY_ENABLED` and `WAITLIST_SWEEP_INTERVAL_MS`.

`.env` and `.env.example` gained the matching keys.

## Contracts I aligned to your actual code

My phase 5–10 code originally assumed slightly different shapes. All corrected
against your real DTOs:

| Assumed | Your actual | Fixed in |
|---|---|---|
| `ShowResponse.startsAt`, `.eventTitle`, `.venueName` | `showDateTime`; title/venue live on `EventResponse`/`VenueResponse` | `VenueEventClient` now fetches event + venue separately, best-effort |
| `ShowResponse.pricing[].PricingDto` | `CategoryPriceView` | `ShowDetailsDto` |
| `SeatResponse.label`, `.categoryName` | only `rowLabel` + `seatNumber` + `section` | `VenueSeatDto.displayLabel()` composes "A12" |
| `POST /api/seats/holds` | `POST /api/seats/shows/{showId}/hold` | `frontend/src/lib/api.js` |
| `DELETE /api/seats/holds/{holdId}` | `DELETE /api/seats/shows/{id}/holds/{id}` **with a seatIds body** | `api.js` (DELETE now carries a payload) |
| `SeatStatus: HELD` | `HELD_BY_ME` / `HELD_BY_OTHERS` | `SeatMap.jsx` — own holds stay interactive |
| `EventRequest.eventType`, `.city` | `type`, `language`, `durationMinutes` | `OrganiserDashboard.jsx` |
| Seat layout `{rowStart, rowEnd, seatsPerRow}` | `{blocks: [{categoryId, rowLabel, seatCount, section}]}` | `AdminVenues.jsx` expands the row range client-side |
| `SeatCategoryRequest.description` | `displayColor`, `defaultPrice`, `displayOrder` | `AdminVenues.jsx` |

## Build status — read this before you run it

- **Frontend: verified.** `npm install && vite build` succeeds, 167 modules, no errors.
- **Java: NOT compiled.** This container has a JRE but no `javac`, and Maven
  Central is blocked, so the three new services have never been through a
  compiler. The contract mismatches above were the substantive risk and are
  fixed, but expect a few import or signature nits on first build:

```bash
cd services/booking-service      && mvn -B clean test
cd ../waitlist-service           && mvn -B clean test
cd ../notification-service       && mvn -B clean test
```

## First run

```bash
docker compose down -v      # -v is required: the notification schema is added
                            # by an init script that only runs on a fresh volume
docker compose up -d postgres redis rabbitmq elasticsearch
docker compose --profile services up -d --build
```

Then set `SMTP_USERNAME` / `SMTP_PASSWORD` / `MAIL_FROM_ADDRESS` in `.env`, or
set `MAIL_DELIVERY_ENABLED=false` to exercise the whole pipeline without SMTP.

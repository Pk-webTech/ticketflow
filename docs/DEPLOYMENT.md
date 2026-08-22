# Deployment

## Option A — Railway (simplest for this stack)

Railway can host all six services plus managed Postgres, Redis and RabbitMQ in
one project.

1. Push the repo to GitHub.
2. Create a Railway project → add **PostgreSQL**, **Redis**, and a **RabbitMQ**
   plugin (or CloudAMQP free tier).
3. For each service, add a service pointing at its subdirectory
   (`services/auth-service`, etc.). Railway detects the Dockerfile.
4. Set shared variables at the **project** level so every service inherits them:
   `JWT_SECRET`, `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`,
   `REDIS_HOST`, `REDIS_PORT`, `RABBITMQ_*`, `SMTP_*`, `PUBLIC_BASE_URL`.
5. Per-service: set `DB_SCHEMA` (`auth`, `venue_event`, `seat_hold`, `booking`,
   `waitlist`, `notification`) and the cross-service URLs
   (`VENUE_EVENT_SERVICE_URL`, `SEAT_HOLD_SERVICE_URL`) to Railway's internal
   hostnames.
6. Deploy the frontend as a static site, or deploy the `nginx` service with the
   gateway config so everything stays same-origin.

**Enable Redis keyspace notifications** — managed Redis often ships with them
off, which silently breaks TTL auto-release broadcasting:

```
CONFIG SET notify-keyspace-events Ex
```

The app also attempts this at startup, but a managed instance may deny
`CONFIG SET`; if the startup log warns about it, set it from the provider's
console.

## Option B — Render

Same shape: one Web Service per Dockerfile, plus Render Postgres and Redis.
RabbitMQ isn't offered natively — use CloudAMQP's free tier and point
`RABBITMQ_*` at it (set `RABBITMQ_VHOST` to the vhost CloudAMQP assigns, which
is *not* `/ticketflow`, and import `infra/rabbitmq/definitions.json` through
its management UI so the exchanges, queues and DLQ bindings exist).

## Option C — Single VM (most faithful to local)

Any VM with Docker:

```bash
git clone <repo> && cd ticketflow
cp .env.example .env    # fill in real secrets
docker compose up -d postgres redis rabbitmq elasticsearch
docker compose --profile services up -d --build
```

Put Caddy or Certbot in front for TLS, and set `PUBLIC_BASE_URL=https://yourdomain`.

## Pre-flight checklist

- [ ] `JWT_SECRET` is ≥32 chars and **identical across all six services** — a
      mismatch means tokens issued by auth-service fail verification everywhere.
- [ ] `PUBLIC_BASE_URL` is the externally reachable origin, not an internal
      service name — it's baked into waitlist claim links inside emails.
- [ ] SMTP App Password set (not the account password).
- [ ] Redis `notify-keyspace-events` includes `Ex`.
- [ ] RabbitMQ topology imported (exchanges, queues, DLQ bindings).
- [ ] ElasticSearch reachable, or accept degraded search (Postgres remains
      authoritative, so browse still works).
- [ ] WebSocket upgrade allowed end-to-end — some PaaS proxies need it enabled
      explicitly, and without it the seat map silently stops updating live.

## Scaling notes

`docker compose up --scale seat-hold-service=3` works as-is. Seat correctness
does not depend on which instance a request lands on: holds live in Redis and
seat-map events fan out through Redis pub/sub to every instance. Nginx uses
`ip_hash` for seat-hold-service purely to keep STOMP sessions stable across
reconnects.

# TicketFlow — Movie & Concert Ticket Booking Platform

Microservices-based ticket booking system with real-time seat maps, TTL-based
seat holds, waitlist auto-assignment, and QR-code email tickets.

## Stack

| Layer | Technology |
|---|---|
| Frontend | React |
| Backend | Spring Boot (true microservices, one JVM + schema per service) |
| Relational DB | PostgreSQL (schema-per-service) |
| Search | ElasticSearch (event browse/filter) |
| Cache / Locks / TTL holds | Redis |
| Async messaging | RabbitMQ |
| Real-time seat map | WebSocket (STOMP/SockJS) + Redis Pub/Sub fan-out |
| Gateway / Load balancing | Nginx |
| Containerization | Docker / Docker Compose |

## Services

- `services/auth-service` — registration, login, JWT issuance, role-based auth (customer/organiser/admin)
- `services/venue-event-service` — venue + seat layout/category management, event/show creation, pricing
- `services/seat-hold-service` — per-show seat map, Redis TTL holds, concurrency-safe locking, WebSocket push
- `services/booking-service` — booking confirmation, cancellation, history, organiser revenue summary
- `services/waitlist-service` — per-category waitlist queue, auto-assignment, time-limited offer flow
- `services/notification-service` — RabbitMQ consumer, QR code generation, Gmail SMTP delivery
- `frontend` — React SPA (customer/organiser/admin views)

## Quick start (infra only, for now)

```bash
cp .env.example .env    # fill in real secrets (JWT_SECRET, SMTP creds, etc.)
docker compose up -d postgres redis rabbitmq elasticsearch
```

- Postgres: `localhost:5432` (schemas: `auth`, `venue_event`, `seat_hold`, `booking`, `waitlist`)
- Redis: `localhost:6379`
- RabbitMQ management UI: `http://localhost:15672` (user/pass from `.env`)
- ElasticSearch: `http://localhost:9200`

Once services are implemented (later phases), bring up everything:

```bash
docker compose --profile services up -d --build
```

App will be available at `http://localhost` (via Nginx gateway).

## Phases

1. ✅ Infra scaffolding (this phase)
2. Auth Service
3. Venue & Event Service
4. Seat Map & Hold Service (core: TTL holds, concurrency, WebSocket)
5. Booking Service
6. Waitlist Service
7. Notification Service (QR + email)
8. React Frontend
9. Nginx gateway wiring / integration pass
10. Docs, deployment, system design write-up

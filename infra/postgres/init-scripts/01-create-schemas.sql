-- =============================================================================
-- TicketFlow — Schema-per-service initialization
-- Runs automatically on first container start (docker-entrypoint-initdb.d).
-- Each microservice owns its schema; no cross-schema foreign keys — services
-- integrate via REST calls / RabbitMQ events, not shared DB joins.
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS auth;
CREATE SCHEMA IF NOT EXISTS venue_event;
CREATE SCHEMA IF NOT EXISTS seat_hold;
CREATE SCHEMA IF NOT EXISTS booking;
CREATE SCHEMA IF NOT EXISTS waitlist;

-- Grants (single shared DB user for local/dev simplicity; use per-schema
-- roles in production)
GRANT ALL PRIVILEGES ON SCHEMA auth        TO CURRENT_USER;
GRANT ALL PRIVILEGES ON SCHEMA venue_event TO CURRENT_USER;
GRANT ALL PRIVILEGES ON SCHEMA seat_hold   TO CURRENT_USER;
GRANT ALL PRIVILEGES ON SCHEMA booking     TO CURRENT_USER;
GRANT ALL PRIVILEGES ON SCHEMA waitlist    TO CURRENT_USER;

-- UUID generation used across all services for primary keys
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

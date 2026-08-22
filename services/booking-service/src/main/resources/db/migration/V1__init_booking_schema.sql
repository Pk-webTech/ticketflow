-- V1__init_booking_schema.sql
-- booking-service owns the `booking` schema exclusively.
--
-- CONCURRENCY NOTE (this is the important bit):
-- Redis holds are the FIRST line of defence against two customers selecting
-- the same seat. The PARTIAL UNIQUE INDEX below is the SECOND, authoritative
-- line: even if Redis were flushed, restarted, or bypassed entirely, the
-- database physically cannot contain two ACTIVE (CONFIRMED) rows for the
-- same (show_id, seat_id). The loser of a race gets a unique-violation,
-- which the service layer translates into HTTP 409.

CREATE TABLE IF NOT EXISTS booking.bookings (
    id                 UUID PRIMARY KEY,
    booking_reference  VARCHAR(20)  NOT NULL UNIQUE,
    show_id            UUID         NOT NULL,
    event_id           UUID         NOT NULL,
    hold_id            UUID,
    customer_id        UUID         NOT NULL,
    customer_email     VARCHAR(255) NOT NULL,
    customer_name      VARCHAR(255) NOT NULL,
    status             VARCHAR(20)  NOT NULL CHECK (status IN ('CONFIRMED', 'CANCELLED')),
    total_amount       NUMERIC(12,2) NOT NULL DEFAULT 0,
    event_title        VARCHAR(255),
    venue_name         VARCHAR(255),
    show_starts_at     TIMESTAMPTZ,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    cancelled_at       TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_bookings_customer  ON booking.bookings (customer_id);
CREATE INDEX IF NOT EXISTS idx_bookings_show      ON booking.bookings (show_id);
CREATE INDEX IF NOT EXISTS idx_bookings_event     ON booking.bookings (event_id);
CREATE INDEX IF NOT EXISTS idx_bookings_status    ON booking.bookings (status);

CREATE TABLE IF NOT EXISTS booking.booking_seats (
    id             UUID PRIMARY KEY,
    booking_id     UUID NOT NULL REFERENCES booking.bookings (id) ON DELETE CASCADE,
    show_id        UUID NOT NULL,
    seat_id        UUID NOT NULL,
    seat_label     VARCHAR(20)  NOT NULL,
    row_label      VARCHAR(10),
    seat_number    INT,
    category_id    UUID,
    category_name  VARCHAR(100),
    price          NUMERIC(12,2) NOT NULL DEFAULT 0,
    active         BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX IF NOT EXISTS idx_booking_seats_booking ON booking.booking_seats (booking_id);
CREATE INDEX IF NOT EXISTS idx_booking_seats_show    ON booking.booking_seats (show_id);

-- THE concurrency guard: one active seat row per (show, seat).
CREATE UNIQUE INDEX IF NOT EXISTS uq_active_seat_per_show
    ON booking.booking_seats (show_id, seat_id)
    WHERE active;

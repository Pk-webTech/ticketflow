-- V1__init_seat_hold_schema.sql
-- seat-hold-service owns the `seat_hold` schema exclusively.
--
-- NOTE: this table is a durable AUDIT TRAIL only. The live/authoritative
-- state of "is this seat currently held, and for how much longer" lives in
-- Redis (key seat:hold:<showId>:<seatId>, with TTL). This table never drives
-- application logic — it exists for support/fraud-review/reconciliation.

CREATE TABLE IF NOT EXISTS seat_hold.hold_audit (
    hold_id       UUID PRIMARY KEY,
    show_id       UUID NOT NULL,
    customer_id   UUID NOT NULL,
    seat_ids      TEXT NOT NULL,
    status        VARCHAR(20) NOT NULL CHECK (status IN ('CREATED', 'RELEASED', 'EXPIRED', 'CONVERTED')),
    ttl_seconds   BIGINT NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at   TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_hold_audit_show_id ON seat_hold.hold_audit (show_id);
CREATE INDEX IF NOT EXISTS idx_hold_audit_customer_id ON seat_hold.hold_audit (customer_id);
CREATE INDEX IF NOT EXISTS idx_hold_audit_status ON seat_hold.hold_audit (status);

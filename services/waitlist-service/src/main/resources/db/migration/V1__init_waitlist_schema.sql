-- V1__init_waitlist_schema.sql
-- waitlist-service owns the `waitlist` schema exclusively.
--
-- QUEUE SEMANTICS
-- One logical FIFO queue per (show_id, category_id). Position is derived from
-- created_at rather than stored as an integer: storing positions would require
-- renumbering every downstream row on each leave/convert, which is both a
-- write amplification problem and a source of races. Ordering by created_at
-- (tie-broken by id) is stable, index-backed, and never needs rewriting.

CREATE TABLE IF NOT EXISTS waitlist.waitlist_entries (
    id             UUID PRIMARY KEY,
    show_id        UUID         NOT NULL,
    event_id       UUID,
    category_id    UUID         NOT NULL,
    category_name  VARCHAR(100),
    customer_id    UUID         NOT NULL,
    customer_email VARCHAR(255) NOT NULL,
    customer_name  VARCHAR(255),
    quantity       INT          NOT NULL DEFAULT 1 CHECK (quantity > 0),
    status         VARCHAR(20)  NOT NULL CHECK (status IN ('ACTIVE','OFFERED','CONVERTED','EXPIRED','CANCELLED')),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ
);

-- A customer may only sit in a given (show, category) queue once while live.
CREATE UNIQUE INDEX IF NOT EXISTS uq_waitlist_live_entry
    ON waitlist.waitlist_entries (show_id, category_id, customer_id)
    WHERE status IN ('ACTIVE', 'OFFERED');

-- Drives the "who is next?" query.
CREATE INDEX IF NOT EXISTS idx_waitlist_queue
    ON waitlist.waitlist_entries (show_id, category_id, status, created_at);

CREATE INDEX IF NOT EXISTS idx_waitlist_customer
    ON waitlist.waitlist_entries (customer_id);

CREATE TABLE IF NOT EXISTS waitlist.seat_offers (
    id                 UUID PRIMARY KEY,
    waitlist_entry_id  UUID NOT NULL REFERENCES waitlist.waitlist_entries (id) ON DELETE CASCADE,
    show_id            UUID NOT NULL,
    category_id        UUID NOT NULL,
    seat_ids           TEXT NOT NULL,   -- comma-separated seat UUIDs
    seat_labels        TEXT,
    -- High-entropy single-use token embedded in the emailed claim link.
    -- Unguessable so an offer cannot be stolen by URL enumeration.
    token              VARCHAR(64) NOT NULL UNIQUE,
    status             VARCHAR(20) NOT NULL CHECK (status IN ('PENDING','ACCEPTED','EXPIRED','DECLINED')),
    offered_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at         TIMESTAMPTZ NOT NULL,
    resolved_at        TIMESTAMPTZ
);

-- The sweeper scans exactly this: PENDING offers whose deadline has passed.
CREATE INDEX IF NOT EXISTS idx_offer_pending_expiry
    ON waitlist.seat_offers (status, expires_at);

CREATE INDEX IF NOT EXISTS idx_offer_entry ON waitlist.seat_offers (waitlist_entry_id);

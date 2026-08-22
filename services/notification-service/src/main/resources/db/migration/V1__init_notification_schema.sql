-- V1__init_notification_schema.sql
-- Durable log of every notification attempt.
--
-- Purpose is twofold: support ("did the customer actually get their ticket?")
-- and IDEMPOTENCY. RabbitMQ guarantees at-least-once delivery, so a redelivered
-- booking.confirmed message would email the same QR ticket twice without the
-- unique index below.

CREATE TABLE IF NOT EXISTS notification.notification_log (
    id             UUID PRIMARY KEY,
    type           VARCHAR(40)  NOT NULL,
    recipient      VARCHAR(255) NOT NULL,
    subject        VARCHAR(255),
    -- Natural key of the triggering domain event (booking reference, offer id).
    dedupe_key     VARCHAR(120) NOT NULL,
    status         VARCHAR(20)  NOT NULL CHECK (status IN ('SENT','FAILED','SKIPPED')),
    error_detail   TEXT,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- One successful notification per (type, dedupe_key). Redeliveries become no-ops.
CREATE UNIQUE INDEX IF NOT EXISTS uq_notification_dedupe
    ON notification.notification_log (type, dedupe_key)
    WHERE status = 'SENT';

CREATE INDEX IF NOT EXISTS idx_notification_recipient ON notification.notification_log (recipient);
CREATE INDEX IF NOT EXISTS idx_notification_created   ON notification.notification_log (created_at);

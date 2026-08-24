-- V1__init_venue_event_schema.sql
-- venue-event-service owns the `venue_event` schema exclusively.
--
-- uuid_generate_v4() is schema-qualified as public.uuid_generate_v4() because
-- this service's JDBC URL sets ?currentSchema=venue_event, which REPLACES the
-- connection's search_path rather than prepending to it. CREATE EXTENSION
-- installs uuid-ossp's functions into `public` by default, so an unqualified
-- call is invisible on this connection even though the function objectively
-- exists in the database — hence explicit qualification everywhere it's used.

CREATE TABLE IF NOT EXISTS venue_event.venues (
    id              UUID PRIMARY KEY DEFAULT public.uuid_generate_v4(),
    name            VARCHAR(200) NOT NULL,
    address         VARCHAR(255) NOT NULL,
    city            VARCHAR(100) NOT NULL,
    state           VARCHAR(100),
    postal_code     VARCHAR(20),
    total_capacity  INTEGER NOT NULL DEFAULT 0,
    created_by      UUID NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_venues_city ON venue_event.venues (city);

CREATE TABLE IF NOT EXISTS venue_event.seat_categories (
    id              UUID PRIMARY KEY DEFAULT public.uuid_generate_v4(),
    venue_id        UUID NOT NULL REFERENCES venue_event.venues (id) ON DELETE CASCADE,
    name            VARCHAR(50) NOT NULL,
    display_color   VARCHAR(7),
    default_price   NUMERIC(10, 2) NOT NULL CHECK (default_price >= 0),
    display_order   INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_seat_categories_venue_id ON venue_event.seat_categories (venue_id);

CREATE TABLE IF NOT EXISTS venue_event.seats (
    id              UUID PRIMARY KEY DEFAULT public.uuid_generate_v4(),
    venue_id        UUID NOT NULL REFERENCES venue_event.venues (id) ON DELETE CASCADE,
    category_id     UUID NOT NULL REFERENCES venue_event.seat_categories (id) ON DELETE RESTRICT,
    row_label       VARCHAR(10) NOT NULL,
    seat_number     INTEGER NOT NULL CHECK (seat_number > 0),
    section         VARCHAR(50),
    CONSTRAINT uq_seat_venue_row_number UNIQUE (venue_id, row_label, seat_number)
);

CREATE INDEX IF NOT EXISTS idx_seats_venue_id ON venue_event.seats (venue_id);
CREATE INDEX IF NOT EXISTS idx_seats_category_id ON venue_event.seats (category_id);

CREATE TABLE IF NOT EXISTS venue_event.events (
    id                  UUID PRIMARY KEY DEFAULT public.uuid_generate_v4(),
    organiser_id        UUID NOT NULL,
    title               VARCHAR(200) NOT NULL,
    type                VARCHAR(20) NOT NULL CHECK (type IN ('MOVIE', 'CONCERT')),
    description         TEXT,
    language            VARCHAR(50),
    duration_minutes    INTEGER,
    poster_url          VARCHAR(500),
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DELISTED')),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_events_organiser_id ON venue_event.events (organiser_id);
CREATE INDEX IF NOT EXISTS idx_events_status ON venue_event.events (status);

CREATE TABLE IF NOT EXISTS venue_event.shows (
    id              UUID PRIMARY KEY DEFAULT public.uuid_generate_v4(),
    event_id        UUID NOT NULL REFERENCES venue_event.events (id) ON DELETE CASCADE,
    venue_id        UUID NOT NULL REFERENCES venue_event.venues (id) ON DELETE RESTRICT,
    show_datetime   TIMESTAMPTZ NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED' CHECK (status IN ('SCHEDULED', 'CANCELLED', 'COMPLETED')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_shows_event_id ON venue_event.shows (event_id);
CREATE INDEX IF NOT EXISTS idx_shows_venue_id ON venue_event.shows (venue_id);
CREATE INDEX IF NOT EXISTS idx_shows_datetime ON venue_event.shows (show_datetime);
CREATE INDEX IF NOT EXISTS idx_shows_status ON venue_event.shows (status);

CREATE TABLE IF NOT EXISTS venue_event.show_pricing (
    id              UUID PRIMARY KEY DEFAULT public.uuid_generate_v4(),
    show_id         UUID NOT NULL REFERENCES venue_event.shows (id) ON DELETE CASCADE,
    category_id     UUID NOT NULL REFERENCES venue_event.seat_categories (id) ON DELETE RESTRICT,
    price           NUMERIC(10, 2) NOT NULL CHECK (price >= 0),
    CONSTRAINT uq_show_pricing_show_category UNIQUE (show_id, category_id)
);

CREATE INDEX IF NOT EXISTS idx_show_pricing_show_id ON venue_event.show_pricing (show_id);
-- ============================================================================
-- This migration reconciles several tables that had drifted out of sync with
-- the current JPA entity model — discovered by running the app against a real
-- (Testcontainers) Postgres instance instead of the H2 in-memory database that
-- every prior test used. H2 was running under Spring Boot's default
-- ddl-auto=create-drop (auto-detected for embedded databases when no explicit
-- ddl-auto is configured), which silently created whatever the entities
-- needed and masked the fact that these Flyway migrations were incomplete.
-- Against a real database with ddl-auto=validate/none, none of this worked.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- EVENT_SPACE_BOOKINGS: did not exist at all. This is the table backing
-- EventSpaceBookingService / ReservationService / all three payment Kafka
-- consumers (EmailConsumer, SettlementConsumer, TicketingConsumer) — none of
-- that code path could have worked against a real Flyway-migrated database.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS event_space_bookings (
    id UUID NOT NULL,
    version BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE,
    created_by UUID NOT NULL,
    updated_by UUID,
    event_id UUID NOT NULL,
    booking_level VARCHAR(255) NOT NULL,
    venue_id UUID,
    building_id UUID,
    floor_id UUID,
    hall_id UUID,
    stall_id UUID,
    status VARCHAR(255) NOT NULL,
    price NUMERIC(10, 2) NOT NULL,
    starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ends_at TIMESTAMP WITH TIME ZONE NOT NULL,
    reservation_id UUID,
    CONSTRAINT pk_event_space_bookings PRIMARY KEY (id),
    CONSTRAINT fk_esb_event FOREIGN KEY (event_id) REFERENCES events (id),
    CONSTRAINT fk_esb_venue FOREIGN KEY (venue_id) REFERENCES venues (id),
    CONSTRAINT fk_esb_building FOREIGN KEY (building_id) REFERENCES buildings (id),
    CONSTRAINT fk_esb_floor FOREIGN KEY (floor_id) REFERENCES floors (id),
    CONSTRAINT fk_esb_hall FOREIGN KEY (hall_id) REFERENCES halls (id),
    CONSTRAINT fk_esb_stall FOREIGN KEY (stall_id) REFERENCES stalls (id),
    CONSTRAINT fk_esb_reservation FOREIGN KEY (reservation_id) REFERENCES reservations (id),
    CONSTRAINT chk_esb_price CHECK (price >= 0),
    CONSTRAINT chk_esb_dates CHECK (ends_at > starts_at)
);

CREATE INDEX IF NOT EXISTS idx_esb_event ON event_space_bookings (event_id);
CREATE INDEX IF NOT EXISTS idx_esb_venue ON event_space_bookings (venue_id);
CREATE INDEX IF NOT EXISTS idx_esb_building ON event_space_bookings (building_id);
CREATE INDEX IF NOT EXISTS idx_esb_floor ON event_space_bookings (floor_id);
CREATE INDEX IF NOT EXISTS idx_esb_hall ON event_space_bookings (hall_id);
CREATE INDEX IF NOT EXISTS idx_esb_stall ON event_space_bookings (stall_id);
CREATE INDEX IF NOT EXISTS idx_esb_reservation ON event_space_bookings (reservation_id);
CREATE INDEX IF NOT EXISTS idx_esb_status ON event_space_bookings (status);

-- ----------------------------------------------------------------------------
-- EVENT_STALLS: the existing columns (stall_name_at_creation, hall_id_snapshot,
-- event_stall_x_coord, base_price, status, active, ...) belonged to an earlier
-- version of this entity and share no column names with the current EventStall
-- (active_for_event, availability_status, custom_x/y/width/height, custom_name,
-- event_price). Several of the stale columns are NOT NULL with no default, so
-- every insert via the current entity would fail outright. Since this table's
-- old shape doesn't correspond to any code path still in use, replace it
-- rather than try to keep both old and new columns live side by side.
-- ----------------------------------------------------------------------------
-- These columns are referenced by CHECK constraints from V1/V4, which must be
-- dropped first (both Postgres and H2 refuse to drop a column a constraint
-- still references).
ALTER TABLE event_stalls DROP CONSTRAINT IF EXISTS chk_event_stalls_base_price;
ALTER TABLE event_stalls DROP CONSTRAINT IF EXISTS chk_event_stalls_override_price;
ALTER TABLE event_stalls DROP CONSTRAINT IF EXISTS chk_event_stalls_width;
ALTER TABLE event_stalls DROP CONSTRAINT IF EXISTS chk_event_stalls_height;
ALTER TABLE event_stalls DROP CONSTRAINT IF EXISTS chk_event_stalls_coords;

ALTER TABLE event_stalls DROP COLUMN IF EXISTS stall_name_at_creation;
ALTER TABLE event_stalls DROP COLUMN IF EXISTS hall_id_snapshot;
ALTER TABLE event_stalls DROP COLUMN IF EXISTS hall_name_at_creation;
ALTER TABLE event_stalls DROP COLUMN IF EXISTS event_stall_x_coord;
ALTER TABLE event_stalls DROP COLUMN IF EXISTS event_stall_y_coord;
ALTER TABLE event_stalls DROP COLUMN IF EXISTS event_stall_width;
ALTER TABLE event_stalls DROP COLUMN IF EXISTS event_stall_height;
ALTER TABLE event_stalls DROP COLUMN IF EXISTS base_price;
ALTER TABLE event_stalls DROP COLUMN IF EXISTS manual_override_price;
ALTER TABLE event_stalls DROP COLUMN IF EXISTS status;
ALTER TABLE event_stalls DROP COLUMN IF EXISTS active;
ALTER TABLE event_stalls DROP COLUMN IF EXISTS square_footage;

ALTER TABLE event_stalls ADD COLUMN IF NOT EXISTS active_for_event BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE event_stalls ADD COLUMN IF NOT EXISTS availability_status VARCHAR(255) NOT NULL DEFAULT 'AVAILABLE';
ALTER TABLE event_stalls ALTER COLUMN active_for_event DROP DEFAULT;
ALTER TABLE event_stalls ALTER COLUMN availability_status DROP DEFAULT;
ALTER TABLE event_stalls ADD COLUMN IF NOT EXISTS custom_x INTEGER;
ALTER TABLE event_stalls ADD COLUMN IF NOT EXISTS custom_y INTEGER;
ALTER TABLE event_stalls ADD COLUMN IF NOT EXISTS custom_width INTEGER;
ALTER TABLE event_stalls ADD COLUMN IF NOT EXISTS custom_height INTEGER;
ALTER TABLE event_stalls ADD COLUMN IF NOT EXISTS custom_name VARCHAR(255);
ALTER TABLE event_stalls ADD COLUMN IF NOT EXISTS event_price NUMERIC(10, 2);

CREATE INDEX IF NOT EXISTS idx_event_stall_status ON event_stalls (availability_status);

-- ----------------------------------------------------------------------------
-- PAYMENTS: missing currency and payment_gateway — both NOT NULL on the
-- current Payment entity. Every payment insert/update was failing at the
-- database level even after PaymentMapper was fixed to populate these fields
-- in Java, since the columns to hold them didn't exist.
-- ----------------------------------------------------------------------------
ALTER TABLE payments ADD COLUMN IF NOT EXISTS currency VARCHAR(3) NOT NULL DEFAULT 'USD';
ALTER TABLE payments ALTER COLUMN currency DROP DEFAULT;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS payment_gateway VARCHAR(255) NOT NULL DEFAULT 'STRIPE';
ALTER TABLE payments ALTER COLUMN payment_gateway DROP DEFAULT;

-- ----------------------------------------------------------------------------
-- RESERVATIONS: reservation_created_by is a stale NOT NULL column with no
-- corresponding field on the current Reservation entity (which only has
-- user_id) — Hibernate never sets it, so every insert failed on this
-- constraint alone.
-- ----------------------------------------------------------------------------
ALTER TABLE reservations DROP COLUMN IF EXISTS reservation_created_by;

-- ----------------------------------------------------------------------------
-- BUILDINGS / FLOORS / HALLS / STALLS: all four gained a nullable dailyRate
-- field at the Java entity level with no migration ever adding the column.
-- ----------------------------------------------------------------------------
ALTER TABLE buildings ADD COLUMN IF NOT EXISTS daily_rate NUMERIC(10, 2);
ALTER TABLE floors ADD COLUMN IF NOT EXISTS daily_rate NUMERIC(10, 2);
ALTER TABLE halls ADD COLUMN IF NOT EXISTS daily_rate NUMERIC(10, 2);
ALTER TABLE stalls ADD COLUMN IF NOT EXISTS daily_rate NUMERIC(10, 2);

-- ----------------------------------------------------------------------------
-- REFRESH_TOKENS: missing family_id and revoked — both essential to the
-- refresh-token rotation / family-based breach-detection logic in
-- TokenManagementService (on token-reuse detection, the whole family is
-- revoked). Without these columns that entire security control is inert
-- against a real database.
-- ----------------------------------------------------------------------------
ALTER TABLE refresh_tokens ADD COLUMN IF NOT EXISTS family_id UUID;
ALTER TABLE refresh_tokens ADD COLUMN IF NOT EXISTS revoked BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE refresh_tokens ALTER COLUMN revoked DROP DEFAULT;
CREATE INDEX IF NOT EXISTS idx_rt_family ON refresh_tokens (family_id);

-- ----------------------------------------------------------------------------
-- VENUES: revenue_share_percentage exists on the entity (nullable) alongside
-- daily_rent_rate/rent_type, but was never added to the table.
-- ----------------------------------------------------------------------------
ALTER TABLE venues ADD COLUMN IF NOT EXISTS revenue_share_percentage NUMERIC(5, 2);
ALTER TABLE venues DROP CONSTRAINT IF EXISTS chk_venues_revenue_share;
ALTER TABLE venues ADD CONSTRAINT chk_venues_revenue_share CHECK (
    revenue_share_percentage IS NULL OR
    (revenue_share_percentage >= 0 AND revenue_share_percentage <= 100)
);

-- ----------------------------------------------------------------------------
-- VENUES.email / ORGANIZATIONS.contact_email: both columns are encrypted at
-- rest via PiiEncryptionConverter (AES-GCM, random IV per value) before ever
-- reaching the database — the DB only ever sees base64 ciphertext, never the
-- plaintext address. A CHECK constraint asserting the stored value contains
-- '@' can never pass against ciphertext (astronomically unlikely to contain
-- that literal byte pattern by chance), so every venue/organization insert
-- that provides an email was failing outright. Format validation for these
-- fields already happens at the application layer via Bean Validation's
-- @Email annotation on the plaintext value, before encryption — the DB-level
-- check is not just redundant but actively incompatible with encrypting the
-- column, so it must be dropped rather than corrected.
--
-- NOTE (flagging, not fixing here): organizations.registration_number is also
-- PiiEncryptionConverter-encrypted AND carries a UNIQUE constraint. Since
-- AES-GCM with a random IV produces different ciphertext for the same
-- plaintext on every encryption, that UNIQUE constraint — and the
-- application's existsByRegistrationNumberAndActiveTrue duplicate check —
-- can never actually detect a real duplicate registration number against a
-- real database. Resolving that requires a deliberate cryptographic design
-- choice (e.g. a separate deterministic blind-index/HMAC column used only for
-- lookup/uniqueness, keeping the main column's encryption random), which is a
-- security-architecture decision, not a schema patch — left for explicit
-- follow-up rather than decided here.
-- ----------------------------------------------------------------------------
ALTER TABLE venues DROP CONSTRAINT IF EXISTS chk_venues_email_format;
ALTER TABLE organizations DROP CONSTRAINT IF EXISTS chk_organizations_email_format;

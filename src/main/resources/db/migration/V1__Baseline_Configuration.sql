-- ============================================================================
-- Flyway Schema Migration Baseline: V1__Baseline_Configuration.sql
-- ============================================================================


-- ============================================================================
-- CORE PLATFORM & USERS
-- ============================================================================

CREATE TABLE IF NOT EXISTS users (
    id UUID NOT NULL,
    version BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE,
    created_by UUID NOT NULL,
    updated_by UUID,
    username VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    email_verified BOOLEAN NOT NULL,
    password VARCHAR(255) NOT NULL,
    system_role VARCHAR(255) NOT NULL,
    contact_number VARCHAR(255),
    address VARCHAR(255),
    active BOOLEAN NOT NULL,
    deleted_at TIMESTAMP WITH TIME ZONE,
    deleted_by UUID,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT chk_users_username_not_empty CHECK (TRIM(username) <> ''),
    CONSTRAINT chk_users_email_format CHECK (email LIKE '%@%')
);

CREATE INDEX IF NOT EXISTS idx_user_active ON users (active);
CREATE INDEX IF NOT EXISTS idx_user_system_role ON users (system_role);

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id UUID NOT NULL,
    version BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE,
    created_by UUID NOT NULL,
    updated_by UUID,
    token VARCHAR(512) NOT NULL,
    user_id UUID NOT NULL,
    expiry_date TIMESTAMP WITH TIME ZONE NOT NULL,
    ip_address VARCHAR(45),
    device_info VARCHAR(512),
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT uk_refresh_tokens_token UNIQUE (token)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_refresh_token_string ON refresh_tokens (token);
CREATE INDEX IF NOT EXISTS idx_refresh_token_user ON refresh_tokens (user_id);

-- ============================================================================
-- ORGANIZATIONS & MEMBERSHIP
-- ============================================================================

CREATE TABLE IF NOT EXISTS organizations (
    id UUID NOT NULL,
    version BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE,
    created_by UUID NOT NULL,
    updated_by UUID,
    name VARCHAR(255) NOT NULL,
    contact_number VARCHAR(255),
    contact_email VARCHAR(255),
    billing_address VARCHAR(255),
    active BOOLEAN NOT NULL,
    deleted_at TIMESTAMP WITH TIME ZONE,
    deleted_by UUID,
    CONSTRAINT pk_organizations PRIMARY KEY (id),
    CONSTRAINT uk_organizations_name UNIQUE (name),
    CONSTRAINT chk_organizations_name_not_empty CHECK (TRIM(name) <> ''),
    CONSTRAINT chk_organizations_email_format CHECK (contact_email IS NULL OR contact_email LIKE '%@%')
);

CREATE INDEX IF NOT EXISTS idx_organization_active ON organizations (active);

CREATE TABLE IF NOT EXISTS organization_capabilities (
    organization_id UUID NOT NULL,
    capability VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS organization_members (
    id UUID NOT NULL,
    version BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE,
    created_by UUID NOT NULL,
    updated_by UUID,
    user_id UUID NOT NULL,
    organization_id UUID NOT NULL,
    role VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL,
    CONSTRAINT pk_organization_members PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_org_member_user ON organization_members (user_id);
CREATE INDEX IF NOT EXISTS idx_org_member_org ON organization_members (organization_id);

CREATE TABLE IF NOT EXISTS organization_invites (
    id UUID NOT NULL,
    version BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE,
    created_by UUID NOT NULL,
    updated_by UUID,
    organization_id UUID NOT NULL,
    email VARCHAR(255) NOT NULL,
    assigned_role VARCHAR(255) NOT NULL,
    token VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used BOOLEAN NOT NULL,
    CONSTRAINT pk_organization_invites PRIMARY KEY (id),
    CONSTRAINT uk_organization_invites_token UNIQUE (token),
    CONSTRAINT chk_org_invites_email_format CHECK (email LIKE '%@%')
);

CREATE INDEX IF NOT EXISTS idx_org_invite_token ON organization_invites (token);
CREATE INDEX IF NOT EXISTS idx_org_invite_email ON organization_invites (email);

-- ============================================================================
-- VENUES & LAYOUT ARCHITECTURE
-- ============================================================================

CREATE TABLE IF NOT EXISTS venues (
    id UUID NOT NULL,
    version BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE,
    created_by UUID NOT NULL,
    updated_by UUID,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    address VARCHAR(255) NOT NULL,
    city VARCHAR(255) NOT NULL,
    country VARCHAR(255) NOT NULL,
    postal_code VARCHAR(255),
    contact_number VARCHAR(255),
    email VARCHAR(255),
    website VARCHAR(255),
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    google_place_id VARCHAR(255),
    map_image_url VARCHAR(255),
    total_square_footage DOUBLE PRECISION,
    parking_available BOOLEAN,
    food_court_available BOOLEAN,
    active BOOLEAN NOT NULL,
    daily_rent_rate NUMERIC(10, 2),
    rent_type VARCHAR(255),
    venue_blueprint_image_url VARCHAR(255),
    owner_organization_id UUID NOT NULL,
    CONSTRAINT pk_venues PRIMARY KEY (id),
    CONSTRAINT uk_venues_name UNIQUE (name),
    CONSTRAINT chk_venues_name_not_empty CHECK (TRIM(name) <> ''),
    CONSTRAINT chk_venues_email_format CHECK (email IS NULL OR email LIKE '%@%'),
    CONSTRAINT chk_venues_daily_rent_rate CHECK (daily_rent_rate IS NULL OR daily_rent_rate >= 0),
    CONSTRAINT chk_venues_square_footage CHECK (total_square_footage IS NULL OR total_square_footage >= 0),
    CONSTRAINT chk_venues_latitude CHECK (latitude IS NULL OR (latitude >= -90.0 AND latitude <= 90.0)),
    CONSTRAINT chk_venues_longitude CHECK (longitude IS NULL OR (longitude >= -180.0 AND longitude <= 180.0))
);

CREATE INDEX IF NOT EXISTS idx_venue_name ON venues (name);

CREATE TABLE IF NOT EXISTS venue_partners (
    venue_id UUID NOT NULL,
    organization_id UUID NOT NULL
);

CREATE TABLE IF NOT EXISTS buildings (
    id UUID NOT NULL,
    version BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE,
    created_by UUID NOT NULL,
    updated_by UUID,
    name VARCHAR(255) NOT NULL,
    building_x_coord INTEGER NOT NULL,
    building_y_coord INTEGER NOT NULL,
    building_width INTEGER NOT NULL,
    building_height INTEGER NOT NULL,
    square_footage DOUBLE PRECISION,
    active BOOLEAN NOT NULL,
    type VARCHAR(255) NOT NULL,
    venue_id UUID NOT NULL,
    CONSTRAINT pk_buildings PRIMARY KEY (id),
    CONSTRAINT uk_building_venue_name UNIQUE (venue_id, name),
    CONSTRAINT chk_buildings_name_not_empty CHECK (TRIM(name) <> ''),
    CONSTRAINT chk_buildings_width CHECK (building_width > 0),
    CONSTRAINT chk_buildings_height CHECK (building_height > 0),
    CONSTRAINT chk_buildings_coords CHECK (building_x_coord >= 0 AND building_y_coord >= 0),
    CONSTRAINT chk_buildings_square_footage CHECK (square_footage IS NULL OR square_footage >= 0)
);

CREATE INDEX IF NOT EXISTS idx_building_venue ON buildings (venue_id);

CREATE TABLE IF NOT EXISTS floors (
    id UUID NOT NULL,
    version BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE,
    created_by UUID NOT NULL,
    updated_by UUID,
    level_name VARCHAR(255) NOT NULL,
    level_number INTEGER NOT NULL,
    building_id UUID NOT NULL,
    active BOOLEAN NOT NULL,
    CONSTRAINT pk_floors PRIMARY KEY (id),
    CONSTRAINT uk_floor_building_level UNIQUE (building_id, level_number)
);

CREATE INDEX IF NOT EXISTS idx_floor_building ON floors (building_id);

CREATE TABLE IF NOT EXISTS halls (
    id UUID NOT NULL,
    version BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE,
    created_by UUID NOT NULL,
    updated_by UUID,
    name VARCHAR(255) NOT NULL,
    space_category VARCHAR(255) NOT NULL,
    hall_type VARCHAR(255) NOT NULL,
    floor_id UUID NOT NULL,
    blueprint_image_url VARCHAR(255),
    hall_x_coord INTEGER NOT NULL,
    hall_y_coord INTEGER NOT NULL,
    hall_width INTEGER NOT NULL,
    hall_height INTEGER NOT NULL,
    square_footage DOUBLE PRECISION,
    active BOOLEAN NOT NULL,
    max_stalls INTEGER,
    wifi_available BOOLEAN,
    air_conditioned BOOLEAN,
    CONSTRAINT pk_halls PRIMARY KEY (id),
    CONSTRAINT uk_hall_floor_name UNIQUE (floor_id, name),
    CONSTRAINT chk_halls_name_not_empty CHECK (TRIM(name) <> ''),
    CONSTRAINT chk_halls_width CHECK (hall_width > 0),
    CONSTRAINT chk_halls_height CHECK (hall_height > 0),
    CONSTRAINT chk_halls_coords CHECK (hall_x_coord >= 0 AND hall_y_coord >= 0),
    CONSTRAINT chk_halls_square_footage CHECK (square_footage IS NULL OR square_footage >= 0),
    CONSTRAINT chk_halls_max_stalls CHECK (max_stalls IS NULL OR max_stalls >= 0)
);

CREATE INDEX IF NOT EXISTS idx_hall_floor ON halls (floor_id);

CREATE TABLE IF NOT EXISTS stalls (
    id UUID NOT NULL,
    version BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE,
    created_by UUID NOT NULL,
    updated_by UUID,
    name VARCHAR(255) NOT NULL,
    hall_id UUID NOT NULL,
    stall_type VARCHAR(255),
    stall_x_coord INTEGER NOT NULL,
    stall_y_coord INTEGER NOT NULL,
    stall_width INTEGER NOT NULL,
    stall_height INTEGER NOT NULL,
    square_footage DOUBLE PRECISION,
    active BOOLEAN NOT NULL,
    CONSTRAINT pk_stalls PRIMARY KEY (id),
    CONSTRAINT uk_stall_name_hall UNIQUE (hall_id, name),
    CONSTRAINT chk_stalls_name_not_empty CHECK (TRIM(name) <> ''),
    CONSTRAINT chk_stalls_width CHECK (stall_width > 0),
    CONSTRAINT chk_stalls_height CHECK (stall_height > 0),
    CONSTRAINT chk_stalls_coords CHECK (stall_x_coord >= 0 AND stall_y_coord >= 0),
    CONSTRAINT chk_stalls_square_footage CHECK (square_footage IS NULL OR square_footage >= 0)
);

CREATE INDEX IF NOT EXISTS idx_stall_hall ON stalls (hall_id);

CREATE TABLE IF NOT EXISTS layout_markers (
    id UUID NOT NULL,
    version BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE,
    created_by UUID NOT NULL,
    updated_by UUID,
    venue_id UUID,
    building_id UUID,
    hall_id UUID,
    type VARCHAR(255) NOT NULL,
    marker_x INTEGER NOT NULL,
    marker_y INTEGER NOT NULL,
    marker_width INTEGER NOT NULL,
    marker_height INTEGER NOT NULL,
    label VARCHAR(255) NOT NULL,
    primary_marker BOOLEAN,
    active BOOLEAN NOT NULL,
    CONSTRAINT pk_layout_markers PRIMARY KEY (id),
    CONSTRAINT chk_markers_width CHECK (marker_width > 0),
    CONSTRAINT chk_markers_height CHECK (marker_height > 0),
    CONSTRAINT chk_markers_coords CHECK (marker_x >= 0 AND marker_y >= 0)
);

-- ============================================================================
-- EVENTS & STALLS
-- ============================================================================

CREATE TABLE IF NOT EXISTS genres (
    id UUID NOT NULL,
    version BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE,
    created_by UUID NOT NULL,
    updated_by UUID,
    name VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL,
    color VARCHAR(255),
    CONSTRAINT pk_genres PRIMARY KEY (id),
    CONSTRAINT uk_genres_name UNIQUE (name),
    CONSTRAINT chk_genres_name_not_empty CHECK (TRIM(name) <> '')
);

CREATE TABLE IF NOT EXISTS events (
    id UUID NOT NULL,
    version BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE,
    created_by UUID NOT NULL,
    updated_by UUID,
    name VARCHAR(255) NOT NULL,
    start_date TIMESTAMP WITH TIME ZONE NOT NULL,
    end_date TIMESTAMP WITH TIME ZONE NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    venue_id UUID NOT NULL,
    organizer_id UUID NOT NULL,
    active BOOLEAN NOT NULL,
    CONSTRAINT pk_events PRIMARY KEY (id),
    CONSTRAINT uk_events_name UNIQUE (name),
    CONSTRAINT chk_events_name_not_empty CHECK (TRIM(name) <> ''),
    CONSTRAINT chk_events_dates CHECK (end_date > start_date)
);

CREATE INDEX IF NOT EXISTS idx_reservation_book_fair ON events (name);
CREATE INDEX IF NOT EXISTS idx_venue_book_fair ON events (venue_id);

CREATE TABLE IF NOT EXISTS event_partners (
    event_id UUID NOT NULL,
    organization_id UUID NOT NULL
);

CREATE TABLE IF NOT EXISTS event_stalls (
    id UUID NOT NULL,
    version BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE,
    created_by UUID NOT NULL,
    updated_by UUID,
    event_id UUID NOT NULL,
    stall_id UUID NOT NULL,
    stall_name_at_creation VARCHAR(255),
    hall_id_snapshot UUID,
    hall_name_at_creation VARCHAR(255),
    event_stall_x_coord INTEGER NOT NULL,
    event_stall_y_coord INTEGER NOT NULL,
    event_stall_width INTEGER NOT NULL,
    event_stall_height INTEGER NOT NULL,
    base_price NUMERIC(10, 2) NOT NULL,
    manual_override_price NUMERIC(10, 2),
    status VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL,
    CONSTRAINT pk_event_stalls PRIMARY KEY (id),
    CONSTRAINT uk_event_stall UNIQUE (event_id, stall_id),
    CONSTRAINT chk_event_stalls_base_price CHECK (base_price >= 0),
    CONSTRAINT chk_event_stalls_override_price CHECK (manual_override_price IS NULL OR manual_override_price >= 0),
    CONSTRAINT chk_event_stalls_width CHECK (event_stall_width > 0),
    CONSTRAINT chk_event_stalls_height CHECK (event_stall_height > 0),
    CONSTRAINT chk_event_stalls_coords CHECK (event_stall_x_coord >= 0 AND event_stall_y_coord >= 0)
);

CREATE INDEX IF NOT EXISTS idx_es_event ON event_stalls (event_id);
CREATE INDEX IF NOT EXISTS idx_es_stall ON event_stalls (stall_id);
CREATE INDEX IF NOT EXISTS idx_es_status ON event_stalls (status);

CREATE TABLE IF NOT EXISTS event_settlements (
    id UUID NOT NULL,
    version BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE,
    created_by UUID NOT NULL,
    updated_by UUID,
    event_id UUID NOT NULL,
    organizer_id UUID NOT NULL,
    venue_owner_id UUID NOT NULL,
    snapshotted_daily_rent_rate NUMERIC(10, 2) NOT NULL,
    snapshotted_rent_type VARCHAR(255) NOT NULL,
    total_rent_owed NUMERIC(10, 2) NOT NULL,
    amount_paid_to_owner NUMERIC(10, 2) NOT NULL,
    remaining_balance NUMERIC(10, 2) NOT NULL,
    organizer_profit NUMERIC(10, 2) NOT NULL,
    status VARCHAR(255) NOT NULL,
    CONSTRAINT pk_event_settlements PRIMARY KEY (id),
    CONSTRAINT uk_event_settlements_event UNIQUE (event_id),
    CONSTRAINT chk_settlements_rent_rate CHECK (snapshotted_daily_rent_rate >= 0),
    CONSTRAINT chk_settlements_total_rent CHECK (total_rent_owed >= 0),
    CONSTRAINT chk_settlements_amount_paid CHECK (amount_paid_to_owner >= 0)
);

CREATE TABLE IF NOT EXISTS pricing_rules (
    id UUID NOT NULL,
    version BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE,
    created_by UUID NOT NULL,
    updated_by UUID,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(255) NOT NULL,
    condition_type VARCHAR(255) NOT NULL,
    condition_value VARCHAR(255) NOT NULL,
    multiplier NUMERIC(38, 2) NOT NULL,
    active BOOLEAN NOT NULL,
    priority INTEGER,
    CONSTRAINT pk_pricing_rules PRIMARY KEY (id),
    CONSTRAINT chk_pricing_rules_name_not_empty CHECK (TRIM(name) <> ''),
    CONSTRAINT chk_pricing_rules_multiplier CHECK (multiplier >= 0),
    CONSTRAINT chk_pricing_rules_priority CHECK (priority IS NULL OR priority >= 0)
);

-- ============================================================================
-- RESERVATIONS & FINANCIAL TRANSACTIONS
-- ============================================================================

CREATE TABLE IF NOT EXISTS reservations (
    id UUID NOT NULL,
    version BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE,
    created_by UUID NOT NULL,
    updated_by UUID,
    user_id UUID NOT NULL,
    organization_id UUID NOT NULL,
    reservation_created_by UUID NOT NULL,
    event_id UUID NOT NULL,
    reservation_start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(255) NOT NULL,
    genre_id UUID,
    total_price NUMERIC(10, 2) NOT NULL,
    qr_code_payload TEXT,
    CONSTRAINT pk_reservations PRIMARY KEY (id),
    CONSTRAINT chk_reservations_total_price CHECK (total_price >= 0),
    CONSTRAINT chk_reservations_times CHECK (expires_at > reservation_start_time)
);

CREATE INDEX IF NOT EXISTS idx_reservation_user ON reservations (user_id);
CREATE INDEX IF NOT EXISTS idx_reservation_event ON reservations (event_id);
CREATE INDEX IF NOT EXISTS idx_reservation_expires ON reservations (expires_at);
CREATE INDEX IF NOT EXISTS idx_reservation_status ON reservations (status);

CREATE TABLE IF NOT EXISTS reservation_stalls (
    id UUID NOT NULL,
    version BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE,
    created_by UUID NOT NULL,
    updated_by UUID,
    reservation_id UUID NOT NULL,
    event_stall_id UUID NOT NULL,
    price_at_booking NUMERIC(10, 2) NOT NULL,
    CONSTRAINT pk_reservation_stalls PRIMARY KEY (id),
    CONSTRAINT uk_reservation_stall UNIQUE (reservation_id, event_stall_id),
    CONSTRAINT chk_res_stalls_price CHECK (price_at_booking >= 0)
);

CREATE INDEX IF NOT EXISTS idx_rs_reservation ON reservation_stalls (reservation_id);
CREATE INDEX IF NOT EXISTS idx_rs_event_stall ON reservation_stalls (event_stall_id);

CREATE TABLE IF NOT EXISTS payments (
    id UUID NOT NULL,
    version BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE,
    created_by UUID NOT NULL,
    updated_by UUID,
    reservation_id UUID NOT NULL,
    transaction_id VARCHAR(255),
    amount NUMERIC(10, 2) NOT NULL,
    status VARCHAR(255) NOT NULL,
    CONSTRAINT pk_payments PRIMARY KEY (id),
    CONSTRAINT uk_payments_transaction_id UNIQUE (transaction_id),
    CONSTRAINT chk_payments_amount CHECK (amount >= 0)
);

CREATE INDEX IF NOT EXISTS idx_payment_reservation ON payments (reservation_id);

CREATE TABLE IF NOT EXISTS transaction_histories (
    id UUID NOT NULL,
    version BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE,
    created_by UUID NOT NULL,
    updated_by UUID,
    event_id UUID NOT NULL,
    amount NUMERIC(10, 2) NOT NULL,
    source_role VARCHAR(255) NOT NULL,
    destination_role VARCHAR(255) NOT NULL,
    description TEXT,
    reservation_id UUID,
    CONSTRAINT pk_transaction_histories PRIMARY KEY (id),
    CONSTRAINT chk_transaction_histories_amount CHECK (amount >= 0)
);

-- ============================================================================
-- FOREIGN KEY CONSTRAINTS
-- ============================================================================

ALTER TABLE IF EXISTS refresh_tokens
    ADD CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id);

ALTER TABLE IF EXISTS organization_capabilities
    ADD CONSTRAINT fk_org_capabilities_org FOREIGN KEY (organization_id) REFERENCES organizations (id);

ALTER TABLE IF EXISTS organization_members
    ADD CONSTRAINT fk_org_members_user FOREIGN KEY (user_id) REFERENCES users (id);
ALTER TABLE IF EXISTS organization_members
    ADD CONSTRAINT fk_org_members_org FOREIGN KEY (organization_id) REFERENCES organizations (id);

ALTER TABLE IF EXISTS venues
    ADD CONSTRAINT fk_venues_owner_org FOREIGN KEY (owner_organization_id) REFERENCES organizations (id);

ALTER TABLE IF EXISTS venue_partners
    ADD CONSTRAINT fk_venue_partners_venue FOREIGN KEY (venue_id) REFERENCES venues (id);
ALTER TABLE IF EXISTS venue_partners
    ADD CONSTRAINT fk_venue_partners_org FOREIGN KEY (organization_id) REFERENCES organizations (id);

ALTER TABLE IF EXISTS buildings
    ADD CONSTRAINT fk_buildings_venue FOREIGN KEY (venue_id) REFERENCES venues (id);

ALTER TABLE IF EXISTS floors
    ADD CONSTRAINT fk_floors_building FOREIGN KEY (building_id) REFERENCES buildings (id);

ALTER TABLE IF EXISTS halls
    ADD CONSTRAINT fk_halls_floor FOREIGN KEY (floor_id) REFERENCES floors (id);

ALTER TABLE IF EXISTS stalls
    ADD CONSTRAINT fk_stalls_hall FOREIGN KEY (hall_id) REFERENCES halls (id);

ALTER TABLE IF EXISTS layout_markers
    ADD CONSTRAINT fk_layout_markers_venue FOREIGN KEY (venue_id) REFERENCES venues (id);
ALTER TABLE IF EXISTS layout_markers
    ADD CONSTRAINT fk_layout_markers_building FOREIGN KEY (building_id) REFERENCES buildings (id);
ALTER TABLE IF EXISTS layout_markers
    ADD CONSTRAINT fk_layout_markers_hall FOREIGN KEY (hall_id) REFERENCES halls (id);

ALTER TABLE IF EXISTS events
    ADD CONSTRAINT fk_events_venue FOREIGN KEY (venue_id) REFERENCES venues (id);
ALTER TABLE IF EXISTS events
    ADD CONSTRAINT fk_events_organizer FOREIGN KEY (organizer_id) REFERENCES organizations (id);

ALTER TABLE IF EXISTS event_partners
    ADD CONSTRAINT fk_event_partners_event FOREIGN KEY (event_id) REFERENCES events (id);
ALTER TABLE IF EXISTS event_partners
    ADD CONSTRAINT fk_event_partners_org FOREIGN KEY (organization_id) REFERENCES organizations (id);

ALTER TABLE IF EXISTS event_stalls
    ADD CONSTRAINT fk_event_stalls_event FOREIGN KEY (event_id) REFERENCES events (id);
ALTER TABLE IF EXISTS event_stalls
    ADD CONSTRAINT fk_event_stalls_stall FOREIGN KEY (stall_id) REFERENCES stalls (id);

ALTER TABLE IF EXISTS event_settlements
    ADD CONSTRAINT fk_event_settlements_event FOREIGN KEY (event_id) REFERENCES events (id);
ALTER TABLE IF EXISTS event_settlements
    ADD CONSTRAINT fk_event_settlements_organizer FOREIGN KEY (organizer_id) REFERENCES organizations (id);
ALTER TABLE IF EXISTS event_settlements
    ADD CONSTRAINT fk_event_settlements_venue_owner FOREIGN KEY (venue_owner_id) REFERENCES organizations (id);

ALTER TABLE IF EXISTS reservations
    ADD CONSTRAINT fk_reservations_user FOREIGN KEY (user_id) REFERENCES users (id);
ALTER TABLE IF EXISTS reservations
    ADD CONSTRAINT fk_reservations_org FOREIGN KEY (organization_id) REFERENCES organizations (id);
ALTER TABLE IF EXISTS reservations
    ADD CONSTRAINT fk_reservations_created_by FOREIGN KEY (reservation_created_by) REFERENCES users (id);
ALTER TABLE IF EXISTS reservations
    ADD CONSTRAINT fk_reservations_event FOREIGN KEY (event_id) REFERENCES events (id);
ALTER TABLE IF EXISTS reservations
    ADD CONSTRAINT fk_reservations_genre FOREIGN KEY (genre_id) REFERENCES genres (id);

ALTER TABLE IF EXISTS reservation_stalls
    ADD CONSTRAINT fk_res_stalls_reservation FOREIGN KEY (reservation_id) REFERENCES reservations (id);
ALTER TABLE IF EXISTS reservation_stalls
    ADD CONSTRAINT fk_res_stalls_event_stall FOREIGN KEY (event_stall_id) REFERENCES event_stalls (id);

ALTER TABLE IF EXISTS payments
    ADD CONSTRAINT fk_payments_reservation FOREIGN KEY (reservation_id) REFERENCES reservations (id);

ALTER TABLE IF EXISTS transaction_histories
    ADD CONSTRAINT fk_transaction_histories_event FOREIGN KEY (event_id) REFERENCES events (id);
ALTER TABLE IF EXISTS transaction_histories
    ADD CONSTRAINT fk_transaction_histories_reservation FOREIGN KEY (reservation_id) REFERENCES reservations (id);

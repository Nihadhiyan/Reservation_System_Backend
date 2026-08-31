-- ----------------------------------------------------------------------------
-- RESERVATIONS: Remove obsolete reservation_created_by column & FK
-- ----------------------------------------------------------------------------
ALTER TABLE IF EXISTS reservations
    DROP CONSTRAINT IF EXISTS fk_reservations_created_by;

ALTER TABLE IF EXISTS reservations
    DROP COLUMN IF EXISTS reservation_created_by;

-- ----------------------------------------------------------------------------
-- BUILDINGS & STALLS: Enforce non-nullable square_footage
-- ----------------------------------------------------------------------------
UPDATE buildings SET square_footage = 0.0 WHERE square_footage IS NULL;
ALTER TABLE buildings ALTER COLUMN square_footage SET DEFAULT 0.0;
ALTER TABLE buildings ALTER COLUMN square_footage SET NOT NULL;

UPDATE stalls SET square_footage = 0.0 WHERE square_footage IS NULL;
ALTER TABLE stalls ALTER COLUMN square_footage SET DEFAULT 0.0;
ALTER TABLE stalls ALTER COLUMN square_footage SET NOT NULL;

-- ----------------------------------------------------------------------------
-- HALLS: Expand blueprint_image_url length
-- ----------------------------------------------------------------------------
ALTER TABLE halls ALTER COLUMN blueprint_image_url SET DATA TYPE VARCHAR(2048);

-- ----------------------------------------------------------------------------
-- EVENT_STALLS: Add square_footage column & constraint
-- ----------------------------------------------------------------------------
ALTER TABLE event_stalls ADD COLUMN IF NOT EXISTS square_footage DOUBLE PRECISION;
ALTER TABLE event_stalls DROP CONSTRAINT IF EXISTS chk_event_stalls_square_footage;
ALTER TABLE event_stalls ADD CONSTRAINT chk_event_stalls_square_footage CHECK (square_footage IS NULL OR square_footage >= 0);

-- ----------------------------------------------------------------------------
-- RESERVATION_STALLS: Add applied_pricing_rules_snapshot for audit logs
-- ----------------------------------------------------------------------------
ALTER TABLE reservation_stalls ADD COLUMN IF NOT EXISTS applied_pricing_rules_snapshot TEXT;

-- ----------------------------------------------------------------------------
-- VENUES: Enforce non-nullable total_square_footage and email
-- ----------------------------------------------------------------------------
UPDATE venues SET total_square_footage = 0.0 WHERE total_square_footage IS NULL;
ALTER TABLE venues ALTER COLUMN total_square_footage SET DEFAULT 0.0;
ALTER TABLE venues ALTER COLUMN total_square_footage SET NOT NULL;

UPDATE venues SET email = 'contact@venue.com' WHERE email IS NULL;
ALTER TABLE venues ALTER COLUMN email SET NOT NULL;

-- ----------------------------------------------------------------------------
-- PAYMENTS: Add currency & payment_gateway columns
-- ----------------------------------------------------------------------------
ALTER TABLE payments ADD COLUMN IF NOT EXISTS currency VARCHAR(3) DEFAULT 'USD' NOT NULL;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS payment_gateway VARCHAR(255) DEFAULT 'STRIPE' NOT NULL;
ALTER TABLE payments ALTER COLUMN currency DROP DEFAULT;
ALTER TABLE payments ALTER COLUMN payment_gateway DROP DEFAULT;

-- ----------------------------------------------------------------------------
-- TRANSACTION_HISTORIES: Add currency, payment_id FK, and performance indexes
-- ----------------------------------------------------------------------------
ALTER TABLE transaction_histories ADD COLUMN IF NOT EXISTS currency VARCHAR(3) DEFAULT 'USD' NOT NULL;
ALTER TABLE transaction_histories ALTER COLUMN currency DROP DEFAULT;

ALTER TABLE transaction_histories ADD COLUMN IF NOT EXISTS payment_id UUID;
ALTER TABLE IF EXISTS transaction_histories
    ADD CONSTRAINT fk_transaction_histories_payment FOREIGN KEY (payment_id) REFERENCES payments (id);

CREATE INDEX IF NOT EXISTS idx_tx_event ON transaction_histories (event_id);
CREATE INDEX IF NOT EXISTS idx_tx_reservation ON transaction_histories (reservation_id);
CREATE INDEX IF NOT EXISTS idx_tx_payment ON transaction_histories (payment_id);
CREATE INDEX IF NOT EXISTS idx_tx_roles ON transaction_histories (source_role, destination_role);

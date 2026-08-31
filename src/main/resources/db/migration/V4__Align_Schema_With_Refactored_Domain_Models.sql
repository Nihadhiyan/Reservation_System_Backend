
-- ----------------------------------------------------------------------------
-- EVENT_SETTLEMENTS: Add currency and snapshotted_revenue_share_percentage
-- ----------------------------------------------------------------------------
ALTER TABLE event_settlements ADD COLUMN IF NOT EXISTS currency VARCHAR(3) DEFAULT 'USD' NOT NULL;
ALTER TABLE event_settlements ALTER COLUMN currency DROP DEFAULT;

ALTER TABLE event_settlements ADD COLUMN IF NOT EXISTS snapshotted_revenue_share_percentage NUMERIC(5, 2);

ALTER TABLE event_settlements DROP CONSTRAINT IF EXISTS chk_settlements_rev_share;
ALTER TABLE event_settlements
    ADD CONSTRAINT chk_settlements_rev_share CHECK (
        snapshotted_revenue_share_percentage IS NULL OR 
        (snapshotted_revenue_share_percentage >= 0 AND snapshotted_revenue_share_percentage <= 100)
    );

-- ----------------------------------------------------------------------------
-- EVENT_PARTNERS & VENUE_PARTNERS: Enforce Composite Primary Keys
-- ----------------------------------------------------------------------------
ALTER TABLE event_partners DROP CONSTRAINT IF EXISTS pk_event_partners;
ALTER TABLE event_partners ADD CONSTRAINT pk_event_partners PRIMARY KEY (event_id, organization_id);

ALTER TABLE venue_partners DROP CONSTRAINT IF EXISTS pk_venue_partners;
ALTER TABLE venue_partners ADD CONSTRAINT pk_venue_partners PRIMARY KEY (venue_id, organization_id);

-- ----------------------------------------------------------------------------
-- EVENT_STALLS: Enforce Non-Negative Base Price Constraint
-- ----------------------------------------------------------------------------
ALTER TABLE event_stalls DROP CONSTRAINT IF EXISTS chk_event_stalls_base_price;
ALTER TABLE event_stalls ADD CONSTRAINT chk_event_stalls_base_price CHECK (base_price >= 0);

ALTER TABLE event_stalls DROP CONSTRAINT IF EXISTS chk_event_stalls_override_price;
ALTER TABLE event_stalls ADD CONSTRAINT chk_event_stalls_override_price CHECK (
    manual_override_price IS NULL OR manual_override_price >= 0
);

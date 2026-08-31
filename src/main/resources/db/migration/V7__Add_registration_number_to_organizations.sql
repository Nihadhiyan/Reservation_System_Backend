-- Add the missing registration_number column to the organizations table
ALTER TABLE organizations ADD COLUMN IF NOT EXISTS registration_number VARCHAR(255);

-- Since it is marked nullable=false, we need to populate existing rows
-- In a real scenario, these would need to be backfilled with properly encrypted data.
-- For now, we set a dummy value just to satisfy the constraint for any existing dev data.
UPDATE organizations SET registration_number = 'UNKNOWN-' || id::text WHERE registration_number IS NULL;

-- Now enforce the constraints that match the JPA entity
ALTER TABLE organizations ALTER COLUMN registration_number SET NOT NULL;
ALTER TABLE organizations ADD CONSTRAINT uk_organizations_registration_number UNIQUE (registration_number);

-- And the index requested by @Index
CREATE INDEX IF NOT EXISTS idx_registration_number ON organizations (registration_number);

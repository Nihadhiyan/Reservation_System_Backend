-- Add the missing verified column to the organizations table.
-- Organization.java maps a `verified` field (nullable = false) but no prior
-- migration ever created this column — Hibernate's schema *validation*
-- (ddl-auto=validate) caught the drift at startup rather than silently
-- creating it, which is exactly what that setting is for.
ALTER TABLE organizations ADD COLUMN IF NOT EXISTS verified BOOLEAN;

-- Backfill existing rows before enforcing NOT NULL, same as V7's pattern.
-- Defaulting to false (unverified) is the safe choice for any pre-existing
-- organizations — nothing should be silently treated as verified.
UPDATE organizations SET verified = false WHERE verified IS NULL;

ALTER TABLE organizations ALTER COLUMN verified SET NOT NULL;
ALTER TABLE organizations ALTER COLUMN verified SET DEFAULT false;

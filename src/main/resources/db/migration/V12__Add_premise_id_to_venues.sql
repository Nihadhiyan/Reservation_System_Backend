-- venues.premise_id: government/municipal registration number for this
-- specific physical premises. Required on every venue and unique — an
-- organization can own multiple venues, so this deliberately does NOT live
-- on organizations (a single org-level field couldn't represent "one ID per
-- venue" for a multi-venue owner). Same backfill-then-constrain pattern as
-- V7's registration_number, since existing rows predate this column.
ALTER TABLE venues ADD COLUMN IF NOT EXISTS premise_id VARCHAR(255);
UPDATE venues SET premise_id = 'UNKNOWN-' || id::text WHERE premise_id IS NULL;
ALTER TABLE venues ALTER COLUMN premise_id SET NOT NULL;
ALTER TABLE venues ADD CONSTRAINT uk_venues_premise_id UNIQUE (premise_id);

-- venues.verified: mirrors organizations.verified — a super admin manually
-- confirms the premise ID (and the venue generally) is legitimate, the same
-- review workflow already used for an organization's business registration
-- number (see OrganizationService.verifyOrganization / POST /organizations/{id}/verify).
ALTER TABLE venues ADD COLUMN IF NOT EXISTS verified BOOLEAN NOT NULL DEFAULT false;

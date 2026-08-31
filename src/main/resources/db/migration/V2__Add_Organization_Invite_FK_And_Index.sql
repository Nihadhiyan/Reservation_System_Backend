-- Add index for organization_id lookup on organization_invites
CREATE INDEX IF NOT EXISTS idx_org_invite_org ON organization_invites (organization_id);

-- Add foreign key constraint linking organization_invites to organizations table
ALTER TABLE organization_invites
    ADD CONSTRAINT fk_org_invites_org FOREIGN KEY (organization_id) REFERENCES organizations (id);

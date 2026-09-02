-- family_id is mapped nullable = false on RefreshToken, but the column
-- allows NULL in the DB. No row should legitimately lack one (every
-- refresh-token session is created with a family id — see
-- AuthService/TokenManagementService), so this only affects stale/dev rows.
UPDATE refresh_tokens SET family_id = id::text WHERE family_id IS NULL;
ALTER TABLE refresh_tokens ALTER COLUMN family_id SET NOT NULL;

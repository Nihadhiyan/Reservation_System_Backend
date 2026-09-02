-- V6 created family_id as a native UUID column, but RefreshToken.familyId has
-- always been mapped as a plain String (it's built everywhere via
-- UUID.randomUUID().toString(), never java.util.UUID) — Hibernate's schema
-- validator expects varchar for a String field and rejects the UUID column
-- type at startup. Casting via ::text preserves existing values' formatting.
ALTER TABLE refresh_tokens ALTER COLUMN family_id TYPE VARCHAR(255) USING family_id::text;

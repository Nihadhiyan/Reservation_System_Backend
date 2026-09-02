-- RefreshToken.jti was originally named `token` (V1's baseline table). The
-- Java field was renamed at some point but the column never followed,
-- leaving Hibernate's schema validator unable to find a `jti` column.
ALTER TABLE refresh_tokens RENAME COLUMN token TO jti;
ALTER TABLE refresh_tokens RENAME CONSTRAINT uk_refresh_tokens_token TO uk_refresh_tokens_jti;

-- Renaming (not drop+recreate): H2 treats idx_refresh_token_string as the
-- backing index for the just-renamed unique constraint and refuses a bare
-- DROP INDEX on it ("belongs to constraint"), even though Postgres allows
-- it. Renaming works on both.
ALTER INDEX IF EXISTS idx_refresh_token_string RENAME TO idx_refresh_token_jti;

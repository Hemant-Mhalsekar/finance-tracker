CREATE TABLE app_meta (
    key   TEXT PRIMARY KEY,
    value TEXT NOT NULL
);

-- RLS is enabled (with no policies) so Supabase's automatic API cannot expose this table.
-- The Spring Boot backend connects with the database owner role, which is not blocked by this.
ALTER TABLE app_meta ENABLE ROW LEVEL SECURITY;

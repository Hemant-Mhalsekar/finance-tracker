CREATE TABLE users (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    email         TEXT        NOT NULL CHECK (length(email) BETWEEN 3 AND 254),
    password_hash TEXT        NOT NULL,
    pay_day       SMALLINT    CHECK (pay_day IS NULL OR pay_day BETWEEN 1 AND 31),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Emails are unique regardless of letter case.
CREATE UNIQUE INDEX users_email_lower_idx ON users (lower(email));

CREATE TABLE refresh_tokens (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash TEXT        NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked    BOOLEAN     NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX refresh_tokens_user_id_idx ON refresh_tokens (user_id);

-- RLS is enabled (with no policies) so Supabase's automatic API cannot expose these tables.
-- The Spring Boot backend connects with the database owner role, which is not blocked by this.
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE refresh_tokens ENABLE ROW LEVEL SECURITY;

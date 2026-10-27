-- All money is stored as BIGINT paise (1 rupee = 100 paise), never NUMERIC or floating point,
-- so sums and percentage splits are exact whole numbers.

CREATE TABLE categories (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name       TEXT        NOT NULL CHECK (length(btrim(name)) BETWEEN 1 AND 50),
    -- NULL means the user chooses the type for each expense (used by the Other category).
    type       TEXT        CHECK (type IS NULL OR type IN ('NEED', 'WANT', 'INVESTMENT')),
    archived   BOOLEAN     NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT categories_id_user_key UNIQUE (id, user_id)
);

CREATE UNIQUE INDEX categories_user_name_lower_idx ON categories (user_id, lower(name));

-- One pay cycle with its budget.
CREATE TABLE cycles (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    period_start   DATE        NOT NULL,
    period_end     DATE        NOT NULL CHECK (period_end >= period_start),
    salary_paise   BIGINT      NOT NULL CHECK (salary_paise > 0),
    needs_pct      SMALLINT    NOT NULL DEFAULT 50 CHECK (needs_pct BETWEEN 0 AND 100),
    wants_pct      SMALLINT    NOT NULL DEFAULT 30 CHECK (wants_pct BETWEEN 0 AND 100),
    savings_pct    SMALLINT    NOT NULL DEFAULT 20 CHECK (savings_pct BETWEEN 0 AND 100),
    needs_paise    BIGINT      NOT NULL CHECK (needs_paise >= 0),
    wants_paise    BIGINT      NOT NULL CHECK (wants_paise >= 0),
    savings_paise  BIGINT      NOT NULL CHECK (savings_paise >= 0),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT cycles_pct_total_check CHECK (needs_pct + wants_pct + savings_pct = 100),
    CONSTRAINT cycles_paise_total_check CHECK (needs_paise + wants_paise + savings_paise = salary_paise),
    CONSTRAINT cycles_user_period_start_key UNIQUE (user_id, period_start)
);

-- There is deliberately no foreign key from expenses to cycles. The cycle for an expense is
-- found by checking which cycle's date range (period_start..period_end) contains expense_date.
CREATE TABLE expenses (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    category_id   UUID        NOT NULL,
    -- The category's type at the time the expense was logged, so past reports never change
    -- when a category is later edited.
    category_type TEXT        NOT NULL CHECK (category_type IN ('NEED', 'WANT', 'INVESTMENT')),
    amount_paise  BIGINT      NOT NULL CHECK (amount_paise BETWEEN 1 AND 10000000000),
    expense_date  DATE        NOT NULL,
    description   TEXT        NOT NULL CHECK (length(btrim(description)) BETWEEN 1 AND 200),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- The composite key guarantees an expense can only use a category owned by the same user.
    -- NO ACTION (the default) is used instead of RESTRICT because RESTRICT is checked immediately
    -- and can make deleting a user fail when the user's categories and expenses are removed by
    -- cascade in the same statement. Deleting a category that still has expenses is still blocked.
    CONSTRAINT expenses_category_same_user_fk FOREIGN KEY (category_id, user_id) REFERENCES categories (id, user_id)
);

CREATE INDEX expenses_user_date_idx ON expenses (user_id, expense_date);
CREATE INDEX expenses_category_id_idx ON expenses (category_id);

-- RLS is enabled (with no policies) so Supabase's automatic API cannot expose these tables.
-- The Spring Boot backend connects with the database owner role, which is not blocked by this.
ALTER TABLE categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE cycles ENABLE ROW LEVEL SECURITY;
ALTER TABLE expenses ENABLE ROW LEVEL SECURITY;

-- A pay day change waits one full cycle: the current cycle and the next one keep the old pay day.
-- The new pay day is stored here until the roll-forward creates the cycle starting on or after
-- pending_pay_day_from, which then moves it into pay_day.
ALTER TABLE users
    ADD COLUMN pending_pay_day      SMALLINT CHECK (pending_pay_day IS NULL OR pending_pay_day BETWEEN 1 AND 31),
    ADD COLUMN pending_pay_day_from DATE,
    ADD CONSTRAINT users_pending_pay_day_pair_check
        CHECK ((pending_pay_day IS NULL) = (pending_pay_day_from IS NULL)),
    ADD CONSTRAINT users_pending_pay_day_needs_pay_day_check
        CHECK (pending_pay_day IS NULL OR pay_day IS NOT NULL);

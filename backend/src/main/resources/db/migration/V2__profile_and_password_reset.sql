-- =============================================================================
-- MindHaven Increment 2 Database Schema Updates (PostgreSQL / Supabase)
-- Adds user profile bio fields and single-use expiring password reset tokens.
-- =============================================================================

ALTER TABLE mindhaven.student_profile
    ADD COLUMN IF NOT EXISTS bio VARCHAR(200);

ALTER TABLE mindhaven.staff_user
    ADD COLUMN IF NOT EXISTS bio VARCHAR(200);

CREATE TABLE IF NOT EXISTS mindhaven.password_reset_token (
    token_id   UUID PRIMARY KEY,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    owner_type VARCHAR(16) NOT NULL CHECK (owner_type IN ('STUDENT', 'STAFF')),
    owner_id   UUID NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used       BOOLEAN NOT NULL DEFAULT FALSE,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_password_reset_token_owner
    ON mindhaven.password_reset_token(owner_type, owner_id);

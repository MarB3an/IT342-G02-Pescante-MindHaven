-- =============================================================================
-- MindHaven Increment 1 Database Schema (PostgreSQL / Supabase)
-- Run in Supabase SQL Editor if creating schema manually, or let Spring Boot
-- Hibernate create_namespaces + ddl-auto=update apply it automatically.
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS mindhaven;

CREATE TABLE IF NOT EXISTS mindhaven.student_account (
    account_id      UUID PRIMARY KEY,
    email_hash      VARCHAR(64) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    failed_attempts INT NOT NULL DEFAULT 0,
    locked_until    TIMESTAMPTZ,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS mindhaven.student_profile (
    pseudonym_id     UUID PRIMARY KEY,
    account_id       UUID NOT NULL UNIQUE REFERENCES mindhaven.student_account(account_id) ON DELETE CASCADE,
    pseudonym_handle VARCHAR(60) NOT NULL UNIQUE,
    avatar_code      VARCHAR(40) NOT NULL DEFAULT 'sparrow',
    is_active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_seen_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS mindhaven.staff_user (
    staff_id        UUID PRIMARY KEY,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    full_name       VARCHAR(120) NOT NULL,
    role            VARCHAR(32) NOT NULL CHECK (role IN ('ROLE_COUNSELOR', 'ROLE_ADMIN')),
    mobile_number   VARCHAR(32),
    is_on_call      BOOLEAN NOT NULL DEFAULT FALSE,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    failed_attempts INT NOT NULL DEFAULT 0,
    locked_until    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS mindhaven.refresh_token (
    token_id   UUID PRIMARY KEY,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    owner_type VARCHAR(16) NOT NULL CHECK (owner_type IN ('STUDENT', 'STAFF')),
    owner_id   UUID NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_refresh_token_owner ON mindhaven.refresh_token(owner_type, owner_id);

CREATE TABLE IF NOT EXISTS mindhaven.topic_room (
    room_id       UUID PRIMARY KEY,
    room_name     VARCHAR(100) NOT NULL UNIQUE,
    category_slug VARCHAR(100) NOT NULL UNIQUE,
    description   TEXT NOT NULL,
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_by    UUID REFERENCES mindhaven.staff_user(staff_id) ON DELETE SET NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS mindhaven.audit_log (
    audit_id    UUID PRIMARY KEY,
    actor_role  VARCHAR(32) NOT NULL,
    actor_label VARCHAR(120) NOT NULL,
    action      VARCHAR(64) NOT NULL,
    details     TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_log_created_at ON mindhaven.audit_log(created_at DESC);

-- Identity: accounts, the refresh-token store that makes rotation and reuse
-- detection possible, and per-user lesson progress.

CREATE TABLE users (
    id            uuid         NOT NULL,
    email         varchar(254) NOT NULL,
    password_hash varchar(100) NOT NULL,
    role          varchar(16)  NOT NULL,
    locale        varchar(2)   NOT NULL DEFAULT 'en',
    theme         varchar(16)  NOT NULL DEFAULT 'SYSTEM',
    enabled       boolean      NOT NULL DEFAULT true,
    created_at    timestamptz  NOT NULL,
    updated_at    timestamptz  NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email),
    -- Email is matched case-insensitively, which is implemented by storing it
    -- lowercased rather than by a functional index, so that the unique
    -- constraint and every lookup agree without a second normalization rule.
    CONSTRAINT ck_users_email_lowercase CHECK (email = lower(email)),
    CONSTRAINT ck_users_role CHECK (role IN ('ADMIN', 'EDITOR', 'USER')),
    CONSTRAINT ck_users_locale CHECK (locale IN ('en', 'tr', 'fr', 'de')),
    CONSTRAINT ck_users_theme CHECK (theme IN ('LIGHT', 'DARK', 'SYSTEM'))
);

-- Refresh tokens are opaque 256-bit random values. Only their SHA-256 digest is
-- stored: the server cannot reproduce the plaintext of any token it has issued,
-- including one it issued a second ago. That is a deliberate property, and the
-- grace path for a lost rotation response depends on it -- with no plaintext to
-- return, the only possible answer is to mint a fresh pair.
CREATE TABLE refresh_tokens (
    id              uuid        NOT NULL,
    user_id         uuid        NOT NULL,
    -- Links every token descended from one sign-in. Reuse detection revokes a
    -- family, not a token.
    family_id       uuid        NOT NULL,
    token_digest    varchar(64)    NOT NULL,
    device_label    varchar(64),
    issued_at       timestamptz NOT NULL,
    expires_at      timestamptz NOT NULL,
    -- Set when this token is exchanged. A token is single-use: a second
    -- presentation of a rotated token is either a lost response inside the
    -- grace window or a replay.
    rotated_at      timestamptz,
    revoked_at      timestamptz,
    revoked_reason  varchar(32),
    -- The token this one rotated into, so a replay can ask whether the chain
    -- has already advanced past the successor.
    successor_id    uuid,
    -- True when this token was minted by the lost-response grace path rather
    -- than by a normal rotation. Counting these per family is the signal worth
    -- alerting on: a healthy client produces very few over a session.
    minted_by_grace boolean     NOT NULL DEFAULT false,
    created_at      timestamptz NOT NULL,
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT uq_refresh_tokens_digest UNIQUE (token_digest),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_refresh_tokens_successor FOREIGN KEY (successor_id) REFERENCES refresh_tokens (id),
    CONSTRAINT ck_refresh_tokens_digest CHECK (token_digest ~ '^[0-9a-f]{64}$')
);

-- Revoking a whole family on reuse detection, and on a password change.
CREATE INDEX ix_refresh_tokens_family ON refresh_tokens (family_id);
-- Revoking every family of one user (logout ?all_devices=true).
CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id);

CREATE TABLE user_progress (
    user_id           uuid        NOT NULL,
    lesson_id         uuid        NOT NULL,
    -- Nullable and meaningful: null records "explicitly marked incomplete",
    -- which is a real user action and has to survive a sync round trip as such.
    completed_at      timestamptz,
    -- The client's own clock, and the value the last-write-wins comparison is
    -- made on. Untrusted, and clamped to server time by the service when it is
    -- implausibly far ahead.
    client_updated_at timestamptz NOT NULL,
    updated_at        timestamptz NOT NULL,
    CONSTRAINT pk_user_progress PRIMARY KEY (user_id, lesson_id),
    CONSTRAINT fk_user_progress_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_progress_lesson FOREIGN KEY (lesson_id) REFERENCES lessons (id)
);

-- The pull direction of progress sync reads one user's rows with an exclusive
-- lower bound on updated_at, ordered ascending.
CREATE INDEX ix_user_progress_user_updated ON user_progress (user_id, updated_at);

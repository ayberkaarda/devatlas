-- Content hierarchy: Track -> Module -> Lesson -> CodeExample, plus the mind map
-- per track and the translation rows that carry the non-English text.
--
-- Two rules govern this file and every later migration that touches content:
--
--   1. No trigger, no function, no generated column and no DEFAULT expression
--      computes a hash, a digest or a content version. Those values are written
--      by the service layer inside the same transaction as the write they
--      describe, so that one code path produces both the stored bytes and the
--      digest describing them, and so that the determinism tests can reach the
--      computation at all.
--
--   2. `content_version` on `tracks` is a plain stored counter. It is never
--      derived as a maximum over its modules and lessons: a maximum cannot
--      decrease, so deleting a lesson or reordering modules would leave the
--      track advertising the version it already had, and every client would
--      conclude its local copy was current.

CREATE TABLE tracks (
    id              uuid         NOT NULL,
    slug            varchar(80)  NOT NULL,
    title           varchar(200) NOT NULL,
    description     varchar(2000),
    icon            varchar(64),
    display_order   integer      NOT NULL,
    published       boolean      NOT NULL DEFAULT false,
    -- Content revision counter consumed by the manifest. Its own column, never
    -- a maximum over descendants (see rule 2 above).
    content_version integer      NOT NULL DEFAULT 1,
    created_at      timestamptz  NOT NULL,
    updated_at      timestamptz  NOT NULL,
    -- Optimistic-lock counter. Unrelated to content_version: this one only
    -- detects concurrent edits and means nothing to clients.
    version         bigint       NOT NULL DEFAULT 0,
    CONSTRAINT pk_tracks PRIMARY KEY (id),
    CONSTRAINT uq_tracks_slug UNIQUE (slug),
    CONSTRAINT ck_tracks_slug CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT ck_tracks_icon CHECK (icon IS NULL OR icon ~ '^[a-z0-9-]+$'),
    CONSTRAINT ck_tracks_display_order CHECK (display_order BETWEEN 1 AND 10000),
    CONSTRAINT ck_tracks_content_version CHECK (content_version >= 1)
);

-- The public track list is filtered on published and ordered by display_order.
CREATE INDEX ix_tracks_published_order ON tracks (published, display_order);

CREATE TABLE modules (
    id                uuid         NOT NULL,
    track_id          uuid         NOT NULL,
    title             varchar(200) NOT NULL,
    display_order     integer      NOT NULL,
    estimated_minutes integer,
    created_at        timestamptz  NOT NULL,
    updated_at        timestamptz  NOT NULL,
    version           bigint       NOT NULL DEFAULT 0,
    CONSTRAINT pk_modules PRIMARY KEY (id),
    CONSTRAINT fk_modules_track FOREIGN KEY (track_id) REFERENCES tracks (id),
    CONSTRAINT ck_modules_display_order CHECK (display_order BETWEEN 1 AND 10000),
    CONSTRAINT ck_modules_estimated_minutes
        CHECK (estimated_minutes IS NULL OR estimated_minutes BETWEEN 1 AND 6000)
);

-- Every module read is "the modules of this track, in order". There is no
-- unique constraint on (track_id, display_order): the service renumbers a
-- parent's children to 1..n inside one transaction, and an immediately checked
-- unique constraint would reject the intermediate states of that renumbering.
CREATE INDEX ix_modules_track_order ON modules (track_id, display_order);

CREATE TABLE lessons (
    id                  uuid         NOT NULL,
    module_id           uuid         NOT NULL,
    slug                varchar(80)  NOT NULL,
    title               varchar(200) NOT NULL,
    body_markdown       text         NOT NULL,
    difficulty          varchar(16)  NOT NULL,
    estimated_minutes   integer,
    display_order       integer      NOT NULL,
    content_version     integer      NOT NULL DEFAULT 1,
    -- The canonical package bytes and the digest over them, produced by the
    -- service layer when content_version is incremented and served back
    -- verbatim. Nullable because a lesson exists from the moment it is created
    -- while packaging is a separate step; a row with no stored package is a row
    -- no manifest may advertise.
    sha256              varchar(64),
    package_bytes       bytea,
    package_size_bytes  integer,
    -- Soft delete. Lessons are the only soft-deleted content entity, because
    -- they are the only one referenced by user_progress, and a completion is a
    -- fact about a person's history that an editorial decision must not erase.
    deleted_at          timestamptz,
    created_at          timestamptz  NOT NULL,
    updated_at          timestamptz  NOT NULL,
    version             bigint       NOT NULL DEFAULT 0,
    CONSTRAINT pk_lessons PRIMARY KEY (id),
    CONSTRAINT fk_lessons_module FOREIGN KEY (module_id) REFERENCES modules (id),
    CONSTRAINT ck_lessons_slug CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT ck_lessons_difficulty
        CHECK (difficulty IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED')),
    CONSTRAINT ck_lessons_estimated_minutes
        CHECK (estimated_minutes IS NULL OR estimated_minutes BETWEEN 1 AND 600),
    CONSTRAINT ck_lessons_display_order CHECK (display_order BETWEEN 1 AND 10000),
    CONSTRAINT ck_lessons_content_version CHECK (content_version >= 1),
    CONSTRAINT ck_lessons_sha256 CHECK (sha256 IS NULL OR sha256 ~ '^[0-9a-f]{64}$'),
    -- The three package columns move together or not at all.
    CONSTRAINT ck_lessons_package_complete CHECK (
        (sha256 IS NULL AND package_bytes IS NULL AND package_size_bytes IS NULL)
        OR (sha256 IS NOT NULL AND package_bytes IS NOT NULL AND package_size_bytes IS NOT NULL)
    )
);

-- Lesson slugs are globally unique, not unique within a track, so a deep link
-- survives a lesson being moved between modules or tracks.
--
-- The index is PARTIAL. A plain unique index would let every soft-deleted
-- lesson squat on its slug forever: an editor who deleted a lesson could never
-- recreate one at the same address, and the conflict they would be shown names
-- a lesson that no read endpoint can find.
CREATE UNIQUE INDEX ux_lessons_slug ON lessons (slug) WHERE deleted_at IS NULL;

-- Listing a module's live lessons in order. Partial for the same reason every
-- read query carries deleted_at IS NULL.
CREATE INDEX ix_lessons_module_order ON lessons (module_id, display_order)
    WHERE deleted_at IS NULL;

CREATE TABLE code_examples (
    id            uuid        NOT NULL,
    lesson_id     uuid        NOT NULL,
    language      varchar(32) NOT NULL,
    code          text        NOT NULL,
    caption       varchar(300),
    display_order integer     NOT NULL,
    created_at    timestamptz NOT NULL,
    updated_at    timestamptz NOT NULL,
    version       bigint      NOT NULL DEFAULT 0,
    CONSTRAINT pk_code_examples PRIMARY KEY (id),
    CONSTRAINT fk_code_examples_lesson FOREIGN KEY (lesson_id) REFERENCES lessons (id),
    CONSTRAINT ck_code_examples_display_order CHECK (display_order BETWEEN 1 AND 10000)
);

CREATE INDEX ix_code_examples_lesson_order ON code_examples (lesson_id, display_order);

CREATE TABLE mind_maps (
    id                 uuid        NOT NULL,
    track_id           uuid        NOT NULL,
    root               jsonb       NOT NULL,
    content_version    integer     NOT NULL DEFAULT 1,
    sha256             varchar(64),
    package_bytes      bytea,
    package_size_bytes integer,
    created_at         timestamptz NOT NULL,
    updated_at         timestamptz NOT NULL,
    version            bigint      NOT NULL DEFAULT 0,
    CONSTRAINT pk_mind_maps PRIMARY KEY (id),
    -- One mind map per track: the write endpoint upserts the whole tree.
    CONSTRAINT uq_mind_maps_track UNIQUE (track_id),
    CONSTRAINT fk_mind_maps_track FOREIGN KEY (track_id) REFERENCES tracks (id),
    CONSTRAINT ck_mind_maps_content_version CHECK (content_version >= 1),
    CONSTRAINT ck_mind_maps_sha256 CHECK (sha256 IS NULL OR sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_mind_maps_package_complete CHECK (
        (sha256 IS NULL AND package_bytes IS NULL AND package_size_bytes IS NULL)
        OR (sha256 IS NOT NULL AND package_bytes IS NOT NULL AND package_size_bytes IS NOT NULL)
    )
);

CREATE TABLE content_translations (
    id          uuid         NOT NULL,
    entity_type varchar(16)  NOT NULL,
    entity_id   uuid         NOT NULL,
    locale      varchar(2)   NOT NULL,
    title       varchar(200) NOT NULL,
    body        text,
    created_at  timestamptz  NOT NULL,
    updated_at  timestamptz  NOT NULL,
    version     bigint       NOT NULL DEFAULT 0,
    CONSTRAINT pk_content_translations PRIMARY KEY (id),
    CONSTRAINT uq_content_translations_entity_locale UNIQUE (entity_type, entity_id, locale),
    -- Mind map labels are not translated, so MIND_MAP is not in the set.
    CONSTRAINT ck_content_translations_entity_type
        CHECK (entity_type IN ('TRACK', 'MODULE', 'LESSON', 'BLOG_POST')),
    -- English is canonical and lives in the entity's own columns. A second
    -- English copy would create two answers to "what is the English title".
    CONSTRAINT ck_content_translations_locale CHECK (locale IN ('tr', 'fr', 'de')),
    -- A lesson or a post IS its body: a translated title over an English body
    -- would report itself as translated while visibly not being so. A track
    -- description and a module title are subtitles, where a partial state is
    -- coherent.
    CONSTRAINT ck_content_translations_body_required CHECK (
        entity_type IN ('TRACK', 'MODULE')
        OR (body IS NOT NULL AND btrim(body) <> '')
    )
);

-- No foreign key: entity_id is polymorphic across four tables. Referential
-- integrity is enforced by the service, which resolves the entity before
-- writing the row.
CREATE INDEX ix_content_translations_entity ON content_translations (entity_type, entity_id);
